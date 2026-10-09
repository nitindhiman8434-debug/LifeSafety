package com.lifesafety.driversafety.alerts

import com.google.firebase.firestore.DocumentSnapshot

/**
 * The kinds of alert an admin can receive. The wire names match alertType in Cloud Functions (index.ts)
 * and the "type" of the push message. Phase 4 adds sos, low_battery, tracking_lost and the tamper alerts.
 */
enum class AlertType(val wireName: String) {
    OVERSPEED_STARTED("overspeed_started"),
    BACK_TO_NORMAL("back_to_normal"),
    ADMIN_ADDED("admin_added"),
    ADMIN_REMOVED("admin_removed"),
    DRIVER_UNLINKED("driver_unlinked"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): AlertType = entries.firstOrNull { it.wireName == value } ?: UNKNOWN
    }
}

/** One entry of the admin's inbox: users/{adminId}/alerts/{id}. Also what a push message carries. */
data class Alert(
    val id: String,
    val type: AlertType,
    val driverId: String,
    val driverName: String,
    val timestampUtc: Long,
    val timezoneId: String?,
    val read: Boolean,
    val speedKmh: Double? = null,
    val speedLimitKmh: Int? = null,
    val topSpeedKmh: Double? = null,
    val durationSec: Int? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val batteryPercent: Int? = null,
    val delayed: Boolean = false,
    /** Link alerts: the admin that was added, removed or left. */
    val adminName: String? = null,
    /** Link alerts: left, driver_removed, primary_removed, driver_removed_primary, primary_left. */
    val reason: String? = null,
    val byName: String? = null
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): Alert = fromFields(doc.id, doc.data ?: emptyMap()) { key -> doc.get(key) }

        /** Push messages carry every value as a String; Firestore documents carry numbers. Both land here. */
        fun fromFields(id: String, map: Map<String, Any?>, get: (String) -> Any? = { map[it] }): Alert {
            fun str(key: String): String? = get(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            fun num(key: String): Double? = when (val v = get(key)) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull()
                else -> null
            }
            fun bool(key: String): Boolean = when (val v = get(key)) {
                is Boolean -> v
                is String -> v == "true"
                else -> false
            }
            return Alert(
                id = id,
                type = AlertType.fromWire(str("alertType")),
                driverId = str("driverId").orEmpty(),
                driverName = str("driverName").orEmpty(),
                timestampUtc = num("timestampUtc")?.toLong() ?: 0L,
                timezoneId = str("timezoneId"),
                read = bool("read"),
                speedKmh = num("speedKmh"),
                speedLimitKmh = num("speedLimitKmh")?.toInt(),
                topSpeedKmh = num("topSpeedKmh"),
                durationSec = num("durationSec")?.toInt(),
                address = str("address"),
                latitude = num("latitude"),
                longitude = num("longitude"),
                batteryPercent = num("batteryPercent")?.toInt(),
                delayed = bool("delayed"),
                adminName = str("adminName"),
                reason = str("reason"),
                byName = str("byName")
            )
        }
    }
}
