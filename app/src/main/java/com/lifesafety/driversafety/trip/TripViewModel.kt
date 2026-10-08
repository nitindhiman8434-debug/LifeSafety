package com.lifesafety.driversafety.trip

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifesafety.driversafety.settings.DriverSettings
import com.lifesafety.driversafety.trip.sync.SyncWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Driver side of the trip screen: live state from the service, the admin's settings, start/end actions. */
class TripViewModel(
    private val appContext: Context,
    uid: String,
    private val repo: TripRepository = TripRepository(appContext, uid)
) : ViewModel() {

    val live: StateFlow<TripLiveState> = TripStateHolder.state

    val settings: StateFlow<DriverSettings> = repo.settingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriverSettings.DEFAULT)

    val simulationAvailable: Boolean = DriveSimulator.AVAILABLE
    val simulating: StateFlow<Boolean> = DriveSimulator.enabled

    init {
        viewModelScope.launch {
            // A trip left open by a crash is closed; anything still waiting for upload is handed to WorkManager.
            if (!TripStateHolder.state.value.tripActive) repo.closeStaleTrips(System.currentTimeMillis())
            if (repo.pendingCount() > 0) SyncWorker.enqueue(appContext)
        }
    }

    fun startTrip() = TripService.start(appContext)

    fun endTrip() = TripService.end(appContext)

    fun setSimulation(on: Boolean) = DriveSimulator.setEnabled(on)

    fun showMonitoringNotification(adminNames: String) = Notifications.showMonitoring(appContext, adminNames)

    fun hideMonitoringNotification() = Notifications.cancelMonitoring(appContext)
}
