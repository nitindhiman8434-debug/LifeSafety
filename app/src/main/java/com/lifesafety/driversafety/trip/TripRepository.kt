package com.lifesafety.driversafety.trip

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lifesafety.driversafety.settings.DriverSettings
import com.lifesafety.driversafety.trip.db.AppDatabase
import com.lifesafety.driversafety.trip.db.EventEntity
import com.lifesafety.driversafety.trip.db.PointEntity
import com.lifesafety.driversafety.trip.db.TripEntity
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

enum class EventType(val wireName: String) {
    OVERSPEED_STARTED("overspeed_started"),
    BACK_TO_NORMAL("back_to_normal"),
    SHORT_OVERSPEED("short_overspeed"),
    TRIP_STARTED("trip_started"),
    TRIP_ENDED("trip_ended")
}

enum class TripEndReason(val wireName: String) {
    AUTO("auto"),
    DRIVER("driver"),
    ADMIN("admin"),
    INTERRUPTED("interrupted")
}

/**
 * Offline-first storage and upload for one driver.
 *
 * Everything goes to Room first. [flush] uploads what is pending: points in batches (one Firestore
 * document per batch, about every 10 seconds), events one by one, and trip summaries. Every upload uses a
 * fixed document id, so sending the same thing twice (after a timeout, for example) changes nothing.
 * Firestore's own offline queue still holds a timed-out write and delivers it later; the fixed ids make that safe.
 */
class TripRepository(context: Context, private val uid: String) {

    private val db = AppDatabase.get(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val driverDoc: DocumentReference get() = firestore.collection("drivers").document(uid)
    private val flushLock = Mutex()

    /** The admin's settings for this driver, live. Emits defaults until the record exists. */
    fun settingsFlow(): Flow<DriverSettings> = callbackFlow {
        val registration = driverDoc.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            trySend(DriverSettings.fromMap(snapshot?.get("settings") as? Map<*, *>))
        }
        awaitClose { registration.remove() }
    }

    // ---- Trips ----

    suspend fun startTrip(settings: DriverSettings, nowUtc: Long): TripEntity {
        val trip = TripEntity(
            id = UUID.randomUUID().toString(),
            driverId = uid,
            startedAtUtc = nowUtc,
            speedLimitKmh = settings.speedLimitKmh,
            timezoneId = DeviceInfo.timezoneId()
        )
        db.tripDao().upsert(trip)
        return trip
    }

    suspend fun saveTrip(trip: TripEntity) {
        db.tripDao().upsert(trip.copy(synced = false))
    }

    /** Trips left open by a crash or a force-stop are closed as "interrupted" at their last point. */
    suspend fun closeStaleTrips(nowUtc: Long) {
        for (trip in db.tripDao().open()) {
            val lastPoint = db.pointDao().lastTimestampForTrip(trip.id) ?: trip.lastPointAtUtc ?: trip.startedAtUtc
            val ended = trip.copy(
                endedAtUtc = lastPoint,
                endReason = TripEndReason.INTERRUPTED.wireName,
                durationSec = ((lastPoint - trip.startedAtUtc) / 1000L).toInt().coerceAtLeast(0),
                synced = false
            )
            db.tripDao().upsert(ended)
        }
        db.tripDao().deleteSyncedBefore(nowUtc - 30L * 24 * 60 * 60 * 1000)
        db.eventDao().deleteUploadedBefore(nowUtc - 30L * 24 * 60 * 60 * 1000)
    }

    // ---- Points and events ----

    suspend fun addPoint(point: PointEntity) = db.pointDao().insert(point)

    suspend fun addEvent(event: EventEntity) = db.eventDao().insert(event)

    suspend fun pendingCount(): Int =
        db.pointDao().count() + db.eventDao().pendingCount() + db.tripDao().unsynced().size

    // ---- Upload ----

    /** Uploads everything pending. Returns false when something could not be sent (offline or timeout). */
    suspend fun flush(): Boolean = flushLock.withLock {
        var ok = uploadTrips()
        ok = uploadEvents() && ok
        ok = uploadPoints() && ok
        ok
    }

