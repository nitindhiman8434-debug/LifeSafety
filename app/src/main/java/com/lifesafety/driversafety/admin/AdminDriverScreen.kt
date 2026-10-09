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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.lifesafety.driversafety.settings.DriverSettings
import com.lifesafety.driversafety.ui.TimeFormat
import com.lifesafety.driversafety.ui.asString
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.Avatar
import com.lifesafety.driversafety.ui.components.BigButton
import com.lifesafety.driversafety.ui.components.BigOutlinedButton
import com.lifesafety.driversafety.ui.components.ConfirmDialog
import com.lifesafety.driversafety.ui.components.EmptyState
import com.lifesafety.driversafety.ui.components.InfoRow
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard
import com.lifesafety.driversafety.ui.components.Pill
import com.lifesafety.driversafety.ui.components.ScreenPadding
import com.lifesafety.driversafety.ui.components.SectionHeader
import com.lifesafety.driversafety.ui.components.StatTile

private enum class PendingAction { REMOVE_DRIVER, REMOVE_SECOND_ADMIN, LEAVE, END_TRIP, REQUEST_START }

/**
 * The admin's second main screen, one driver. A header with the driver's name, role and status, then four
 * tabs: Live (speed, map, actions, recent events), Trips, Settings (editable by the primary admin) and
 * Admins (the Phase 1 add/remove/leave section). Each key action is within two taps of the dashboard.
 */
