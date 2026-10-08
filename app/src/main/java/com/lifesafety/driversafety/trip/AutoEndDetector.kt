package com.lifesafety.driversafety.trip

/**
 * Decides when a trip ends by itself: the vehicle has been stationary for the admin's auto-end time,
 * or nothing showed movement for that long (parked in a basement, phone in a bag).
 *
 * Accepted (accurate) fixes report speed through [onFix]. Every raw fix, accurate or not, goes through
 * [onRawFix]: moving well beyond the fix's own error radius proves the vehicle is still driving even when
 * GPS accuracy is poor for a long stretch, while the jittery fixes of a parked phone never do.
 * Pure Kotlin so it is unit tested without a device.
 */
class AutoEndDetector(private val movingSpeedKmh: Double = 3.0) {
    private var stationarySinceMs: Long? = null
    private var lastMovementMs: Long? = null
    private var anchorLat = 0.0
    private var anchorLng = 0.0
    private var hasAnchor = false

    fun start(nowMs: Long) {
        lastMovementMs = nowMs
        stationarySinceMs = null
        hasAnchor = false
    }

    /** Feed every accepted (accurate) fix with its smoothed speed. */
    fun onFix(speedKmh: Double, nowMs: Long) {
        lastMovementMs = nowMs
        if (speedKmh >= movingSpeedKmh) {
            stationarySinceMs = null
        } else if (stationarySinceMs == null) {
            stationarySinceMs = nowMs
        }
    }

    /** Feed every raw fix. Only clear movement (beyond three times the error radius, at least 100 m) counts. */
    fun onRawFix(latitude: Double, longitude: Double, accuracyM: Float, nowMs: Long) {
        if (accuracyM.isNaN() || accuracyM > 150f) return // cell-tower fixes carry no information
        if (!hasAnchor) {
            anchorLat = latitude
            anchorLng = longitude
            hasAnchor = true
            return
        }
        val moved = Geo.distanceMeters(anchorLat, anchorLng, latitude, longitude)
        if (moved > maxOf(3.0 * accuracyM, 100.0)) {
            anchorLat = latitude
            anchorLng = longitude
            lastMovementMs = nowMs
            stationarySinceMs = null
        }
    }

    /** Call every few seconds, with or without fixes. */
    fun shouldEnd(nowMs: Long, autoEndMinutes: Int): Boolean {
        val limitMs = autoEndMinutes * 60_000L
        val stationary = stationarySinceMs
        if (stationary != null && nowMs - stationary >= limitMs) return true
        val lastMovement = lastMovementMs
        return lastMovement != null && nowMs - lastMovement >= limitMs
    }
}
