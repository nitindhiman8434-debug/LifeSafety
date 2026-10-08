package com.lifesafety.driversafety.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.pairing.FunctionErrors
import com.lifesafety.driversafety.ui.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class NeedsRole(val uid: String, val displayName: String) : SessionState
    data class Ready(val profile: UserProfile) : SessionState
    data class Error(val message: UiText) : SessionState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {

    /** Signed out -> needs role -> ready. Rebuilt automatically whenever the Firebase user or the profile changes. */
    val session: StateFlow<SessionState> = repo.authUserFlow()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf<SessionState>(SessionState.SignedOut)
            } else {
                val name = user.displayName ?: user.email ?: ""
                repo.profileFlow(user.uid)
                    .map<UserProfile?, SessionState> { profile ->
                        if (profile == null) SessionState.NeedsRole(user.uid, name) else SessionState.Ready(profile)
                    }
                    .onStart { emit(SessionState.Loading) }
                    .catch { e ->
                        emit(SessionState.Error(UiText.Res(R.string.error_load_failed, listOf(e.message ?: ""))))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            when (val result = repo.signInWithGoogle(activity)) {
                SignInResult.Success, SignInResult.Cancelled -> Unit
                SignInResult.NoGoogleAccount -> _message.value = UiText.Res(R.string.error_no_google_account)
                is SignInResult.Failed ->
                    _message.value = UiText.Res(R.string.error_sign_in_failed, listOf(result.error.message ?: ""))
            }
            _busy.value = false
        }
    }

    fun chooseRole(role: UserRole) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                repo.setRole(role)
            } catch (e: Exception) {
                _message.value = FunctionErrors.toUiText(e)
            }
            _busy.value = false
        }
    }

    fun signOut(context: Context) {
        _message.value = null
        viewModelScope.launch { repo.signOut(context.applicationContext) }
    }

    fun clearMessage() {
        _message.value = null
    }
}
