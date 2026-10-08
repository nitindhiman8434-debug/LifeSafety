package com.lifesafety.driversafety.trip

/**
 * Decides when a trip ends by itself: the vehicle has been stationary for the admin's auto-end time,
 * or no usable GPS fix arrived for that long (parked in a basement, phone in a bag).
 * Pure Kotlin so it is unit tested without a device.
 */
class AutoEndDetector(private val movingSpeedKmh: Double = 3.0) {
    private var stationarySinceMs: Long? = null
    private var lastFixMs: Long? = null

    fun start(nowMs: Long) {
        lastFixMs = nowMs
        stationarySinceMs = null
    }

    /** Feed every accepted (accurate) fix. */
    fun onFix(speedKmh: Double, nowMs: Long) {
        lastFixMs = nowMs
        if (speedKmh >= movingSpeedKmh) {
            stationarySinceMs = null
        } else if (stationarySinceMs == null) {
            stationarySinceMs = nowMs
        }
    }

    /** Call every few seconds, with or without fixes. */
    fun shouldEnd(nowMs: Long, autoEndMinutes: Int): Boolean {
        val limitMs = autoEndMinutes * 60_000L
        val stationary = stationarySinceMs
        if (stationary != null && nowMs - stationary >= limitMs) return true
        val lastFix = lastFixMs
        return lastFix != null && nowMs - lastFix >= limitMs
    }
}
