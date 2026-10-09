package com.lifesafety.driversafety.alerts

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** The admin's inbox: users/{adminId}/alerts, newest first. Only the "read" flag is ever written by the app. */
class AlertsRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun alertsFlow(adminId: String, limit: Long = 100): Flow<List<Alert>> = callbackFlow {
        val registration = db.collection("users").document(adminId).collection("alerts")
            .orderBy("timestampUtc", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) trySend(snapshot.documents.map { Alert.fromSnapshot(it) })
            }
        awaitClose { registration.remove() }
    }

    suspend fun markRead(adminId: String, alertIds: List<String>) {
        if (alertIds.isEmpty()) return
        val inbox = db.collection("users").document(adminId).collection("alerts")
        // Firestore allows 500 writes per batch; the inbox flow is capped at 100 anyway.
        alertIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id -> batch.update(inbox.document(id), "read", true) }
            batch.commit().await()
        }
    }
}
