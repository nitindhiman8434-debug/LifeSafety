package com.lifesafety.driversafety.settings

/**
 * The per-driver settings the primary admin controls. Stored in drivers/{driverId}.settings.
 * Defaults match the product spec; Phase 3 adds the admin screen that edits them.
 */
data class DriverSettings(
    val speedLimitKmh: Int = 60,
    val toleranceKmh: Int = 0,
    val adminAlertDelaySec: Int = 10,
    val autoEndMinutes: Int = 15,
    val driverCanEndTrip: Boolean = false
) {
    companion object {
        val DEFAULT = DriverSettings()

        fun fromMap(map: Map<*, *>?): DriverSettings {
            if (map == null) return DEFAULT
            fun int(key: String, fallback: Int) = (map[key] as? Number)?.toInt() ?: fallback
            return DriverSettings(
                speedLimitKmh = int("speedLimitKmh", DEFAULT.speedLimitKmh).coerceIn(10, 200),
                toleranceKmh = int("toleranceKmh", DEFAULT.toleranceKmh).coerceIn(0, 30),
                adminAlertDelaySec = int("adminAlertDelaySec", DEFAULT.adminAlertDelaySec).coerceIn(0, 120),
                autoEndMinutes = int("autoEndMinutes", DEFAULT.autoEndMinutes).coerceIn(1, 120),
                driverCanEndTrip = map["driverCanEndTrip"] as? Boolean ?: DEFAULT.driverCanEndTrip
            )
        }
    }
}
