package com.lifesafety.driversafety.admin

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.lifesafety.driversafety.pairing.FUNCTIONS_REGION
import com.lifesafety.driversafety.settings.DriverSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Admin side of Phase 3: the driver's trips and events (read live from Firestore, rules allow it only for
 * approved admins) and the Cloud Functions behind Save settings, Request trip start and End trip now.
 */
class AdminRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
) {

    fun tripsFlow(driverId: String, limit: Long = 30): Flow<List<TripSummary>> = callbackFlow {
        val registration = db.collection("drivers").document(driverId).collection("trips")
            .orderBy("startedAtUtc", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) trySend(snapshot.documents.mapNotNull { TripSummary.fromSnapshot(it) })
            }
        awaitClose { registration.remove() }
    }

    fun eventsFlow(driverId: String, limit: Long = 20): Flow<List<DriverEvent>> = callbackFlow {
        val registration = db.collection("drivers").document(driverId).collection("events")
            .orderBy("timestampUtc", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) trySend(snapshot.documents.mapNotNull { DriverEvent.fromSnapshot(it) })
            }
        awaitClose { registration.remove() }
    }

    suspend fun updateSettings(driverId: String, settings: DriverSettings, phone: String?) {
        call(
            "updateDriverSettings",
            mapOf(
                "driverId" to driverId,
                "settings" to mapOf(
                    "speedLimitKmh" to settings.speedLimitKmh,
                    "toleranceKmh" to settings.toleranceKmh,
                    "adminAlertDelaySec" to settings.adminAlertDelaySec,
                    "autoEndMinutes" to settings.autoEndMinutes,
                    "driverCanEndTrip" to settings.driverCanEndTrip
                ),
                "phone" to phone.orEmpty()
            )
        )
    }

    /** Returns how many of the driver's phones accepted the push (0 means the driver's app never registered). */
    suspend fun requestTripStart(driverId: String): Int =
        (call("requestTripStart", mapOf("driverId" to driverId))["delivered"] as? Number)?.toInt() ?: 0

    /** Returns true when the driver's phone has been silent for a while: the command waits for it to reconnect. */
    suspend fun endTripNow(driverId: String): Boolean =
        call("endTripNow", mapOf("driverId" to driverId))["stale"] as? Boolean ?: false

    private suspend fun call(name: String, data: Map<String, Any>): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(data).await()
        return result.data as? Map<*, *> ?: emptyMap<Any, Any>()
    }
}
