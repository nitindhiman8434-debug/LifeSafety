package com.lifesafety.driversafety.auth

import android.app.Activity
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.ui.UiText
import com.lifesafety.driversafety.ui.components.MessageCard

@Composable
fun SignInScreen(
    busy: Boolean,
    message: UiText?,
    onSignIn: (Activity) -> Unit
) {
    val activity = LocalActivity.current
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sign_in_subtitle),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.sign_in_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            MessageCard(message)
            if (message != null) Spacer(Modifier.height(16.dp))
            if (busy) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { activity?.let(onSignIn) },
                    enabled = activity != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(stringResource(R.string.action_continue_google), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.sign_in_policy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
