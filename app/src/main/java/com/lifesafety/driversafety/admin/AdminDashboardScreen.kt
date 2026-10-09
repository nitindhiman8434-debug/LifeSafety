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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.lifesafety.driversafety.ui.components.Avatar
import com.lifesafety.driversafety.ui.components.BigButton
import com.lifesafety.driversafety.ui.components.BigOutlinedButton
import com.lifesafety.driversafety.ui.components.EmptyState
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard
import com.lifesafety.driversafety.ui.components.Pill
import com.lifesafety.driversafety.ui.components.ScreenPadding
import com.lifesafety.driversafety.ui.components.SectionHeader
import com.lifesafety.driversafety.ui.components.StatTile
import java.util.Calendar

/**
 * The admin's first main screen. Top to bottom: a greeting with the date, an overview (on trip, over the
 * limit, unread alerts), the live map, and one card per driver. "Add driver" is the floating button;
 * the bell opens the alerts inbox. Phase 4 adds the "tracking lost" counter and status.
 */
@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    adminName: String,
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
                title = stringResource(R.string.dash_title),
                actions = {
                    IconButton(onClick = onOpenAlerts) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text(unread.coerceAtMost(99).toString()) } }) {
                            Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.alerts_title))
                        }
                    }
                    AccountMenu(
                        canChangeRole = !state.loading && state.links.isEmpty(),
                        onSignOut = onSignOut,
                        onChangeRole = onChangeRole,
                        name = adminName
                    )
                }
            )
        },
        floatingActionButton = {
            if (!state.loading && state.links.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddDriver,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.admin_add_driver)) }
                )
            }
        }
    ) { padding ->
        when {
            state.loading -> LoadingScreen(Modifier.padding(padding))
            state.loadError != null -> Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) { MessageCard(state.loadError) }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 96.dp)
            ) {
                item { Greeting(adminName = adminName, driverCount = state.links.size, now = now) }
                if (state.links.isEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                EmptyState(
                                    icon = Icons.Default.Person,
                                    title = stringResource(R.string.admin_no_drivers_title),
                                    body = stringResource(R.string.admin_no_drivers_body)
                                )
                                BigButton(text = stringResource(R.string.admin_add_driver), onClick = onAddDriver)
                                Spacer(Modifier.height(12.dp))
                                BigOutlinedButton(text = stringResource(R.string.admin_join_second), onClick = onJoinAsSecondAdmin)
                            }
                        }
                    }
                } else {
                    item {
                        Spacer(Modifier.height(20.dp))
                        SectionHeader(stringResource(R.string.dash_overview))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile(
                                value = onTrip.toString(),
                                label = stringResource(R.string.dash_on_trip),
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                value = overLimit.toString(),
                                label = stringResource(R.string.dash_over_limit),
                                container = if (overLimit > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                content = if (overLimit > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                value = unread.toString(),
                                label = stringResource(R.string.dash_open_alerts),
                                container = if (unread > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                content = if (unread > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenAlerts
                            )
                        }
                    }
                    if (pins.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            SectionHeader(stringResource(R.string.dash_live_map))
                            DriverMap(
                                pins = pins,
                                modifier = Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.medium)
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(16.dp))
                        SectionHeader(stringResource(R.string.dash_your_drivers))
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
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.dash_join_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onJoinAsSecondAdmin) { Text(stringResource(R.string.dash_join_action)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Greeting(adminName: String, driverCount: Int, now: Long) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = stringResource(
        when {
            hour < 12 -> R.string.dash_greeting_morning
            hour < 17 -> R.string.dash_greeting_afternoon
            else -> R.string.dash_greeting_evening
        },
        adminName.substringBefore(' ').ifBlank { adminName }
    )
    Column {
        Text(greeting, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            text = TimeFormat.longDate(now) + "  ·  " + if (driverCount == 1) stringResource(R.string.dash_managing_one) else stringResource(R.string.dash_managing_many, driverCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(
                    name = link.driverName,
                    container = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                    content = if (alert) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(link.driverName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        text = stringResource(if (link.role == LinkRole.PRIMARY) R.string.admin_role_primary else R.string.admin_role_secondary),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(link = link, status = status)
            }
            if (link.status == LinkStatus.ACTIVE && live != null) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (status == DriverStatus.ON_TRIP || status == DriverStatus.OFFLINE) {
                        Text(
                            text = stringResource(R.string.dash_speed_vs_limit, TimeFormat.kmh(live.speedKmh), live.speedLimitKmh),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.detail_idle_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val battery = if (live.batteryPercent >= 0) stringResource(R.string.dash_battery, live.batteryPercent) else ""
                    val updated = live.lastSyncAtUtc?.let { TimeFormat.ago(context, it, now) }.orEmpty()
                    Text(
                        text = listOf(battery, updated).filter { it.isNotBlank() }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (live.simulated && status == DriverStatus.ON_TRIP) {
                    Text(stringResource(R.string.dash_simulated), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** On trip (green), Offline (amber), Idle (grey), Waiting for consent (grey), No data yet (grey). */
@Composable
fun StatusChip(link: Link, status: DriverStatus) {
    val (label, container, content) = when {
        link.status != LinkStatus.ACTIVE -> Triple(stringResource(R.string.link_status_pending_short), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        status == DriverStatus.ON_TRIP -> Triple(stringResource(R.string.status_on_trip), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        status == DriverStatus.OFFLINE -> Triple(stringResource(R.string.status_offline), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        status == DriverStatus.IDLE -> Triple(stringResource(R.string.status_idle), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple(stringResource(R.string.status_no_data), MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Pill(text = label, container = container, content = content)
}
