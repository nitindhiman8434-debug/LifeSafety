package com.lifesafety.driversafety.admin

import com.google.firebase.firestore.DocumentSnapshot

/**
 * The "live" block the driver's phone writes on drivers/{driverId} about every 10 seconds during a trip
 * and once when the trip ends. The admin dashboard and driver detail are built from it.
 */
data class DriverLive(
    val status: String,            // "on_trip" or "idle"
    val tripId: String?,
    val tripStartedAtUtc: Long?,
    val speedKmh: Double,
    val speedLimitKmh: Int,
    val latitude: Double?,
    val longitude: Double?,
    val lastFixAtUtc: Long?,
    val lastSyncAtUtc: Long?,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val networkType: String?,
    val overspeedNow: Boolean,
    val adminsAlerted: Boolean,
    val distanceKm: Double,
    val topSpeedKmh: Double,
    val simulated: Boolean
) {
    val hasPosition: Boolean get() = latitude != null && longitude != null

    companion object {
        fun fromMap(map: Map<*, *>?): DriverLive? {
            if (map == null) return null
            fun num(key: String) = map[key] as? Number
            return DriverLive(
                status = map["status"] as? String ?: "idle",
                tripId = map["tripId"] as? String,
                tripStartedAtUtc = num("tripStartedAtUtc")?.toLong(),
                speedKmh = num("speedKmh")?.toDouble() ?: 0.0,
                speedLimitKmh = num("speedLimitKmh")?.toInt() ?: 0,
                latitude = num("latitude")?.toDouble(),
                longitude = num("longitude")?.toDouble(),
                lastFixAtUtc = num("lastFixAtUtc")?.toLong(),
                lastSyncAtUtc = num("lastSyncAtUtc")?.toLong(),
                batteryPercent = num("batteryPercent")?.toInt() ?: -1,
                isCharging = map["isCharging"] as? Boolean ?: false,
                networkType = map["networkType"] as? String,
                overspeedNow = map["overspeedNow"] as? Boolean ?: false,
                adminsAlerted = map["adminsAlerted"] as? Boolean ?: false,
                distanceKm = num("distanceKm")?.toDouble() ?: 0.0,
                topSpeedKmh = num("topSpeedKmh")?.toDouble() ?: 0.0,
                simulated = map["simulated"] as? Boolean ?: false
            )
        }
    }
}

/** What the dashboard says about a driver right now. Phase 4 adds "Tracking lost" with its reasons. */
enum class DriverStatus { ON_TRIP, IDLE, OFFLINE, NO_DATA }

/** No data for this long during a trip counts as offline. Phase 4 turns this into the server-side "Lost" check. */
const val OFFLINE_AFTER_MS = 3 * 60 * 1000L

fun DriverLive?.statusAt(nowUtc: Long): DriverStatus = when {
    this == null -> DriverStatus.NO_DATA
    status != "on_trip" -> DriverStatus.IDLE
    lastSyncAtUtc != null && nowUtc - lastSyncAtUtc > OFFLINE_AFTER_MS -> DriverStatus.OFFLINE
    else -> DriverStatus.ON_TRIP
}

/** One row of the trip list: drivers/{driverId}/trips/{tripId}. */
data class TripSummary(
    val id: String,
    val status: String,
    val startedAtUtc: Long,
    val endedAtUtc: Long?,
    val endReason: String?,
    val distanceKm: Double,
    val topSpeedKmh: Double,
    val durationSec: Int,
    val overspeedCount: Int,
    val shortOverspeedCount: Int,
    val speedLimitKmh: Int,
    val timezoneId: String?
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): TripSummary? = TripSummary(
            id = doc.id,
            status = doc.getString("status") ?: "ended",
            startedAtUtc = doc.getLong("startedAtUtc") ?: return null,
            endedAtUtc = doc.getLong("endedAtUtc"),
            endReason = doc.getString("endReason"),
            distanceKm = doc.getDouble("distanceKm") ?: 0.0,
            topSpeedKmh = doc.getDouble("topSpeedKmh") ?: 0.0,
            durationSec = doc.getLong("durationSec")?.toInt() ?: 0,
            overspeedCount = doc.getLong("overspeedCount")?.toInt() ?: 0,
            shortOverspeedCount = doc.getLong("shortOverspeedCount")?.toInt() ?: 0,
            speedLimitKmh = doc.getLong("speedLimitKmh")?.toInt() ?: 0,
            timezoneId = doc.getString("timezoneId")
        )
    }
}

/** One row of the live event feed: drivers/{driverId}/events/{eventId}. */
data class DriverEvent(
    val id: String,
    val eventType: String,
    val timestampUtc: Long,
    val timezoneId: String?,
    val speedKmh: Double?,
    val speedLimitKmh: Int?,
    val topSpeedKmh: Double?,
    val durationSec: Int?,
    val address: String?,
    val batteryPercent: Int?,
    val delayed: Boolean,
    val mockLocationSuspected: Boolean
) {
    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): DriverEvent? = DriverEvent(
            id = doc.id,
            eventType = doc.getString("eventType") ?: return null,
            timestampUtc = doc.getLong("timestampUtc") ?: return null,
            timezoneId = doc.getString("timezoneId"),
            speedKmh = doc.getDouble("speedKmh"),
            speedLimitKmh = doc.getLong("speedLimitKmh")?.toInt(),
            topSpeedKmh = doc.getDouble("topSpeedKmh"),
            durationSec = doc.getLong("durationSec")?.toInt(),
            address = doc.getString("address"),
            batteryPercent = doc.getLong("batteryPercent")?.toInt(),
            delayed = doc.getBoolean("delayed") ?: false,
            mockLocationSuspected = doc.getBoolean("mockLocationSuspected") ?: false
        )
    }
}
