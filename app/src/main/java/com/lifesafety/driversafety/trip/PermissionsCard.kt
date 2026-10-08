package com.lifesafety.driversafety.trip

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lifesafety.driversafety.R

data class PermissionStatus(val location: Boolean, val notifications: Boolean) {
    val allGranted: Boolean get() = location && notifications
}

fun checkPermissions(context: Context): PermissionStatus {
    val location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return PermissionStatus(location, notifications)
}

/** Re-checks the permissions every time the screen comes back to the front (for example from Settings). */
@Composable
fun rememberPermissionStatus(): Pair<PermissionStatus, () -> Unit> {
    val context = LocalContext.current
    var status by remember { mutableStateOf(checkPermissions(context)) }
    val refresh = { status = checkPermissions(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    return status to refresh
}

/**
 * Google Play rule: explain a permission in the app before asking for it. Location first, then notifications.
 * After a refusal, offers the system settings page, because Android stops showing the dialog after two denials.
 */
@Composable
fun PermissionsCard(status: PermissionStatus, onChanged: () -> Unit) {
    val context = LocalContext.current
    var deniedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { granted -> !granted }) deniedOnce = true
        onChanged()
    }
    val askingForLocation = !status.location
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(if (askingForLocation) R.string.perm_location_title else R.string.perm_notifications_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(if (askingForLocation) R.string.perm_location_body else R.string.perm_notifications_body),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (askingForLocation) {
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(stringResource(R.string.perm_allow), style = MaterialTheme.typography.titleMedium)
            }
            if (deniedOnce) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.perm_denied_note), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(stringResource(R.string.perm_open_settings))
                }
            }
        }
    }
}
