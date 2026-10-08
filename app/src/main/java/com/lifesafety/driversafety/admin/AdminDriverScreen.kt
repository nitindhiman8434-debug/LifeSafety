package com.lifesafety.driversafety.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.lifesafety.driversafety.pairing.LinkRole
import com.lifesafety.driversafety.pairing.LinkStatus
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.ConfirmDialog
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

private enum class PendingAction { REMOVE_DRIVER, REMOVE_SECOND_ADMIN, LEAVE }

/**
 * Phase 1 driver detail: my role, link status, the Admins section with add/remove/leave.
 * Phase 3 adds the live map, speed, settings, actions and the trip list.
 */
@Composable
fun AdminDriverScreen(
    viewModel: AdminViewModel,
    driverId: String,
    onBack: () -> Unit,
    onAddSecondAdmin: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val recordFlow = remember(driverId) { viewModel.driverRecordFlow(driverId) }
    val record by recordFlow.collectAsStateWithLifecycle(initialValue = null)
    val link = state.links.firstOrNull { it.driverId == driverId }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

    // The link disappears when the driver is removed or this admin leaves: go back to the dashboard.
    LaunchedEffect(state.loading, link == null) {
        if (!state.loading && link == null) onBack()
    }

    val driverName = link?.driverName ?: stringResource(R.string.driver_detail_title)

    pendingAction?.let { action ->
        when (action) {
            PendingAction.REMOVE_DRIVER -> ConfirmDialog(
                title = stringResource(R.string.detail_remove_driver_title),
                text = stringResource(R.string.detail_remove_driver_text, driverName),
                confirmLabel = stringResource(R.string.action_remove),
                onConfirm = { viewModel.leaveDriver(driverId) },
                onDismiss = { pendingAction = null }
            )
            PendingAction.REMOVE_SECOND_ADMIN -> ConfirmDialog(
                title = stringResource(R.string.detail_remove_second_title),
                text = stringResource(R.string.detail_remove_second_text, record?.secondaryAdminName ?: ""),
                confirmLabel = stringResource(R.string.action_remove),
                onConfirm = { viewModel.removeSecondaryAdmin(driverId) },
                onDismiss = { pendingAction = null }
            )
            PendingAction.LEAVE -> ConfirmDialog(
                title = stringResource(R.string.detail_leave_title),
                text = stringResource(R.string.detail_leave_text, driverName),
                confirmLabel = stringResource(R.string.action_leave),
                onConfirm = { viewModel.leaveDriver(driverId) },
                onDismiss = { pendingAction = null }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppTopBar(title = driverName, onBack = onBack) }
    ) { padding ->
        if (link == null || busy) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        val isPrimary = link.role == LinkRole.PRIMARY
        val isActive = link.status == LinkStatus.ACTIVE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(
                            R.string.detail_my_role,
                            stringResource(if (isPrimary) R.string.detail_role_primary else R.string.detail_role_secondary)
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.detail_status,
                            stringResource(if (isActive) R.string.link_status_active else R.string.link_status_pending)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                    if (!isActive) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.detail_pending_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.detail_admins_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            if (isPrimary) {
                Text(
                    text = stringResource(R.string.detail_primary, stringResource(R.string.detail_you, link.adminName)),
                    style = MaterialTheme.typography.bodyLarge
                )
                val secondaryName = record?.secondaryAdminName
                Text(
                    text = when {
                        secondaryName == null -> stringResource(R.string.detail_secondary_none)
                        record?.secondaryStatus == "pending_consent" -> stringResource(R.string.detail_secondary_pending, secondaryName)
                        else -> stringResource(R.string.detail_secondary, secondaryName)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                if (isActive) {
                    if (record?.secondaryAdminId == null) {
                        Button(onClick = onAddSecondAdmin, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(stringResource(R.string.detail_add_second), style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { pendingAction = PendingAction.REMOVE_SECOND_ADMIN },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(stringResource(R.string.detail_remove_second), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = { pendingAction = PendingAction.REMOVE_DRIVER },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.detail_remove_driver), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(
                    text = stringResource(R.string.detail_primary, record?.primaryAdminName ?: stringResource(R.string.detail_unknown)),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.detail_secondary, stringResource(R.string.detail_you, link.adminName)),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { pendingAction = PendingAction.LEAVE },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.detail_leave), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.detail_phase3_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
