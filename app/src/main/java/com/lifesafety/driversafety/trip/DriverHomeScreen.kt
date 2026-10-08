package com.lifesafety.driversafety.trip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.pairing.ConsentScreen
import com.lifesafety.driversafety.pairing.DriverLinkUiState
import com.lifesafety.driversafety.pairing.DriverLinkViewModel
import com.lifesafety.driversafety.ui.UiText
import com.lifesafety.driversafety.ui.components.AccountMenu
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.CodeEntryCard
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

/**
 * The driver's main screen. In Phase 1 it shows the link state: enter a code, give consent, or "monitoring active".
 * Phase 2 turns it into the trip screen (huge speed, limit badge, Start Trip, SOS).
 */
@Composable
fun DriverHomeScreen(
    viewModel: DriverLinkViewModel,
    onOpenAdmins: () -> Unit,
    onSignOut: () -> Unit,
    onChangeRole: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val code by viewModel.codeInput.collectAsStateWithLifecycle()

    // A link waiting for consent takes over the whole screen until the driver answers.
    val pending = state.pendingLink
    if (pending != null) {
        ConsentScreen(
            link = pending,
            busy = busy,
            message = message,
            onAgree = { viewModel.respondToConsent(pending.adminId, true) },
            onDecline = { viewModel.respondToConsent(pending.adminId, false) }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.driver_title),
                actions = {
                    AccountMenu(
                        canChangeRole = !state.loading && state.links.isEmpty(),
                        onSignOut = onSignOut,
                        onChangeRole = onChangeRole
                    )
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                state.loadError != null -> MessageCard(state.loadError)
                state.isLinked -> LinkedCard(state = state, message = message, onOpenAdmins = onOpenAdmins)
                else -> CodeEntryCard(
                    title = stringResource(R.string.driver_unlinked_title),
                    body = stringResource(R.string.driver_unlinked_body),
                    code = code,
                    onCodeChanged = viewModel::onCodeChanged,
                    onSubmit = viewModel::joinWithCode,
                    busy = busy,
                    message = message,
                    buttonLabel = stringResource(R.string.driver_join_button)
                )
            }
        }
    }
}

@Composable
private fun LinkedCard(
    state: DriverLinkUiState,
    message: UiText?,
    onOpenAdmins: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.driver_linked_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.driver_linked_body), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.driver_primary_admin, state.primary?.adminName ?: ""),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = state.secondary?.let { stringResource(R.string.driver_second_admin, it.adminName) }
                    ?: stringResource(R.string.driver_second_admin_none),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenAdmins, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.driver_who_can_see), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
