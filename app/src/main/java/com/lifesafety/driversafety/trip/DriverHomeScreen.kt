package com.lifesafety.driversafety.trip

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
import com.lifesafety.driversafety.pairing.ConsentScreen
import com.lifesafety.driversafety.pairing.DriverLinkViewModel
import com.lifesafety.driversafety.ui.components.AccountMenu
import com.lifesafety.driversafety.ui.components.AppTopBar
import com.lifesafety.driversafety.ui.components.CodeEntryCard
import com.lifesafety.driversafety.ui.components.LoadingScreen
import com.lifesafety.driversafety.ui.components.MessageCard

/**
 * The driver's one main screen. Unlinked: enter the admin's code. Consent pending: the consent screen.
 * Linked: the trip screen (speed, limit, Start Trip). "Who can see my data" lives in the menu so there is
 * nothing to tap while driving.
 */
@Composable
fun DriverHomeScreen(
    linkViewModel: DriverLinkViewModel,
    tripViewModel: TripViewModel,
    onOpenAdmins: () -> Unit,
    onSignOut: () -> Unit,
    onChangeRole: () -> Unit
) {
    val state by linkViewModel.state.collectAsStateWithLifecycle()
    val busy by linkViewModel.busy.collectAsStateWithLifecycle()
    val message by linkViewModel.message.collectAsStateWithLifecycle()
    val code by linkViewModel.codeInput.collectAsStateWithLifecycle()
    val live by tripViewModel.live.collectAsStateWithLifecycle()
    val settings by tripViewModel.settings.collectAsStateWithLifecycle()
    val simulating by tripViewModel.simulating.collectAsStateWithLifecycle()
    val (permissions, refreshPermissions) = rememberPermissionStatus()

    val adminNames = listOfNotNull(state.primary?.adminName, state.secondary?.adminName).joinToString(", ")

    // "Monitoring active" notification while linked and idle; the trip service shows its own during a trip.
    LaunchedEffect(state.isLinked, adminNames, live.tripActive, permissions.notifications) {
        if (state.isLinked && !live.tripActive) {
            tripViewModel.showMonitoringNotification(adminNames)
        } else if (!state.isLinked) {
            tripViewModel.hideMonitoringNotification()
        }
    }

    // A link waiting for consent takes over the whole screen until the driver answers.
    val pending = state.pendingLink
    if (pending != null) {
        ConsentScreen(
            link = pending,
            busy = busy,
            message = message,
            onAgree = { linkViewModel.respondToConsent(pending.adminId, true) },
            onDecline = { linkViewModel.respondToConsent(pending.adminId, false) },
            onSignOut = onSignOut
        )
        return
    }

    val whoCanSee = stringResource(R.string.driver_who_can_see)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.driver_title),
                actions = {
                    AccountMenu(
                        canChangeRole = !state.loading && state.links.isEmpty(),
                        onSignOut = onSignOut,
                        onChangeRole = onChangeRole,
                        extraItems = if (state.isLinked) listOf(whoCanSee to onOpenAdmins) else emptyList()
                    )
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> LoadingScreen(Modifier.padding(padding))

            state.loadError != null -> Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                MessageCard(state.loadError)
            }

            state.isLinked -> TripScreen(
                live = live,
                settings = settings,
                adminNames = adminNames,
                permissions = permissions,
                onPermissionsChanged = refreshPermissions,
                simulationAvailable = tripViewModel.simulationAvailable,
                simulating = simulating,
                onToggleSimulation = tripViewModel::setSimulation,
                onStartTrip = tripViewModel::startTrip,
                onEndTrip = tripViewModel::endTrip,
                modifier = Modifier.padding(padding)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                CodeEntryCard(
                    title = stringResource(R.string.driver_unlinked_title),
                    body = stringResource(R.string.driver_unlinked_body),
                    code = code,
                    onCodeChanged = linkViewModel::onCodeChanged,
                    onSubmit = linkViewModel::joinWithCode,
                    busy = busy,
                    message = message,
                    buttonLabel = stringResource(R.string.driver_join_button)
                )
            }
        }
    }
}
