package com.lifesafety.driversafety.trip

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** RELEASE BUILDS: the simulator does not exist. The switch never shows and no fake fix is ever produced. */
object DriveSimulator {
    const val AVAILABLE = false

    val enabled: StateFlow<Boolean> = MutableStateFlow(false)

    fun setEnabled(on: Boolean) = Unit

    fun locations(): Flow<Location> = emptyFlow()
}
