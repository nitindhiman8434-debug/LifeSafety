package com.lifesafety.driversafety.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.ConfirmDialog
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

private enum class PendingAction { REMOVE_DRIVER, REMOVE_SECOND_ADMIN, LEAVE, END_TRIP, REQUEST_START }

/**
 * The admin's second main screen, one driver: live status and map, call / request trip start / end trip now,
 * settings (editable by the primary admin), the recent events, the trip list, and the Admins section.
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
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val info by viewModel.info.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val recordFlow = remember(driverId) { viewModel.driverRecordFlow(driverId) }
    val record by recordFlow.collectAsStateWithLifecycle(initialValue = null)
    val tripsFlow = remember(driverId) { viewModel.tripsFlow(driverId) }
    val trips by tripsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val eventsFlow = remember(driverId) { viewModel.eventsFlow(driverId) }
    val events by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
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
        topBar = { AppTopBar(title = driverName, onBack = onBack) }
    ) { padding ->
        if (link == null) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        val isPrimary = link.role == LinkRole.PRIMARY
        val isActive = link.status == LinkStatus.ACTIVE
        val live = record?.live
        val status = live.statusAt(now)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(16.dp))
            MessageCard(info, isError = false)
            if (info != null) Spacer(Modifier.height(16.dp))

            if (!isActive) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.detail_status, stringResource(R.string.link_status_pending)),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.detail_pending_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                // ---- Live status ----
                LiveCard(link = link, record = record, status = status, now = now)
                Spacer(Modifier.height(16.dp))
                if (live != null && live.hasPosition) {
                    DriverMap(
                        pins = listOf(
                            MapPin(
                                id = driverId,
                                latitude = live.latitude!!,
                                longitude = live.longitude!!,
                                title = driverName,
                                snippet = if (status == DriverStatus.ON_TRIP) TimeFormat.kmh(live.speedKmh) + " km/h" else null,
                                alert = status == DriverStatus.ON_TRIP && live.overspeedNow
                            )
                        ),
                        modifier = Modifier.fillMaxWidth().height(240.dp)
                    )
                    TextButton(onClick = { openInMaps(context, live.latitude!!, live.longitude!!, driverName) }) {
                        Text(stringResource(R.string.detail_open_in_maps))
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(stringResource(R.string.detail_no_position), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                }

                // ---- Actions ----
                val phone = record?.phone
                if (phone != null) {
                    Button(onClick = { dial(context, phone) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text(stringResource(R.string.detail_call, phone), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (status == DriverStatus.ON_TRIP || status == DriverStatus.OFFLINE) {
                    if (isPrimary) {
                        Button(
                            onClick = { pendingAction = PendingAction.END_TRIP },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Text(stringResource(R.string.detail_end_trip), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { pendingAction = PendingAction.REQUEST_START },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.detail_request_start), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))

                // ---- Settings ----
                DriverSettingsCard(
                    settings = record?.settings ?: com.lifesafety.driversafety.settings.DriverSettings.DEFAULT,
                    phone = record?.phone,
                    editable = isPrimary,
                    busy = busy,
                    onSave = { settings, newPhone -> viewModel.saveSettings(driverId, settings, newPhone) }
                )
                Spacer(Modifier.height(24.dp))

                // ---- Recent events ----
                Text(stringResource(R.string.detail_events_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (events.isEmpty()) {
                    Text(stringResource(R.string.detail_events_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    events.forEach { event ->
                        EventRow(event)
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(24.dp))

                // ---- Trips ----
                Text(stringResource(R.string.detail_trips_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (trips.isEmpty()) {
                    Text(stringResource(R.string.detail_trips_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    trips.forEach { trip ->
                        TripRow(trip)
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ---- Admins ----
            AdminsSection(
                link = link,
                record = record,
                isPrimary = isPrimary,
                isActive = isActive,
                busy = busy,
                onAddSecondAdmin = onAddSecondAdmin,
                onRemoveSecond = { pendingAction = PendingAction.REMOVE_SECOND_ADMIN },
                onRemoveDriver = { pendingAction = PendingAction.REMOVE_DRIVER },
                onLeave = { pendingAction = PendingAction.LEAVE }
            )
        }
    }
}

@Composable
private fun LiveCard(link: Link, record: DriverRecord?, status: DriverStatus, now: Long) {
    val context = LocalContext.current
    val live = record?.live
    val alert = status == DriverStatus.ON_TRIP && live?.overspeedNow == true
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.detail_my_role,
                        stringResource(if (link.role == LinkRole.PRIMARY) R.string.detail_role_primary else R.string.detail_role_secondary)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(link = link, status = status)
            }
            Spacer(Modifier.height(12.dp))
            when {
                live == null -> Text(stringResource(R.string.detail_no_data_yet), style = MaterialTheme.typography.bodyLarge)
                status == DriverStatus.ON_TRIP || status == DriverStatus.OFFLINE -> {
                    Text(
                        text = stringResource(R.string.dash_speed_vs_limit, TimeFormat.kmh(live.speedKmh), live.speedLimitKmh),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (alert) {
                        Text(stringResource(R.string.detail_over_limit_now), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                    if (status == DriverStatus.OFFLINE) {
                        Text(stringResource(R.string.detail_offline_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    val since = live.tripStartedAtUtc?.let { TimeFormat.time(it, null) }.orEmpty()
                    Text(
                        text = stringResource(R.string.detail_current_trip, since, TimeFormat.km(live.distanceKm), TimeFormat.kmh(live.topSpeedKmh)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (live.simulated) Text(stringResource(R.string.dash_simulated), style = MaterialTheme.typography.bodySmall)
                }
                else -> Text(stringResource(R.string.detail_idle_note), style = MaterialTheme.typography.bodyLarge)
            }
            if (live != null) {
                Spacer(Modifier.height(8.dp))
                val battery = if (live.batteryPercent >= 0) {
                    stringResource(R.string.dash_battery, live.batteryPercent) + if (live.isCharging) " " + stringResource(R.string.detail_charging) else ""
                } else ""
                val updated = live.lastSyncAtUtc?.let { stringResource(R.string.detail_updated, TimeFormat.ago(context, it, now)) }.orEmpty()
                Text(listOf(battery, updated).filter { it.isNotBlank() }.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
            }
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
private fun TripRow(trip: TripSummary) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(TimeFormat.dateTime(trip.startedAtUtc, trip.timezoneId), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (trip.status == "active") {
                Text(stringResource(R.string.status_on_trip), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = stringResource(
                R.string.trip_row_summary,
                TimeFormat.duration(context, trip.durationSec),
                TimeFormat.km(trip.distanceKm),
                TimeFormat.kmh(trip.topSpeedKmh),
                trip.overspeedCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (trip.overspeedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        val reason = when (trip.endReason) {
            "auto" -> stringResource(R.string.trip_end_auto)
            "driver" -> stringResource(R.string.trip_end_driver)
            "admin" -> stringResource(R.string.trip_end_admin)
            "interrupted" -> stringResource(R.string.trip_end_interrupted)
            else -> null
        }
        if (reason != null) Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                record.secondaryStatus == "pending_consent" -> stringResource(R.string.detail_secondary_pending, secondaryName)
                else -> stringResource(R.string.detail_secondary, secondaryName)
            },
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        if (isActive) {
            if (record?.secondaryAdminId == null) {
                Button(onClick = onAddSecondAdmin, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.detail_add_second), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                OutlinedButton(onClick = onRemoveSecond, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.detail_remove_second), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(
            onClick = onRemoveDriver,
            enabled = !busy,
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
            onClick = onLeave,
            enabled = !busy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.detail_leave), style = MaterialTheme.typography.titleMedium)
        }
    }
    Spacer(Modifier.height(24.dp))
}
