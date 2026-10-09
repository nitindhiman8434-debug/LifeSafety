package com.lifesafety.driversafety.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.settings.DriverSettings

/**
 * The driver's settings. The primary admin edits and saves them (the updateDriverSettings Cloud Function
 * checks the same ranges); the second admin sees them read-only.
 */
@Composable
fun DriverSettingsCard(
    settings: DriverSettings,
    phone: String?,
    editable: Boolean,
    busy: Boolean,
    onSave: (DriverSettings, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (editable) R.string.settings_editable_note else R.string.settings_readonly_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (editable) {
                SettingsForm(settings = settings, phone = phone, busy = busy, onSave = onSave)
            } else {
                SettingsSummary(settings = settings, phone = phone)
            }
        }
    }
}

@Composable
private fun SettingsSummary(settings: DriverSettings, phone: String?) {
    SettingLine(stringResource(R.string.settings_speed_limit), stringResource(R.string.settings_kmh_value, settings.speedLimitKmh))
    SettingLine(stringResource(R.string.settings_tolerance), stringResource(R.string.settings_kmh_value, settings.toleranceKmh))
    SettingLine(stringResource(R.string.settings_alert_delay), stringResource(R.string.settings_seconds_value, settings.adminAlertDelaySec))
    SettingLine(stringResource(R.string.settings_auto_end), stringResource(R.string.settings_minutes_value, settings.autoEndMinutes))
    SettingLine(
        stringResource(R.string.settings_driver_can_end),
        stringResource(if (settings.driverCanEndTrip) R.string.settings_yes else R.string.settings_no)
    )
    SettingLine(stringResource(R.string.settings_phone), phone ?: stringResource(R.string.settings_phone_none))
}

@Composable
private fun SettingLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsForm(settings: DriverSettings, phone: String?, busy: Boolean, onSave: (DriverSettings, String) -> Unit) {
    // The form restarts from the saved values whenever they change on the server.
    var limit by remember(settings) { mutableStateOf(settings.speedLimitKmh.toString()) }
    var tolerance by remember(settings) { mutableStateOf(settings.toleranceKmh.toString()) }
    var delay by remember(settings) { mutableStateOf(settings.adminAlertDelaySec.toString()) }
    var autoEnd by remember(settings) { mutableStateOf(settings.autoEndMinutes.toString()) }
    var canEnd by remember(settings) { mutableStateOf(settings.driverCanEndTrip) }
    var phoneText by remember(phone) { mutableStateOf(phone.orEmpty()) }

    val limitValue = limit.toIntOrNull()?.takeIf { it in 10..200 }
    val toleranceValue = tolerance.toIntOrNull()?.takeIf { it in 0..30 }
    val delayValue = delay.toIntOrNull()?.takeIf { it in 0..120 }
    val autoEndValue = autoEnd.toIntOrNull()?.takeIf { it in 1..120 }
    val phoneOk = phoneText.isBlank() || Regex("^\\+?[0-9 ()-]{6,20}$").matches(phoneText.trim())

    NumberField(limit, { limit = it }, stringResource(R.string.settings_speed_limit), stringResource(R.string.settings_range_kmh, 10, 200), limitValue == null, busy)
    NumberField(tolerance, { tolerance = it }, stringResource(R.string.settings_tolerance), stringResource(R.string.settings_tolerance_help, 0, 30), toleranceValue == null, busy)
    NumberField(delay, { delay = it }, stringResource(R.string.settings_alert_delay), stringResource(R.string.settings_alert_delay_help, 0, 120), delayValue == null, busy)
    NumberField(autoEnd, { autoEnd = it }, stringResource(R.string.settings_auto_end), stringResource(R.string.settings_auto_end_help, 1, 120), autoEndValue == null, busy)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_driver_can_end), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.settings_driver_can_end_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = canEnd, onCheckedChange = { canEnd = it }, enabled = !busy)
    }
    OutlinedTextField(
        value = phoneText,
        onValueChange = { phoneText = it },
        label = { Text(stringResource(R.string.settings_phone)) },
        supportingText = { Text(stringResource(R.string.settings_phone_help)) },
        isError = !phoneOk,
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    val allValid = limitValue != null && toleranceValue != null && delayValue != null && autoEndValue != null && phoneOk
    val changed = allValid && (
        limitValue != settings.speedLimitKmh || toleranceValue != settings.toleranceKmh ||
            delayValue != settings.adminAlertDelaySec || autoEndValue != settings.autoEndMinutes ||
            canEnd != settings.driverCanEndTrip || phoneText.trim() != phone.orEmpty()
        )
    Button(
        onClick = {
            onSave(
                DriverSettings(
                    speedLimitKmh = limitValue ?: settings.speedLimitKmh,
                    toleranceKmh = toleranceValue ?: settings.toleranceKmh,
                    adminAlertDelaySec = delayValue ?: settings.adminAlertDelaySec,
                    autoEndMinutes = autoEndValue ?: settings.autoEndMinutes,
                    driverCanEndTrip = canEnd
                ),
                phoneText.trim()
            )
        },
        enabled = changed && !busy,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(stringResource(R.string.settings_save), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, help: String, isError: Boolean, busy: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(3)) },
        label = { Text(label) },
        supportingText = { Text(help) },
        isError = isError,
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
