package com.lifesafety.driversafety.pairing

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.ui.UiText
import com.lifesafety.driversafety.ui.components.AccountMenu
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.ScreenPadding
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

/**
 * Google Play requirement: before any admin sees data, the driver reads who the admin is,
 * what they will see and when, and taps Agree. Shown for the primary admin and again for a second admin.
 */
@Composable
fun ConsentScreen(
    link: Link,
    busy: Boolean,
    message: UiText?,
    onAgree: () -> Unit,
    onDecline: () -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.consent_top_title),
                actions = { AccountMenu(canChangeRole = false, onSignOut = onSignOut, onChangeRole = {}) }
            )
        }
    ) { padding ->
        if (busy) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ScreenPadding, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.consent_title, link.adminName),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (link.role == LinkRole.PRIMARY) {
                    stringResource(R.string.consent_role_primary, link.adminName)
                } else {
                    stringResource(R.string.consent_role_secondary, link.adminName)
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.consent_sees_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("• " + stringResource(R.string.consent_sees_1), style = MaterialTheme.typography.bodyLarge)
                    Text("• " + stringResource(R.string.consent_sees_2), style = MaterialTheme.typography.bodyLarge)
                    Text("• " + stringResource(R.string.consent_sees_3), style = MaterialTheme.typography.bodyLarge)
                    Text("• " + stringResource(R.string.consent_sees_4), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.consent_when_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.consent_when_body), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.consent_not_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.consent_not_body), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.consent_remove_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(16.dp))
            Button(onClick = onAgree, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.action_agree), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.action_decline), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
