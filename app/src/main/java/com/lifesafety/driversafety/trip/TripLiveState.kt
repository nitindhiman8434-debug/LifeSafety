package com.lifesafety.driversafety.trip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the driver screen shows. Written by [TripService], read by the view model. */
data class TripLiveState(
    val tripActive: Boolean = false,
    val tripId: String? = null,
    val startedAtMs: Long? = null,
    val elapsedSec: Int = 0,
    /** Smoothed speed in km/h, null until the first good fix. */
    val speedKmh: Double? = null,
    val limitKmh: Int = 60,
    val driverCanEndTrip: Boolean = false,
    val distanceKm: Double = 0.0,
    val topSpeedKmh: Double = 0.0,
    /** The alarm is sounding. */
    val overspeed: Boolean = false,
    val adminsAlerted: Boolean = false,
    val gpsOk: Boolean = false,
    val locationPermissionMissing: Boolean = false,
    val lastFixAtMs: Long? = null,
    val lastSyncAtMs: Long? = null,
    val pendingUploads: Int = 0,
    val simulating: Boolean = false
)

/** Process-wide holder so the service and the UI share one state without binding to the service. */
object TripStateHolder {
    private val _state = MutableStateFlow(TripLiveState())
    val state: StateFlow<TripLiveState> = _state.asStateFlow()

    fun update(transform: (TripLiveState) -> TripLiveState) = _state.update(transform)
}
