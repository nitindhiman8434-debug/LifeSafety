package com.lifesafety.driversafety.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.ui.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DriverLinkUiState(
    val loading: Boolean = true,
    val links: List<Link> = emptyList(),
    val loadError: UiText? = null
) {
    /** A link waiting for the driver's Agree or Decline. Shown as the consent screen. */
    val pendingLink: Link? get() = links.firstOrNull { it.status == LinkStatus.PENDING_CONSENT }
    val primary: Link? get() = links.firstOrNull { it.role == LinkRole.PRIMARY && it.status == LinkStatus.ACTIVE }
    val secondary: Link? get() = links.firstOrNull { it.role == LinkRole.SECONDARY && it.status == LinkStatus.ACTIVE }
    val isLinked: Boolean get() = primary != null
}

/** Driver side: live link state plus the actions a driver can take. One instance per signed-in driver. */
class DriverLinkViewModel(
    uid: String,
    private val repo: PairingRepository = PairingRepository()
) : ViewModel() {

    val state: StateFlow<DriverLinkUiState> = repo.linksForDriver(uid)
        .map { DriverLinkUiState(loading = false, links = it) }
        .catch { e ->
            emit(DriverLinkUiState(loading = false, loadError = UiText.Res(R.string.error_load_failed, listOf(e.message ?: ""))))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverLinkUiState())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val _codeInput = MutableStateFlow("")
    val codeInput: StateFlow<String> = _codeInput.asStateFlow()

    fun onCodeChanged(value: String) {
        _codeInput.value = value.filter { it.isDigit() }.take(6)
    }

    fun joinWithCode() = runAction {
        repo.redeemPairingCode(_codeInput.value)
        _codeInput.value = ""
    }

    fun respondToConsent(adminId: String, accept: Boolean) = runAction {
        repo.respondToConsent(adminId, accept)
    }

    fun removeAdmin(adminId: String) = runAction {
        repo.removeAdmin(adminId)
        _message.value = UiText.Res(R.string.msg_admin_removed)
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                block()
            } catch (e: Exception) {
                _message.value = FunctionErrors.toUiText(e)
            } finally {
                _busy.value = false
            }
        }
    }
}
