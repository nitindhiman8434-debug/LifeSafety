package com.lifesafety.driversafety.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.admin.AdminDashboardScreen
import com.lifesafety.driversafety.admin.AdminDriverScreen
import com.lifesafety.driversafety.admin.AdminViewModel
import com.lifesafety.driversafety.auth.AuthViewModel
import com.lifesafety.driversafety.auth.RoleSelectScreen
import com.lifesafety.driversafety.auth.SessionState
import com.lifesafety.driversafety.auth.SignInScreen
import com.lifesafety.driversafety.auth.UserProfile
import com.lifesafety.driversafety.auth.UserRole
import com.lifesafety.driversafety.pairing.DriverLinkViewModel
import com.lifesafety.driversafety.pairing.EnterCoAdminCodeScreen
import com.lifesafety.driversafety.pairing.PairingCodeScreen
import com.lifesafety.driversafety.pairing.WhoCanSeeMyDataScreen
import com.lifesafety.driversafety.trip.DriverHomeScreen
import com.lifesafety.driversafety.ui.components.ConfirmDialog
import com.lifesafety.driversafety.ui.components.LoadingScreen

/**
 * The session gate. Signed out -> sign-in screen. No role yet -> role selection.
 * Then the driver or the admin part of the app, each with its own navigation.
 */
@Composable
fun AppRoot(authViewModel: AuthViewModel = viewModel()) {
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val busy by authViewModel.busy.collectAsStateWithLifecycle()
    val message by authViewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val s = session) {
        SessionState.Loading -> LoadingScreen()
        SessionState.SignedOut -> SignInScreen(
            busy = busy,
            message = message,
            onSignIn = authViewModel::signIn
        )
        is SessionState.NeedsRole -> RoleSelectScreen(
            displayName = s.displayName,
            busy = busy,
            message = message,
            onChoose = authViewModel::chooseRole,
            onSignOut = { authViewModel.signOut(context) }
        )
        is SessionState.Error -> ErrorScreen(
            message = s.message,
            onSignOut = { authViewModel.signOut(context) }
        )
        is SessionState.Ready -> SignedInApp(profile = s.profile, authViewModel = authViewModel)
    }
}

@Composable
private fun SignedInApp(profile: UserProfile, authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val message by authViewModel.message.collectAsStateWithLifecycle()
    var confirmRoleChange by remember { mutableStateOf(false) }
    val otherRole = if (profile.role == UserRole.ADMIN) UserRole.DRIVER else UserRole.ADMIN

    if (confirmRoleChange) {
        ConfirmDialog(
            title = stringResource(R.string.change_role_title),
            text = stringResource(
                R.string.change_role_text,
                stringResource(if (otherRole == UserRole.ADMIN) R.string.role_admin_title else R.string.role_driver_title)
            ),
            confirmLabel = stringResource(R.string.action_change_role),
            onConfirm = { authViewModel.chooseRole(otherRole) },
            onDismiss = { confirmRoleChange = false }
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = authViewModel::clearMessage,
            confirmButton = { TextButton(onClick = authViewModel::clearMessage) { Text(stringResource(R.string.action_ok)) } },
            text = { Text(text.asString()) }
        )
    }

    val onSignOut: () -> Unit = { authViewModel.signOut(context) }
    val onChangeRole: () -> Unit = { confirmRoleChange = true }
    when (profile.role) {
        UserRole.DRIVER -> DriverNavHost(profile, onSignOut, onChangeRole)
        UserRole.ADMIN -> AdminNavHost(profile, onSignOut, onChangeRole)
    }
}

@Composable
private fun DriverNavHost(profile: UserProfile, onSignOut: () -> Unit, onChangeRole: () -> Unit) {
    val navController = rememberNavController()
    // Keyed by uid so a different driver signing in on the same phone gets a fresh view model.
    val viewModel: DriverLinkViewModel = viewModel(key = "driver-${profile.uid}") { DriverLinkViewModel(profile.uid) }
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            DriverHomeScreen(
                viewModel = viewModel,
                onOpenAdmins = { viewModel.clearMessage(); navController.navigate("admins") },
                onSignOut = onSignOut,
                onChangeRole = onChangeRole
            )
        }
        composable("admins") {
            WhoCanSeeMyDataScreen(viewModel = viewModel, onBack = { viewModel.clearMessage(); navController.popBackStack() })
        }
    }
}

@Composable
private fun AdminNavHost(profile: UserProfile, onSignOut: () -> Unit, onChangeRole: () -> Unit) {
    val navController = rememberNavController()
    val viewModel: AdminViewModel = viewModel(key = "admin-${profile.uid}") { AdminViewModel(profile.uid) }
    NavHost(navController = navController, startDestination = "dashboard") {
        composable("dashboard") {
            AdminDashboardScreen(
                viewModel = viewModel,
                onAddDriver = { viewModel.clearCode(); navController.navigate("code") },
                onJoinAsSecondAdmin = { viewModel.clearMessage(); navController.navigate("join") },
                onOpenDriver = { driverId -> viewModel.clearMessage(); navController.navigate("driver/$driverId") },
                onSignOut = onSignOut,
                onChangeRole = onChangeRole
            )
        }
        composable("code") {
            PairingCodeScreen(viewModel = viewModel, driverId = null, onBack = { navController.popBackStack() })
        }
        composable("coadmin/{driverId}") { entry ->
            val driverId = entry.arguments?.getString("driverId") ?: return@composable
            PairingCodeScreen(viewModel = viewModel, driverId = driverId, onBack = { navController.popBackStack() })
        }
        composable("join") {
            EnterCoAdminCodeScreen(
                viewModel = viewModel,
                onBack = { viewModel.clearMessage(); navController.popBackStack() },
                onJoined = { navController.popBackStack() }
            )
        }
        composable("driver/{driverId}") { entry ->
            val driverId = entry.arguments?.getString("driverId") ?: return@composable
            AdminDriverScreen(
                viewModel = viewModel,
                driverId = driverId,
                onBack = { viewModel.clearMessage(); navController.popBackStack() },
                onAddSecondAdmin = { viewModel.clearCode(); navController.navigate("coadmin/$driverId") }
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: UiText, onSignOut: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(message.asString())
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSignOut) { Text(stringResource(R.string.action_sign_out)) }
        }
    }
}
