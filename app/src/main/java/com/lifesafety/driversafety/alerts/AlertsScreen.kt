package com.lifesafety.driversafety.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import com.lifesafety.driversafety.ui.components.EmptyState
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard
import com.lifesafety.driversafety.ui.components.ScreenPadding

/** The admin's third main screen: the alert inbox, grouped by day. Tapping an alert marks it read and opens the driver. */
@Composable
fun AlertsScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit
) {
    val inbox by viewModel.alerts.collectAsStateWithLifecycle()
    val links by viewModel.state.collectAsStateWithLifecycle()
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
            inbox.loadError != null -> Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) { MessageCard(inbox.loadError) }
            inbox.alerts.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.alerts_empty_title),
                        body = stringResource(R.string.alerts_empty_body)
                    )
                }
            }

            else -> {
                // Group by the day the alert happened, newest first (the inbox flow is already sorted).
                val groups = inbox.alerts.groupBy { TimeFormat.dayLabel(context, it.timestampUtc) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp)
                ) {
                    groups.forEach { (day, alerts) ->
                        item(key = "day-$day") {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                            )
                        }
                        items(alerts, key = { it.id }) { alert ->
                            AlertCard(
                                alert = alert,
                                title = AlertText.title(context, alert),
                                body = AlertText.body(context, alert),
                                onClick = {
                                    viewModel.markAlertRead(alert.id)
                                    // Only a driver this admin is still linked to has a detail screen to open.
                                    if (links.links.any { it.driverId == alert.driverId }) onOpenDriver(alert.driverId)
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, title: String, body: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, iconBg, iconFg) = when (alert.type) {
        AlertType.OVERSPEED_STARTED -> Triple(Icons.Default.Warning, scheme.errorContainer, scheme.onErrorContainer)
        AlertType.BACK_TO_NORMAL -> Triple(Icons.Default.CheckCircle, scheme.secondaryContainer, scheme.onSecondaryContainer)
        AlertType.ADMIN_ADDED, AlertType.ADMIN_REMOVED -> Triple(Icons.Default.Person, scheme.primaryContainer, scheme.onPrimaryContainer)
        else -> Triple(Icons.Default.Info, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (alert.read) CardDefaults.cardColors() else CardDefaults.cardColors(containerColor = scheme.surfaceContainerLowest),
        border = if (alert.read) null else androidx.compose.foundation.BorderStroke(1.dp, iconBg)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(40.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconFg, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (alert.read) FontWeight.Medium else FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = TimeFormat.time(alert.timestampUtc, alert.timezoneId),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            if (!alert.read) {
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.padding(top = 6.dp).size(10.dp).background(scheme.primary, CircleShape))
            }
        }
    }
}
