package com.lifesafety.driversafety.alerts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.admin.AdminViewModel
import com.lifesafety.driversafety.ui.TimeFormat
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

/** The admin's third main screen: the alert inbox. Tapping an alert marks it read and opens the driver. */
@Composable
fun AlertsScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit
) {
    val inbox by viewModel.alerts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.alerts_title),
                onBack = onBack,
                actions = {
                    if (inbox.alerts.any { !it.read }) {
                        TextButton(onClick = viewModel::markAllAlertsRead) {
                            Text(stringResource(R.string.alerts_mark_all_read))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            inbox.loading -> LoadingScreen(Modifier.padding(padding))
            inbox.loadError != null -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) { MessageCard(inbox.loadError) }
            inbox.alerts.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.alerts_empty_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.alerts_empty_body), style = MaterialTheme.typography.bodyLarge)
            }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(inbox.alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        title = AlertText.title(context, alert),
                        body = AlertText.body(context, alert),
                        onClick = {
                            viewModel.markAlertRead(alert.id)
                            if (alert.driverId.isNotBlank() && alert.type != AlertType.DRIVER_UNLINKED) onOpenDriver(alert.driverId)
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, title: String, body: String, onClick: () -> Unit) {
    val urgent = alert.type == AlertType.OVERSPEED_STARTED
    val colors = when {
        urgent && !alert.read -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
        !alert.read -> CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> CardDefaults.cardColors()
    }
    Card(onClick = onClick, colors = colors, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = when (alert.type) {
                    AlertType.OVERSPEED_STARTED -> Icons.Default.Warning
                    AlertType.BACK_TO_NORMAL -> Icons.Default.CheckCircle
                    else -> Icons.Default.Info
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (alert.read) FontWeight.Normal else FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = TimeFormat.dateTime(alert.timestampUtc, alert.timezoneId),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
