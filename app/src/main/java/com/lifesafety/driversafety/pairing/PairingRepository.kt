package com.lifesafety.driversafety.pairing

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.lifesafety.driversafety.settings.DriverSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Reads links and driver records live from Firestore, and calls the Cloud Functions that change them.
 * The app never writes links, driver records or codes itself (Firestore rules forbid it).
 */
class PairingRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
) {

    fun linksForDriver(driverId: String): Flow<List<Link>> =
        linksFlow(db.collection("links").whereEqualTo("driverId", driverId))

    fun linksForAdmin(adminId: String): Flow<List<Link>> =
        linksFlow(db.collection("links").whereEqualTo("adminId", adminId))

    private fun linksFlow(query: Query): Flow<List<Link>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toLink() })
        }
        awaitClose { registration.remove() }
    }

    /** Emits null while the record does not exist or cannot be read. */
    fun driverRecord(driverId: String): Flow<DriverRecord?> = callbackFlow {
        val registration = db.collection("drivers").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toDriverRecord())
            }
        awaitClose { registration.remove() }
    }

    // ---- Cloud Functions ----

    suspend fun createPairingCode(): PairingCode =
        call("createPairingCode").toPairingCode()

    suspend fun createCoAdminCode(driverId: String): PairingCode =
        call("createCoAdminCode", mapOf("driverId" to driverId)).toPairingCode()

    /** Returns the admin's name. */
    suspend fun redeemPairingCode(code: String): String =
        call("redeemPairingCode", mapOf("code" to code))["adminName"] as? String ?: ""

    /** Returns the driver's name. */
    suspend fun redeemCoAdminCode(code: String): String =
        call("redeemCoAdminCode", mapOf("code" to code))["driverName"] as? String ?: ""

    suspend fun respondToConsent(adminId: String, accept: Boolean) {
        call("respondToConsent", mapOf("adminId" to adminId, "accept" to accept))
    }

    suspend fun removeAdmin(adminId: String) {
        call("removeAdmin", mapOf("adminId" to adminId))
    }

    suspend fun leaveDriver(driverId: String) {
        call("leaveDriver", mapOf("driverId" to driverId))
    }

    suspend fun removeSecondaryAdmin(driverId: String) {
        call("removeSecondaryAdmin", mapOf("driverId" to driverId))
    }

    private suspend fun call(name: String, data: Map<String, Any> = emptyMap()): Map<*, *> {
        val result = functions.getHttpsCallable(name).call(data).await()
        return result.data as? Map<*, *> ?: emptyMap<Any, Any>()
    }
}

private fun Map<*, *>.toPairingCode(): PairingCode {
    // Count down from this phone's clock plus the server's "valid for" time, so a wrong phone clock does not matter.
    val validFor = (this["validForMillis"] as? Number)?.toLong()
    val expiresAt = if (validFor != null) System.currentTimeMillis() + validFor
    else (this["expiresAtMillis"] as? Number)?.toLong() ?: 0L
    return PairingCode(code = this["code"] as? String ?: "", expiresAtMillis = expiresAt)
}

private fun DocumentSnapshot.toLink(): Link? {
    val role = LinkRole.fromWire(getString("role")) ?: return null
    val status = LinkStatus.fromWire(getString("status")) ?: return null
    return Link(
        id = id,
        driverId = getString("driverId") ?: return null,
        adminId = getString("adminId") ?: return null,
        role = role,
        status = status,
        driverName = getString("driverName").orEmpty(),
        adminName = getString("adminName").orEmpty()
    )
}

private fun DocumentSnapshot.toDriverRecord(): DriverRecord = DriverRecord(
    id = id,
    displayName = getString("displayName").orEmpty(),
    linkStatus = getString("linkStatus") ?: "unlinked",
    primaryAdminId = getString("primaryAdminId"),
    primaryAdminName = getString("primaryAdminName"),
    secondaryAdminId = getString("secondaryAdminId"),
    secondaryAdminName = getString("secondaryAdminName"),
    secondaryStatus = getString("secondaryStatus") ?: "none",
    settings = DriverSettings.fromMap(get("settings") as? Map<*, *>)
)
