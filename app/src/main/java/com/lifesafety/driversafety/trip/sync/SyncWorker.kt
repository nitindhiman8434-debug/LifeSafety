package com.lifesafety.driversafety.trip.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.lifesafety.driversafety.trip.TripRepository
import com.lifesafety.driversafety.trip.TripStateHolder
import java.util.concurrent.TimeUnit

/**
 * Uploads whatever the trip service could not send (phone was offline). WorkManager runs it when the
 * network is back, and retries with growing delays. Events uploaded late are marked "delayed" by the repository.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // During a trip the service uploads itself and enqueues this worker again when the trip ends.
        if (TripStateHolder.state.value.tripActive) return Result.success()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val repo = TripRepository(applicationContext, uid)
        val ok = repo.flush()
        val pending = repo.pendingCount()
        val now = System.currentTimeMillis()
        TripStateHolder.update { it.copy(pendingUploads = pending, lastSyncAtMs = if (ok) now else it.lastSyncAtMs) }
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "trip-sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
