package com.lifesafety.driversafety.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.settings.DriverSettings
import com.lifesafety.driversafety.ui.components.BigButton
import com.lifesafety.driversafety.ui.components.Pill
import com.lifesafety.driversafety.ui.components.ScreenPadding
import com.lifesafety.driversafety.ui.components.StatTile
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The driver's trip screen. Top to bottom: who is monitoring, the permission card if needed, one big speed
 * card (number, limit badge, GPS and sync status), the trip's time/distance/top speed, and one big button.
 * The whole screen turns red while the alarm sounds. Nothing else to tap while driving.
 */
@Composable
fun TripScreen(
    live: TripLiveState,
    settings: DriverSettings,
    adminNames: String,
    permissions: PermissionStatus,
    onPermissionsChanged: () -> Unit,
    simulationAvailable: Boolean,
    simulating: Boolean,
    onToggleSimulation: (Boolean) -> Unit,
    onStartTrip: () -> Unit,
    onEndTrip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val alarm = live.overspeed
    // During a trip the service tracks the limit; when idle, show the admin's current setting.
    val limitKmh = if (live.tripActive) live.limitKmh else settings.speedLimitKmh
    val background = if (alarm) scheme.error else scheme.background
    val foreground = if (alarm) scheme.onError else scheme.onBackground
    val tileContainer = if (alarm) scheme.onError.copy(alpha = 0.16f) else scheme.surfaceContainerLow

    Surface(modifier = modifier.fillMaxSize(), color = background) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- Who is watching ----
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_notification), contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.trip_monitored_by, adminNames), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
                if (!permissions.allGranted) {
                    Spacer(Modifier.height(16.dp))
                    PermissionsCard(status = permissions, onChanged = onPermissionsChanged)
                }

                // ---- Speed card ----
                Spacer(Modifier.height(20.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (alarm) scheme.error else scheme.surfaceContainerLowest,
                        contentColor = foreground
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (alarm) 0.dp else 1.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (alarm) {
                            Text(
                                text = stringResource(R.string.trip_slow_down),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = live.speedKmh?.let { String.format(Locale.US, "%.0f", it) } ?: stringResource(R.string.trip_speed_no_fix),
                            fontSize = 112.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 120.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(stringResource(R.string.trip_speed_unit), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        Pill(
                            text = stringResource(R.string.trip_limit_badge, limitKmh),
                            container = if (alarm) scheme.onError else scheme.primaryContainer,
                            content = if (alarm) scheme.error else scheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(text = gpsLabel(live), container = tileContainer, content = foreground)
                            Pill(text = syncLabel(live.lastSyncAtMs), container = tileContainer, content = foreground)
                        }
                        if (live.pendingUploads > 0) {
                            Spacer(Modifier.height(8.dp))
                            Pill(
                                text = stringResource(R.string.trip_pending_short, live.pendingUploads),
                                container = if (alarm) tileContainer else scheme.tertiaryContainer,
                                content = if (alarm) foreground else scheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // ---- Trip facts ----
                Spacer(Modifier.height(16.dp))
                if (live.tripActive) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(formatElapsed(live.elapsedSec), stringResource(R.string.trip_stat_time), container = tileContainer, content = foreground, modifier = Modifier.weight(1f))
                        StatTile(String.format(Locale.US, "%.1f", live.distanceKm), stringResource(R.string.trip_stat_distance), container = tileContainer, content = foreground, modifier = Modifier.weight(1f))
                        StatTile(String.format(Locale.US, "%.0f", live.topSpeedKmh), stringResource(R.string.trip_stat_top), container = tileContainer, content = foreground, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(
                        text = live.endedByAdminName?.let { stringResource(R.string.trip_ended_by_admin, it) }
                            ?: stringResource(R.string.trip_status_idle),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = scheme.onSurfaceVariant
                    )
                }

                // ---- The one button ----
                Spacer(Modifier.height(24.dp))
                if (!live.tripActive) {
                    Button(
                        onClick = onStartTrip,
                        enabled = permissions.location && !live.starting,
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Text(stringResource(R.string.trip_start), style = MaterialTheme.typography.headlineSmall)
                    }
                } else if (settings.driverCanEndTrip) {
                    Button(
                        onClick = onEndTrip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alarm) scheme.onError else scheme.secondary,
                            contentColor = if (alarm) scheme.error else scheme.onSecondary
                        ),
                        modifier = Modifier.fillMaxWidth().height(72.dp)
                    ) {
                        Text(stringResource(R.string.trip_end), style = MaterialTheme.typography.headlineSmall)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.trip_auto_end_note, settings.autoEndMinutes),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                // ---- Debug only: fake speeds ----
                if (simulationAvailable) {
                    Spacer(Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = tileContainer, contentColor = foreground)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.trip_simulate), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = simulating, onCheckedChange = onToggleSimulation)
                            }
                            if (simulating) {
                                Text(stringResource(R.string.trip_simulating_note), style = MaterialTheme.typography.bodySmall)
                            }
                            if (live.tripActive && !settings.driverCanEndTrip) {
                                // Debug builds only: lets a simulated trip end without waiting for the auto-end minutes.
                                Spacer(Modifier.height(12.dp))
                                BigButton(text = stringResource(R.string.trip_end_test), onClick = onEndTrip)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun gpsLabel(live: TripLiveState): String = stringResource(
    when {
        live.locationPermissionMissing -> R.string.trip_gps_off
        !live.tripActive -> R.string.trip_gps_idle
        live.gpsOk && live.speedKmh != null -> R.string.trip_gps_ok
        else -> R.string.trip_gps_searching
    }
)

@Composable
private fun syncLabel(lastSyncAtMs: Long?): String =
    if (lastSyncAtMs == null) {
        stringResource(R.string.trip_last_sync_never)
    } else {
        stringResource(R.string.trip_last_sync, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastSyncAtMs)))
    }

private fun formatElapsed(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
