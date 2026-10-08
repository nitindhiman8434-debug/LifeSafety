package com.lifesafety.driversafety.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.pairing.Link
import com.lifesafety.driversafety.pairing.LinkRole
import com.lifesafety.driversafety.pairing.LinkStatus
import com.lifesafety.driversafety.ui.components.AccountMenu
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

/** Phase 1 dashboard: the admin's drivers with role and link status. Phase 3 adds counters, map and live speed. */
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onAddDriver: () -> Unit,
    onJoinAsSecondAdmin: () -> Unit,
    onOpenDriver: (String) -> Unit,
    onSignOut: () -> Unit,
    onChangeRole: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.admin_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Button(onClick = onAddDriver, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.admin_add_driver), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onJoinAsSecondAdmin, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.admin_join_second), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
            when {
                state.loading -> LoadingScreen()
                state.loadError != null -> MessageCard(state.loadError)
                state.links.isEmpty() -> {
                    Text(stringResource(R.string.admin_no_drivers_title), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.admin_no_drivers_body), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn {
                    items(state.links, key = { it.id }) { link ->
                        DriverCard(link = link, onClick = { onOpenDriver(link.driverId) })
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverCard(link: Link, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(link.driverName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (link.role == LinkRole.PRIMARY) R.string.admin_role_primary else R.string.admin_role_secondary
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(
                    if (link.status == LinkStatus.ACTIVE) R.string.link_status_active else R.string.link_status_pending
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = if (link.status == LinkStatus.ACTIVE) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
            )
        }
    }
}
