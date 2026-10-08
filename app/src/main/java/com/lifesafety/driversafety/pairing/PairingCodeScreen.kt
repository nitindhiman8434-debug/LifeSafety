package com.lifesafety.driversafety.pairing

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.admin.AdminViewModel
import com.lifesafety.driversafety.admin.CodeUiState
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.MessageCard
import kotlinx.coroutines.delay

/**
 * Admin screen that shows a fresh 6-digit code with a 10-minute countdown and a Share button.
 * driverId == null: code for a new driver (primary pairing). Otherwise: co-admin code for that driver.
 */
@Composable
fun PairingCodeScreen(
    viewModel: AdminViewModel,
    driverId: String?,
    onBack: () -> Unit
) {
    val codeState by viewModel.code.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val forSecondAdmin = driverId != null

    // Generate once per visit. The ViewModel keeps the code across rotation; the dashboard clears it on entry.
    LaunchedEffect(driverId) {
        if (viewModel.code.value is CodeUiState.Idle) {
            if (driverId == null) viewModel.generatePrimaryCode() else viewModel.generateCoAdminCode(driverId)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(if (forSecondAdmin) R.string.code_title_secondary else R.string.code_title_primary),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(if (forSecondAdmin) R.string.code_body_secondary else R.string.code_body_primary),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))
            when (val s = codeState) {
                CodeUiState.Idle, CodeUiState.Loading -> CircularProgressIndicator()
                is CodeUiState.Failed -> MessageCard(s.message)
                is CodeUiState.Ready -> {
                    val remainingMillis = rememberCountdown(s.code.expiresAtMillis)
                    val expired = remainingMillis <= 0L
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = s.code.code.chunked(3).joinToString(" "),
                                style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 6.sp),
                                color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (expired) stringResource(R.string.code_expired)
                                else stringResource(R.string.code_valid_for, formatMinutesSeconds(remainingMillis)),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val text = context.getString(
                                if (forSecondAdmin) R.string.code_share_text_secondary else R.string.code_share_text_primary,
                                s.code.code
                            )
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                        enabled = !expired,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.action_share), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (codeState is CodeUiState.Ready || codeState is CodeUiState.Failed) {
                Spacer(Modifier.height(if (codeState is CodeUiState.Failed) 24.dp else 12.dp))
                OutlinedButton(
                    onClick = { if (driverId == null) viewModel.generatePrimaryCode() else viewModel.generateCoAdminCode(driverId) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.action_new_code), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Ticks once a second until the code expires. Returns the remaining milliseconds, never negative. */
@Composable
private fun rememberCountdown(expiresAtMillis: Long): Long {
    var now by remember(expiresAtMillis) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= expiresAtMillis) break
            delay(1_000)
        }
    }
    return (expiresAtMillis - now).coerceAtLeast(0L)
}

private fun formatMinutesSeconds(millis: Long): String {
    val totalSeconds = millis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
