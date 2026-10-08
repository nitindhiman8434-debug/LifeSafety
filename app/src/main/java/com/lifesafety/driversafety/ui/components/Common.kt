package com.lifesafety.driversafety.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.lifesafety.driversafety.ui.UiText
import com.lifesafety.driversafety.ui.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        },
        actions = actions
    )
}

/** Overflow menu with Sign out and, while the user is not linked to anyone, Change role. */
@Composable
fun AccountMenu(
    canChangeRole: Boolean,
    onSignOut: () -> Unit,
    onChangeRole: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (canChangeRole) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_change_role)) },
                onClick = { open = false; onChangeRole() }
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_sign_out)) },
            onClick = { open = false; onSignOut() }
        )
    }
}

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Shows an error or info message in a tinted card. Nothing is drawn when message is null. */
@Composable
fun MessageCard(message: UiText?, isError: Boolean = true, modifier: Modifier = Modifier) {
    if (message == null) return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Text(
            text = message.asString(),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** A 6-digit code field with a submit button. Used by the driver (pairing code) and by a second admin (co-admin code). */
@Composable
fun CodeEntryCard(
    title: String,
    body: String,
    code: String,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    busy: Boolean,
    message: UiText?,
    buttonLabel: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChanged,
                label = { Text(stringResource(R.string.code_label)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSubmit,
                enabled = !busy && code.length == 6,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(buttonLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
