package com.lifesafety.driversafety.trip

/**
 * Turns raw GPS fixes into a steady speed reading.
 *
 * Only fixes with a speed and with accuracy better than [maxAccuracyM] are accepted. The result is the
 * average of the last [windowSize] accepted speeds. If nothing was accepted for [staleAfterMs] (GPS lost
 * in a tunnel, for example) the window starts fresh so an old reading cannot linger.
 *
 * Pure Kotlin, no Android classes, so it is unit tested without a device.
 */
class SpeedSmoother(
    private val windowSize: Int = 3,
    private val maxAccuracyM: Float = 25f,
    private val staleAfterMs: Long = 10_000L
) {
    private val speeds = ArrayDeque<Double>()
    private var lastAcceptedAtMs: Long? = null

    /** Returns the smoothed speed in km/h, or null when this fix was rejected. */
    fun accept(speedKmh: Double, accuracyM: Float, hasSpeed: Boolean, nowMs: Long): Double? {
        if (!hasSpeed || accuracyM.isNaN() || accuracyM > maxAccuracyM || speedKmh < 0.0) return null
        val last = lastAcceptedAtMs
        if (last != null && nowMs - last > staleAfterMs) speeds.clear()
        lastAcceptedAtMs = nowMs
        speeds.addLast(speedKmh)
        while (speeds.size > windowSize) speeds.removeFirst()
        return speeds.average()
    }

    fun reset() {
        speeds.clear()
        lastAcceptedAtMs = null
    }
}
