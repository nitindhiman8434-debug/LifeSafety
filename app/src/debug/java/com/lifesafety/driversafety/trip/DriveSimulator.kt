package com.lifesafety.driversafety.trip

import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.cos

/**
 * DEBUG BUILDS ONLY (this file lives in src/debug; src/release has a stub with AVAILABLE = false).
 * Feeds fake GPS fixes so the overspeed alarm can be tested at home. A two-minute loop:
 * speed up to 50, cruise, 75 for 25 seconds (over a 60 limit: alarm after 3 s, admins after 13 s),
 * back to 40, then stopped for 30 seconds.
 */
object DriveSimulator {
    const val AVAILABLE = true

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        _enabled.value = on
    }

    fun locations(): Flow<Location> = flow {
        var second = 0
        var lat = 28.6139
        var lng = 77.2090
        while (true) {
            val kmh = speedAt(second % 120)
            val metres = kmh / 3.6
            lat += metres / 111_320.0
            lng += metres / (111_320.0 * cos(Math.toRadians(lat)))
            val location = Location("simulated").apply {
                latitude = lat
                longitude = lng
                speed = (kmh / 3.6).toFloat()
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            emit(location)
            delay(1_000L)
            second++
        }
    }

    private fun speedAt(t: Int): Double = when {
        t < 10 -> t * 5.0
        t < 30 -> 50.0
        t < 35 -> 50.0 + (t - 30) * 5.0
        t < 60 -> 75.0
        t < 65 -> 75.0 - (t - 60) * 7.0
        t < 85 -> 40.0
        t < 90 -> 40.0 - (t - 85) * 8.0
        else -> 0.0
    }
}