@Composable
fun AdminDriverScreen(
    viewModel: AdminViewModel,
    driverId: String,
    onBack: () -> Unit,
    onAddSecondAdmin: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val anyBusy by viewModel.busy.collectAsStateWithLifecycle()
    val actionDriverId by viewModel.actionDriverId.collectAsStateWithLifecycle()
    val anyMessage by viewModel.message.collectAsStateWithLifecycle()
    val anyInfo by viewModel.info.collectAsStateWithLifecycle()
    // Only this driver's action and its result; an action started on another driver's screen stays there.
    val mine = actionDriverId == driverId
    val busy = anyBusy && mine
    val message = if (mine) anyMessage else null
    val info = if (mine) anyInfo else null
    val now by viewModel.now.collectAsStateWithLifecycle()
    val recordFlow = remember(driverId) { viewModel.driverRecordFlow(driverId) }
    val record by recordFlow.collectAsStateWithLifecycle(initialValue = null)
    val tripsFlow = remember(driverId) { viewModel.tripsFlow(driverId) }
    val trips by tripsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val eventsFlow = remember(driverId) { viewModel.eventsFlow(driverId) }
    val events by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val link = state.links.firstOrNull { it.driverId == driverId }
    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // The result of an action pops up at the bottom, where the buttons are; the cards at the top stay as well.
    val snackbar = remember { SnackbarHostState() }
    val infoText = info?.asString()
    val messageText = message?.asString()
    LaunchedEffect(infoText, messageText) {
        (messageText ?: infoText)?.let { snackbar.showSnackbar(it) }
    }

    // The link disappears when the driver is removed or this admin leaves: go back to the dashboard.
    LaunchedEffect(state.loading, link == null) {
        if (!state.loading && link == null) onBack()
    }

    val driverName = link?.driverName ?: stringResource(R.string.driver_detail_title)
    val phone = record?.phone

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
            PendingAction.END_TRIP -> ConfirmDialog(
                title = stringResource(R.string.detail_end_trip_title),
                text = stringResource(R.string.detail_end_trip_text, driverName),
                confirmLabel = stringResource(R.string.detail_end_trip),
                onConfirm = { viewModel.endTripNow(driverId) },
                onDismiss = { pendingAction = null }
            )
            PendingAction.REQUEST_START -> ConfirmDialog(
                title = stringResource(R.string.detail_request_start_title),
                text = stringResource(R.string.detail_request_start_text, driverName),
                confirmLabel = stringResource(R.string.action_send),
                onConfirm = { viewModel.requestTripStart(driverId) },
                onDismiss = { pendingAction = null }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = driverName,
                onBack = onBack,
                actions = {
                    if (phone != null) {
                        IconButton(onClick = { dial(context, phone) }) {
                            Icon(Icons.Default.Call, contentDescription = stringResource(R.string.detail_call_short))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (link == null) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        val isPrimary = link.role == LinkRole.PRIMARY
        val isActive = link.status == LinkStatus.ACTIVE
        val live = record?.live
        val status = live.statusAt(now)

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DriverHeader(link = link, status = status, phone = phone, modifier = Modifier.padding(horizontal = ScreenPadding))
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) else Spacer(Modifier.height(12.dp))

            if (!isActive) {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding)) {
                    MessageCard(message)
                    if (message != null) Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.link_status_pending), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.detail_pending_note), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    AdminsSection(
                        link = link, record = record, isPrimary = isPrimary, isActive = false, busy = busy,
                        onAddSecondAdmin = onAddSecondAdmin,
                        onRemoveSecond = { pendingAction = PendingAction.REMOVE_SECOND_ADMIN },
                        onRemoveDriver = { pendingAction = PendingAction.REMOVE_DRIVER },
                        onLeave = { pendingAction = PendingAction.LEAVE }
                    )
                }
                return@Scaffold
            }

            val tabs = listOf(
                stringResource(R.string.detail_tab_live),
                stringResource(R.string.detail_tab_trips),
                stringResource(R.string.detail_tab_settings),
                stringResource(R.string.detail_tab_admins)
            )
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }

            when (tab) {
                0 -> LiveTab(
                    driverName = driverName, record = record, status = status, now = now, isPrimary = isPrimary,
                    busy = busy, message = message, events = events,
                    onRequestStart = { pendingAction = PendingAction.REQUEST_START },
                    onEndTrip = { pendingAction = PendingAction.END_TRIP }
                )
                1 -> TripsTab(trips = trips)
                2 -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding)) {
                    MessageCard(message)
                    if (message != null) Spacer(Modifier.height(16.dp))
                    DriverSettingsCard(
                        settings = record?.settings ?: DriverSettings.DEFAULT,
                        phone = record?.phone,
                        editable = isPrimary,
                        busy = busy,
                        onSave = { settings, newPhone -> viewModel.saveSettings(driverId, settings, newPhone) }
                    )
                    Spacer(Modifier.height(24.dp))
                }
                else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding)) {
                    MessageCard(message)
                    if (message != null) Spacer(Modifier.height(16.dp))
                    AdminsSection(
                        link = link, record = record, isPrimary = isPrimary, isActive = true, busy = busy,
                        onAddSecondAdmin = onAddSecondAdmin,
                        onRemoveSecond = { pendingAction = PendingAction.REMOVE_SECOND_ADMIN },
                        onRemoveDriver = { pendingAction = PendingAction.REMOVE_DRIVER },
                        onLeave = { pendingAction = PendingAction.LEAVE }
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverHeader(link: Link, status: DriverStatus, phone: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name = link.driverName, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(link.driverName, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                text = listOfNotNull(
                    stringResource(if (link.role == LinkRole.PRIMARY) R.string.admin_role_primary else R.string.admin_role_secondary),
                    phone
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(link = link, status = status)
    }
}

@Composable
private fun LiveTab(
    driverName: String,
    record: DriverRecord?,
    status: DriverStatus,
    now: Long,
    isPrimary: Boolean,
    busy: Boolean,
    message: com.lifesafety.driversafety.ui.UiText?,
    events: List<DriverEvent>,
    onRequestStart: () -> Unit,
    onEndTrip: () -> Unit
) {
    val context = LocalContext.current
    val live = record?.live
    val alert = status == DriverStatus.ON_TRIP && live?.overspeedNow == true
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding, vertical = 12.dp)) {
        MessageCard(message)
        if (message != null) Spacer(Modifier.height(16.dp))

        // ---- Speed and trip facts ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = if (alert) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) else CardDefaults.cardColors()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                when {
                    live == null -> Text(stringResource(R.string.detail_no_data_yet), style = MaterialTheme.typography.bodyLarge)
                    status == DriverStatus.ON_TRIP || status == DriverStatus.OFFLINE -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(TimeFormat.kmh(live.speedKmh), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.detail_speed_of_limit, live.speedLimitKmh),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (alert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (alert) {
                            Text(stringResource(R.string.detail_over_limit_now), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (status == DriverStatus.OFFLINE) {
                            Text(stringResource(R.string.detail_offline_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(
                                value = live.tripStartedAtUtc?.let { TimeFormat.time(it, null) } ?: "–",
                                label = stringResource(R.string.detail_stat_since),
                                container = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                value = TimeFormat.km(live.distanceKm),
                                label = stringResource(R.string.detail_stat_distance),
                                container = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                value = TimeFormat.kmh(live.topSpeedKmh),
                                label = stringResource(R.string.detail_stat_top),
                                container = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)
                            )
                        }
                        if (live.simulated) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.dash_simulated), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    else -> Text(stringResource(R.string.detail_idle_note), style = MaterialTheme.typography.bodyLarge)
                }
                if (live != null) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    val battery = if (live.batteryPercent >= 0) {
                        stringResource(R.string.dash_battery_value, live.batteryPercent) + if (live.isCharging) " " + stringResource(R.string.detail_charging) else ""
                    } else "–"
                    InfoRow(stringResource(R.string.detail_stat_battery), battery)
                    InfoRow(stringResource(R.string.detail_stat_updated), live.lastSyncAtUtc?.let { TimeFormat.ago(context, it, now) } ?: "–")
                }
            }
        }

        // ---- Map ----
        Spacer(Modifier.height(16.dp))
        if (live != null && live.hasPosition) {
            SectionHeader(
                title = stringResource(R.string.detail_map_title),
                action = {
                    TextButton(onClick = { openInMaps(context, live.latitude!!, live.longitude!!, driverName) }) {
                        Text(stringResource(R.string.detail_open_in_maps))
                    }
                }
            )
            DriverMap(
                pins = listOf(
                    MapPin(
                        id = record.id,
                        latitude = live.latitude!!,
                        longitude = live.longitude!!,
                        title = driverName,
                        snippet = if (status == DriverStatus.ON_TRIP) TimeFormat.kmh(live.speedKmh) + " km/h" else null,
                        alert = alert
                    )
                ),
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium)
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Default.Place,
                    title = stringResource(R.string.detail_no_position_title),
                    body = stringResource(R.string.detail_no_position)
                )
            }
        }

        // ---- Actions ----
        Spacer(Modifier.height(20.dp))
        if (isPrimary && (status == DriverStatus.ON_TRIP || status == DriverStatus.OFFLINE)) {
            Button(
                onClick = onEndTrip,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.detail_end_trip), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (status != DriverStatus.ON_TRIP) {
            BigOutlinedButton(text = stringResource(R.string.detail_request_start), onClick = onRequestStart, enabled = !busy)
        }

        // ---- Recent events ----
        Spacer(Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.detail_events_title))
        Card(modifier = Modifier.fillMaxWidth()) {
            if (events.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.detail_events_empty_title),
                    body = stringResource(R.string.detail_events_empty)
                )
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    events.forEachIndexed { index, event ->
                        EventRow(event)
                        if (index < events.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TripsTab(trips: List<TripSummary>) {
    if (trips.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(ScreenPadding)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Default.DateRange,
                    title = stringResource(R.string.detail_trips_empty_title),
                    body = stringResource(R.string.detail_trips_empty)
                )
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 12.dp)) {
        items(trips, key = { it.id }) { trip ->
            TripCard(trip)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EventRow(event: DriverEvent) {
    val context = LocalContext.current
    val title = when (event.eventType) {
        "overspeed_started" -> stringResource(R.string.event_overspeed, event.speedKmh?.let { TimeFormat.kmh(it) } ?: "?", event.speedLimitKmh ?: 0)
        "back_to_normal" -> stringResource(R.string.event_back_to_normal, event.topSpeedKmh?.let { TimeFormat.kmh(it) } ?: "?", TimeFormat.duration(context, event.durationSec ?: 0))
        "trip_started" -> stringResource(R.string.event_trip_started)
        "trip_ended" -> stringResource(R.string.event_trip_ended, TimeFormat.duration(context, event.durationSec ?: 0), event.topSpeedKmh?.let { TimeFormat.kmh(it) } ?: "0")
        else -> event.eventType
    }
    val flags = listOfNotNull(
        if (event.delayed) stringResource(R.string.alert_delayed_suffix) else null,
        if (event.mockLocationSuspected) stringResource(R.string.event_mock_location) else null
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (event.eventType == "overspeed_started") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = listOfNotNull(TimeFormat.dateTime(event.timestampUtc, event.timezoneId), event.address, flags.joinToString(" ").takeIf { it.isNotBlank() })
                .joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TripCard(trip: TripSummary) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(TimeFormat.dateTime(trip.startedAtUtc, trip.timezoneId), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (trip.status == "active") {
                    Pill(stringResource(R.string.status_on_trip), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                } else if (trip.overspeedCount > 0) {
                    Pill(
                        stringResource(R.string.trip_overspeed_count, trip.overspeedCount),
                        MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(TimeFormat.duration(context, trip.durationSec), stringResource(R.string.trip_stat_time), container = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f))
                StatTile(TimeFormat.km(trip.distanceKm), stringResource(R.string.trip_stat_distance), container = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f))
                StatTile(TimeFormat.kmh(trip.topSpeedKmh), stringResource(R.string.trip_stat_top), container = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f))
            }
            val reason = when (trip.endReason) {
                "auto" -> stringResource(R.string.trip_end_auto)
                "driver" -> stringResource(R.string.trip_end_driver)
                "admin" -> stringResource(R.string.trip_end_admin)
                "interrupted" -> stringResource(R.string.trip_end_interrupted)
                else -> null
            }
            if (reason != null) {
                Spacer(Modifier.height(8.dp))
                Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AdminsSection(
    link: Link,
    record: DriverRecord?,
    isPrimary: Boolean,
    isActive: Boolean,
    busy: Boolean,
    onAddSecondAdmin: () -> Unit,
    onRemoveSecond: () -> Unit,
    onRemoveDriver: () -> Unit,
    onLeave: () -> Unit
) {
    val you = stringResource(R.string.detail_you_short)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isPrimary) {
                AdminRow(name = link.adminName, role = stringResource(R.string.detail_role_primary), suffix = you)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val secondaryName = record?.secondaryAdminName
                when {
                    secondaryName == null -> Text(
                        stringResource(R.string.detail_secondary_none),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    record.secondaryStatus == "pending_consent" -> AdminRow(name = secondaryName, role = stringResource(R.string.detail_role_secondary), suffix = stringResource(R.string.detail_pending_short))
                    else -> AdminRow(name = secondaryName, role = stringResource(R.string.detail_role_secondary), suffix = null)
                }
            } else {
                AdminRow(name = record?.primaryAdminName ?: stringResource(R.string.detail_unknown), role = stringResource(R.string.detail_role_primary), suffix = null)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                AdminRow(name = link.adminName, role = stringResource(R.string.detail_role_secondary), suffix = you)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (isPrimary) {
        if (isActive) {
            if (record?.secondaryAdminId == null) {
                BigButton(text = stringResource(R.string.detail_add_second), onClick = onAddSecondAdmin, enabled = !busy)
            } else {
                BigOutlinedButton(text = stringResource(R.string.detail_remove_second), onClick = onRemoveSecond, enabled = !busy)
            }
            Spacer(Modifier.height(12.dp))
        }
        DangerButton(text = stringResource(R.string.detail_remove_driver), onClick = onRemoveDriver, enabled = !busy)
    } else {
        DangerButton(text = stringResource(R.string.detail_leave), onClick = onLeave, enabled = !busy)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun AdminRow(name: String, role: String, suffix: String?) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name = name, size = 40.dp, container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(role, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (suffix != null) {
            Pill(suffix, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
