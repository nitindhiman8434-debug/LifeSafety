package com.lifesafety.driversafety.trip.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

/**
 * Offline storage. Everything the trip service records lands here first; uploads happen from here.
 * Points are deleted once uploaded (the server keeps them). Events and trips keep an "uploaded/synced" flag.
 */

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val startedAtUtc: Long,
    val endedAtUtc: Long? = null,
    val endReason: String? = null,
    val lastPointAtUtc: Long? = null,
    val distanceKm: Double = 0.0,
    val topSpeedKmh: Double = 0.0,
    val durationSec: Int = 0,
    /** Episodes that reached the admins ("Overspeed started" / "Back to normal"). */
    val overspeedCount: Int = 0,
    /** Alarm episodes that ended before the admin delay. Kept in the trip record only, as the spec says. */
    val shortOverspeedCount: Int = 0,
    val speedLimitKmh: Int,
    val timezoneId: String,
    val synced: Boolean = false
)

@Entity(tableName = "points")
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val driverId: String,
    val tripId: String,
    val timestampUtc: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Double,
    val accuracyM: Float,
    val batteryPercent: Int,
    val isCharging: Boolean
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val tripId: String,
    val eventType: String,
    val timestampUtc: Long,
    val timezoneId: String,
    val speedKmh: Double,
    val speedLimitKmh: Int,
    val topSpeedKmh: Double?,
    val durationSec: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Float?,
    val address: String?,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val networkType: String,
    val mockLocationSuspected: Boolean,
    val uploaded: Boolean = false
)

@Dao
interface TripDao {
    @Upsert
    suspend fun upsert(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun get(id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE driverId = :driverId AND synced = 0")
    suspend fun unsynced(driverId: String): List<TripEntity>

    @Query("SELECT * FROM trips WHERE driverId = :driverId AND endedAtUtc IS NULL")
    suspend fun open(driverId: String): List<TripEntity>

    @Query("UPDATE trips SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM trips WHERE synced = 1 AND endedAtUtc IS NOT NULL AND endedAtUtc < :beforeUtc")
    suspend fun deleteSyncedBefore(beforeUtc: Long)
}

@Dao
interface PointDao {
    @Insert
    suspend fun insert(point: PointEntity)

    @Query("SELECT * FROM points WHERE driverId = :driverId ORDER BY id LIMIT :limit")
    suspend fun oldest(driverId: String, limit: Int): List<PointEntity>

    @Query("DELETE FROM points WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM points WHERE driverId = :driverId")
    suspend fun count(driverId: String): Int

    @Query("SELECT MAX(timestampUtc) FROM points WHERE tripId = :tripId")
    suspend fun lastTimestampForTrip(tripId: String): Long?
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events WHERE driverId = :driverId AND uploaded = 0 ORDER BY timestampUtc LIMIT 50")
    suspend fun pending(driverId: String): List<EventEntity>

    @Query("UPDATE events SET uploaded = 1 WHERE id = :id")
    suspend fun markUploaded(id: String)

    @Query("SELECT COUNT(*) FROM events WHERE driverId = :driverId AND uploaded = 0")
    suspend fun pendingCount(driverId: String): Int

    @Query("DELETE FROM events WHERE uploaded = 1 AND timestampUtc < :beforeUtc")
    suspend fun deleteUploadedBefore(beforeUtc: Long)
}

@Database(entities = [TripEntity::class, PointEntity::class, EventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun pointDao(): PointDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "driver-safety.db")
                    .build()
                    .also { instance = it }
            }
    }
}
