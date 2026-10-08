package com.lifesafety.driversafety.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.settings.DriverSettings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The driver's trip screen: huge speed, limit badge, trip timer, admin names, sync and GPS status,
 * one big Start Trip (or End Trip) button. The whole screen turns red while the alarm sounds.
 * Nothing else to tap while driving.
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
    val context = LocalContext.current
    val alarm = live.overspeed
    val background = if (alarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.background
    val foreground = if (alarm) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onBackground

    Surface(modifier = modifier.fillMaxSize(), color = background) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- Status line ----
                Text(stringResource(R.string.trip_admins, adminNames), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = gpsLabel(live) + "  ·  " + syncLabel(live.lastSyncAtMs, context),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (live.pendingUploads > 0) {
                    Text(stringResource(R.string.trip_pending, live.pendingUploads), style = MaterialTheme.typography.bodySmall)
                }
                if (!permissions.allGranted) {
                    Spacer(Modifier.height(16.dp))
                    PermissionsCard(status = permissions, onChanged = onPermissionsChanged)
                }

                // ---- Speed ----
                Spacer(Modifier.height(24.dp))
                if (alarm) {
                    Text(
                        text = stringResource(R.string.trip_slow_down),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = live.speedKmh?.let { String.format(Locale.US, "%.0f", it) } ?: stringResource(R.string.trip_speed_no_fix),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 130.sp,
                    textAlign = TextAlign.Center
                )
                Text(stringResource(R.string.trip_speed_unit), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (alarm) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (alarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.trip_limit_badge, live.limitKmh),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (live.tripActive) {
                        stringResource(R.string.trip_status_on_trip) + "  ·  " + formatElapsed(live.elapsedSec) + "  ·  " +
                            stringResource(R.string.trip_distance, live.distanceKm)
                    } else {
                        stringResource(R.string.trip_status_idle)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                if (live.tripActive && live.topSpeedKmh > 0) {
                    Text(stringResource(R.string.trip_top_speed, live.topSpeedKmh), style = MaterialTheme.typography.bodyMedium)
                }

                // ---- Actions ----
                Spacer(Modifier.height(32.dp))
                if (!live.tripActive) {
                    Button(
                        onClick = onStartTrip,
                        enabled = permissions.location,
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    ) {
                        Text(stringResource(R.string.trip_start), style = MaterialTheme.typography.headlineSmall)
                    }
                } else if (settings.driverCanEndTrip) {
                    Button(
                        onClick = onEndTrip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (alarm) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.secondary,
                            contentColor = if (alarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
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
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onEndTrip, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text(stringResource(R.string.trip_end_test))
                        }
                    }
                }
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
private fun syncLabel(lastSyncAtMs: Long?, context: android.content.Context): String =
    if (lastSyncAtMs == null) {
        stringResource(R.string.trip_last_sync_never)
    } else {
        stringResource(R.string.trip_last_sync, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastSyncAtMs)))
    }

private fun formatElapsed(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}
