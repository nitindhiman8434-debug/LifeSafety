package com.lifesafety.driversafety.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.ConfirmDialog
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

/** Driver screen: who the admins are, what they see, and a Remove button for each. */
@Composable
fun WhoCanSeeMyDataScreen(
    viewModel: DriverLinkViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf<Link?>(null) }

    // Removing the primary admin ends the whole link; go back to the home screen, which then asks for a code.
    val unlinked = !state.loading && state.loadError == null && state.primary == null
    LaunchedEffect(unlinked) {
        if (unlinked) onBack()
    }

    confirmRemove?.let { link ->
        val isPrimary = link.role == LinkRole.PRIMARY
        ConfirmDialog(
            title = stringResource(if (isPrimary) R.string.admins_remove_primary_title else R.string.admins_remove_secondary_title),
            text = stringResource(if (isPrimary) R.string.admins_remove_primary_text else R.string.admins_remove_secondary_text, link.adminName),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { viewModel.removeAdmin(link.adminId) },
            onDismiss = { confirmRemove = null }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppTopBar(title = stringResource(R.string.admins_title), onBack = onBack) }
    ) { padding ->
        if (state.loading || busy) {
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
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(16.dp))
            val primary = state.primary
            val secondary = state.secondary
            if (primary == null) {
                Text(stringResource(R.string.admins_none), style = MaterialTheme.typography.bodyLarge)
            } else {
                AdminCard(
                    name = primary.adminName,
                    roleLabel = stringResource(R.string.admins_primary_label),
                    onRemove = { confirmRemove = primary }
                )
                Spacer(Modifier.height(16.dp))
                if (secondary == null) {
                    Text(stringResource(R.string.admins_secondary_none), style = MaterialTheme.typography.bodyLarge)
                } else {
                    AdminCard(
                        name = secondary.adminName,
                        roleLabel = stringResource(R.string.admins_secondary_label),
                        onRemove = { confirmRemove = secondary }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminCard(name: String, roleLabel: String, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(roleLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.admins_sees), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.action_remove))
            }
        }
    }
}
