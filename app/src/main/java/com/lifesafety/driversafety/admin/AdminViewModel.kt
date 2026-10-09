package com.lifesafety.driversafety.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.alerts.Alert
import com.lifesafety.driversafety.alerts.AlertsRepository
import com.lifesafety.driversafety.pairing.DriverRecord
import com.lifesafety.driversafety.pairing.FunctionErrors
import com.lifesafety.driversafety.pairing.Link
import com.lifesafety.driversafety.pairing.LinkStatus
import com.lifesafety.driversafety.pairing.PairingCode
import com.lifesafety.driversafety.pairing.PairingRepository
import com.lifesafety.driversafety.settings.DriverSettings
import com.lifesafety.driversafety.ui.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class AdminUiState(
    val loading: Boolean = true,
    val links: List<Link> = emptyList(),
    val loadError: UiText? = null
)

data class AlertsUiState(
    val loading: Boolean = true,
    val alerts: List<Alert> = emptyList(),
    val loadError: UiText? = null
)

sealed interface CodeUiState {
    data object Idle : CodeUiState
    data object Loading : CodeUiState
    data class Ready(val code: PairingCode) : CodeUiState
    data class Failed(val message: UiText) : CodeUiState
}

/**
 * Admin side: the admin's links, the live record of every approved driver, the alert inbox, code generation,
 * joining as second admin, leaving, and the Phase 3 actions (settings, request trip start, end trip now).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val uid: String,
    private val repo: PairingRepository = PairingRepository(),
    private val adminRepo: AdminRepository = AdminRepository(),
    private val alertsRepo: AlertsRepository = AlertsRepository()
) : ViewModel() {

    val state: StateFlow<AdminUiState> = repo.linksForAdmin(uid)
        .map { AdminUiState(loading = false, links = it.sortedBy { link -> link.driverName.lowercase() }) }
        .retryWhen { e, attempt ->
            // Show the error, wait a little, then listen again (network drop, or a sign-out followed by a sign-in).
            emit(AdminUiState(loading = false, loadError = UiText.Res(R.string.error_load_failed, listOf(e.message ?: ""))))
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    /**
     * The live record of every driver whose link is active, keyed by driver id. Before the driver agrees,
     * Firestore rules deny the read, so pending drivers are simply absent.
     */
    val drivers: StateFlow<Map<String, DriverRecord>> = state
        .map { s -> s.links.filter { it.status == LinkStatus.ACTIVE }.map { it.driverId }.sorted() }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap<String, DriverRecord>())
            } else {
                combine(ids.map { id -> repo.driverRecord(id).catch { emit(null) } }) { records ->
                    ids.zip(records.toList()).mapNotNull { (id, record) -> record?.let { id to it } }.toMap()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Ticks every 30 seconds so "updated 4 min ago" and the Offline status stay current. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    fun driverRecordFlow(driverId: String): Flow<DriverRecord?> = drivers.map { it[driverId] }.distinctUntilChanged()

    fun tripsFlow(driverId: String): Flow<List<TripSummary>> = adminRepo.tripsFlow(driverId).catch { emit(emptyList()) }

    fun eventsFlow(driverId: String): Flow<List<DriverEvent>> = adminRepo.eventsFlow(driverId).catch { emit(emptyList()) }

    // ---- Alerts inbox ----

    val alerts: StateFlow<AlertsUiState> = alertsRepo.alertsFlow(uid)
        .map { AlertsUiState(loading = false, alerts = it) }
        .retryWhen { e, attempt ->
            emit(AlertsUiState(loading = false, loadError = UiText.Res(R.string.error_load_failed, listOf(e.message ?: ""))))
            delay(2_000L * (attempt + 1).coerceAtMost(5))
            true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    val unreadCount: StateFlow<Int> = alerts
        .map { s -> s.alerts.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun markAlertRead(alertId: String) {
        viewModelScope.launch {
            try {
                alertsRepo.markRead(uid, listOf(alertId))
            } catch (_: Exception) {
                // Offline: the alert stays unread. Nothing to tell the user.
            }
        }
    }

    fun markAllAlertsRead() {
        val unread = alerts.value.alerts.filter { !it.read }.map { it.id }
        viewModelScope.launch {
            try {
                alertsRepo.markRead(uid, unread)
            } catch (_: Exception) {
            }
        }
    }

    // ---- Pairing code screen ----

    private val _code = MutableStateFlow<CodeUiState>(CodeUiState.Idle)
    val code: StateFlow<CodeUiState> = _code.asStateFlow()

    fun generatePrimaryCode() = generate { repo.createPairingCode() }

    fun generateCoAdminCode(driverId: String) = generate { repo.createCoAdminCode(driverId) }

    private var generateJob: Job? = null

    fun clearCode() {
        generateJob?.cancel()
        generateJob = null
        _code.value = CodeUiState.Idle
    }

    private fun generate(block: suspend () -> PairingCode) {
        // Cancel a slower earlier request so its code cannot land on a later screen.
        generateJob?.cancel()
        generateJob = viewModelScope.launch {
            _code.value = CodeUiState.Loading
            val result = try {
                CodeUiState.Ready(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CodeUiState.Failed(FunctionErrors.toUiText(e))
            }
            _code.value = result
        }
    }

    // ---- Actions with a busy flag and a message ----

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** An error from the last action (red). */
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /** A confirmation from the last action (blue), for example "Settings saved". */
    private val _info = MutableStateFlow<UiText?>(null)
    val info: StateFlow<UiText?> = _info.asStateFlow()

    private val _codeInput = MutableStateFlow("")
    val codeInput: StateFlow<String> = _codeInput.asStateFlow()

    /** Fires once each time a co-admin code is accepted, so the join screen can close itself. */
    private val _joinedEvents = MutableSharedFlow<Unit>()
    val joinedEvents: SharedFlow<Unit> = _joinedEvents.asSharedFlow()

    fun onCodeChanged(value: String) {
        _codeInput.value = value.filter { it.isDigit() }.take(6)
    }

    fun joinAsSecondAdmin() = runAction {
        repo.redeemCoAdminCode(_codeInput.value)
        _codeInput.value = ""
        _joinedEvents.emit(Unit)
    }

    fun leaveDriver(driverId: String) = runAction { repo.leaveDriver(driverId) }

    fun removeSecondaryAdmin(driverId: String) = runAction { repo.removeSecondaryAdmin(driverId) }

    fun saveSettings(driverId: String, settings: DriverSettings, phone: String) = runAction {
        adminRepo.updateSettings(driverId, settings, phone)
        _info.value = UiText.Res(R.string.detail_settings_saved)
    }

    fun requestTripStart(driverId: String) = runAction {
        val delivered = adminRepo.requestTripStart(driverId)
        _info.value = UiText.Res(if (delivered > 0) R.string.detail_request_sent else R.string.detail_request_not_delivered)
    }

    fun endTripNow(driverId: String) = runAction {
        val delivered = adminRepo.endTripNow(driverId)
        _info.value = UiText.Res(if (delivered > 0) R.string.detail_end_sent else R.string.detail_end_sent_offline)
    }

    fun clearMessage() {
        _message.value = null
        _info.value = null
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            _info.value = null
            try {
                block()
            } catch (e: Exception) {
                _message.value = FunctionErrors.toUiText(e)
            } finally {
                _busy.value = false
            }
        }
    }
}
