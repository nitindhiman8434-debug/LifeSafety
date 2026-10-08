package com.lifesafety.driversafety.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.ui.UiText
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

@Composable
fun RoleSelectScreen(
    displayName: String,
    busy: Boolean,
    message: UiText?,
    onChoose: (UserRole) -> Unit,
    onSignOut: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppTopBar(title = stringResource(R.string.role_title)) }
    ) { padding ->
        if (busy) {
            LoadingScreen(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.role_hello, displayName),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(24.dp))
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(16.dp))
            RoleCard(
                title = stringResource(R.string.role_admin_title),
                body = stringResource(R.string.role_admin_body),
                onClick = { onChoose(UserRole.ADMIN) }
            )
            Spacer(Modifier.height(16.dp))
            RoleCard(
                title = stringResource(R.string.role_driver_title),
                body = stringResource(R.string.role_driver_body),
                onClick = { onChoose(UserRole.DRIVER) }
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.role_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.action_sign_out))
            }
        }
    }
}

@Composable
private fun RoleCard(title: String, body: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