    private suspend fun uploadTrips(): Boolean {
        for (trip in db.tripDao().unsynced()) {
            val data = mapOf(
                "driverId" to trip.driverId,
                "status" to if (trip.endedAtUtc == null) "active" else "ended",
                "startedAtUtc" to trip.startedAtUtc,
                "endedAtUtc" to trip.endedAtUtc,
                "endReason" to trip.endReason,
                "lastPointAtUtc" to trip.lastPointAtUtc,
                "distanceKm" to trip.distanceKm,
                "topSpeedKmh" to trip.topSpeedKmh,
                "durationSec" to trip.durationSec,
                "overspeedCount" to trip.overspeedCount,
                "speedLimitKmh" to trip.speedLimitKmh,
                "timezoneId" to trip.timezoneId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!setWithTimeout(driverDoc.collection("trips").document(trip.id), data, merge = true)) return false
            db.tripDao().markSynced(trip.id)
        }
        return true
    }

    private suspend fun uploadEvents(): Boolean {
        val now = System.currentTimeMillis()
        for (event in db.eventDao().pending()) {
            val data = mapOf(
                "eventType" to event.eventType,
                "driverId" to event.driverId,
                "tripId" to event.tripId,
                "speedKmh" to event.speedKmh,
                "speedLimitKmh" to event.speedLimitKmh,
                "topSpeedKmh" to event.topSpeedKmh,
                "durationSec" to event.durationSec,
                "timestampUtc" to event.timestampUtc,
                "timezoneId" to event.timezoneId,
                "latitude" to event.latitude,
                "longitude" to event.longitude,
                "accuracyM" to event.accuracyM,
                "address" to event.address,
                "batteryPercent" to event.batteryPercent,
                "isCharging" to event.isCharging,
                "networkType" to event.networkType,
                "mockLocationSuspected" to event.mockLocationSuspected,
                // Sent more than a minute after it happened (the phone was offline).
                "delayed" to (now - event.timestampUtc > DELAYED_AFTER_MS),
                "uploadedAt" to FieldValue.serverTimestamp()
            )
            if (!setWithTimeout(driverDoc.collection("events").document(event.id), data)) return false
            db.eventDao().markUploaded(event.id)
        }
        return true
    }

    private suspend fun uploadPoints(): Boolean {
        while (true) {
            val oldest = db.pointDao().oldest(POINTS_PER_BATCH)
            if (oldest.isEmpty()) return true
            val tripId = oldest.first().tripId
            val batch = oldest.takeWhile { it.tripId == tripId }
            val last = batch.last()
            val data = mapOf(
                "driverId" to uid,
                "tripId" to tripId,
                "firstAtUtc" to batch.first().timestampUtc,
                "lastAtUtc" to last.timestampUtc,
                "count" to batch.size,
                "batteryPercent" to last.batteryPercent,
                "isCharging" to last.isCharging,
                "delayed" to (System.currentTimeMillis() - last.timestampUtc > DELAYED_AFTER_MS),
                "uploadedAt" to FieldValue.serverTimestamp(),
                "points" to batch.map {
                    mapOf("t" to it.timestampUtc, "lat" to it.latitude, "lng" to it.longitude, "s" to it.speedKmh, "a" to it.accuracyM)
                }
            )
            val docId = "$tripId-${batch.first().id}"
            val ref = driverDoc.collection("trips").document(tripId).collection("points").document(docId)
            if (!setWithTimeout(ref, data)) return false
            db.pointDao().delete(batch.map { it.id })
        }
    }

    /** The "live" block on the driver record: what the admin dashboard shows. */
    suspend fun updateLive(live: Map<String, Any?>): Boolean = try {
        withTimeout(UPLOAD_TIMEOUT_MS) {
            driverDoc.update(mapOf("live" to live, "updatedAt" to FieldValue.serverTimestamp())).await()
        }
        true
    } catch (e: TimeoutCancellationException) {
        false
    } catch (e: Exception) {
        Log.w(TAG, "live update failed", e)
        false
    }

    private suspend fun setWithTimeout(ref: DocumentReference, data: Map<String, Any?>, merge: Boolean = false): Boolean = try {
        withTimeout(UPLOAD_TIMEOUT_MS) {
            (if (merge) ref.set(data, SetOptions.merge()) else ref.set(data)).await()
        }
        true
    } catch (e: TimeoutCancellationException) {
        false
    } catch (e: Exception) {
        Log.w(TAG, "upload failed: ${ref.path}", e)
        false
    }

    companion object {
        private const val TAG = "TripRepository"
        private const val UPLOAD_TIMEOUT_MS = 20_000L
        private const val POINTS_PER_BATCH = 60
        private const val DELAYED_AFTER_MS = 60_000L
    }
}
