package com.lifesafety.driversafety.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.admin.AdminViewModel
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.CodeEntryCard

/** Admin screen: enter the co-admin code a primary admin shared, to become a driver's second admin. */
@Composable
fun EnterCoAdminCodeScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit,
    onJoined: () -> Unit
) {
    val code by viewModel.codeInput.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val joined by viewModel.joined.collectAsStateWithLifecycle()

    LaunchedEffect(joined) {
        if (joined) {
            viewModel.consumeJoined()
            onJoined()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppTopBar(title = stringResource(R.string.join_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            CodeEntryCard(
                title = stringResource(R.string.join_title),
                body = stringResource(R.string.join_body),
                code = code,
                onCodeChanged = viewModel::onCodeChanged,
                onSubmit = viewModel::joinAsSecondAdmin,
                busy = busy,
                message = message,
                buttonLabel = stringResource(R.string.action_join)
            )
        }
    }
}
