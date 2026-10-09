package com.lifesafety.driversafety.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.lifesafety.driversafety.pairing.DriverRecord
import com.lifesafety.driversafety.pairing.Link
import com.lifesafety.driversafety.pairing.LinkRole
import com.lifesafety.driversafety.pairing.LinkStatus
import com.lifesafety.driversafety.ui.TimeFormat
import com.lifesafety.driversafety.ui.components.AccountMenu
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

/**
 * The admin's first main screen: counters (on trip, over the limit now, unread alerts), a map with every
 * driver's last known position, and a card per driver. The bell opens the alerts inbox.
 * Phase 4 adds the "tracking lost" counter and status.
 */
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onAddDriver: () -> Unit,
    onJoinAsSecondAdmin: () -> Unit,
    onOpenDriver: (String) -> Unit,
    onOpenAlerts: () -> Unit,
    onSignOut: () -> Unit,
    onChangeRole: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drivers by viewModel.drivers.collectAsStateWithLifecycle()
    val unread by viewModel.unreadCount.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    val statuses = drivers.mapValues { (_, record) -> record.live.statusAt(now) }
    val onTrip = statuses.count { it.value == DriverStatus.ON_TRIP }
    val overLimit = drivers.count { (id, record) -> statuses[id] == DriverStatus.ON_TRIP && record.live?.overspeedNow == true }
    val pins = drivers.values.mapNotNull { record ->
        val live = record.live ?: return@mapNotNull null
        if (!live.hasPosition) return@mapNotNull null
        MapPin(
            id = record.id,
            latitude = live.latitude!!,
            longitude = live.longitude!!,
            title = record.displayName,
            snippet = if (statuses[record.id] == DriverStatus.ON_TRIP) TimeFormat.kmh(live.speedKmh) + " km/h" else null,
            alert = statuses[record.id] == DriverStatus.ON_TRIP && live.overspeedNow
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.admin_title),
                actions = {
                    IconButton(onClick = onOpenAlerts) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }) {
                            Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.alerts_title))
                        }
                    }
                    AccountMenu(
                        canChangeRole = !state.loading && state.links.isEmpty(),
                        onSignOut = onSignOut,
                        onChangeRole = onChangeRole
                    )
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> LoadingScreen(Modifier.padding(padding))
            state.loadError != null -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) { MessageCard(state.loadError) }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Button(onClick = onAddDriver, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.admin_add_driver), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onJoinAsSecondAdmin, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.admin_join_second), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (state.links.isEmpty()) {
                    item {
                        Text(stringResource(R.string.admin_no_drivers_title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.admin_no_drivers_body), style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    item {
                        CountersRow(onTrip = onTrip, overLimit = overLimit, unread = unread)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (pins.isNotEmpty()) {
                        item {
                            DriverMap(
                                pins = pins,
                                modifier = Modifier.fillMaxWidth().height(220.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                    items(state.links, key = { it.id }) { link ->
                        DriverCard(
                            link = link,
                            record = drivers[link.driverId],
                            status = statuses[link.driverId] ?: DriverStatus.NO_DATA,
                            now = now,
                            onClick = { onOpenDriver(link.driverId) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountersRow(onTrip: Int, overLimit: Int, unread: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Counter(onTrip, stringResource(R.string.dash_on_trip), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f))
        Counter(
            overLimit, stringResource(R.string.dash_over_limit),
            if (overLimit > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
            if (overLimit > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            Modifier.weight(1f)
        )
        Counter(unread, stringResource(R.string.dash_open_alerts), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f))
    }
}

@Composable
private fun Counter(value: Int, label: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = container, contentColor = content, modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DriverCard(link: Link, record: DriverRecord?, status: DriverStatus, now: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val live = record?.live
    val alert = status == DriverStatus.ON_TRIP && live?.overspeedNow == true
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (alert) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(link.driverName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                StatusChip(link = link, status = status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (link.role == LinkRole.PRIMARY) R.string.admin_role_primary else R.string.admin_role_secondary),
                style = MaterialTheme.typography.bodyMedium,
                color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
            )
            if (link.status == LinkStatus.ACTIVE && live != null) {
                Spacer(Modifier.height(8.dp))
                if (status == DriverStatus.ON_TRIP || status == DriverStatus.OFFLINE) {
                    Text(
                        text = stringResource(R.string.dash_speed_vs_limit, TimeFormat.kmh(live.speedKmh), live.speedLimitKmh),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                val battery = if (live.batteryPercent >= 0) stringResource(R.string.dash_battery, live.batteryPercent) else ""
                val updated = live.lastSyncAtUtc?.let { TimeFormat.ago(context, it, now) }.orEmpty()
                Text(listOf(battery, updated).filter { it.isNotBlank() }.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
                if (live.simulated && status == DriverStatus.ON_TRIP) {
                    Text(stringResource(R.string.dash_simulated), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun StatusChip(link: Link, status: DriverStatus) {
    val (label, container, content) = when {
        link.status != LinkStatus.ACTIVE -> Triple(stringResource(R.string.link_status_pending), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        status == DriverStatus.ON_TRIP -> Triple(stringResource(R.string.status_on_trip), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        status == DriverStatus.OFFLINE -> Triple(stringResource(R.string.status_offline), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        status == DriverStatus.IDLE -> Triple(stringResource(R.string.status_idle), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple(stringResource(R.string.status_no_data), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
