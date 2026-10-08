package com.lifesafety.driversafety.trip

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.lifesafety.driversafety.settings.DriverSettings
import com.lifesafety.driversafety.trip.db.EventEntity
import com.lifesafety.driversafety.trip.db.PointEntity
import com.lifesafety.driversafety.trip.db.TripEntity
import com.lifesafety.driversafety.trip.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.max

/**
 * The trip. A foreground service of type "location" that runs only between Start Trip and the end of the trip.
 *
 * Every GPS fix goes through: SpeedSmoother (accuracy filter + 3-reading average) -> OverspeedStateMachine
 * (alarm after 3 s, admins after the admin delay, recovery after 5 s under the limit) -> AutoEndDetector
 * (parked or no movement for N minutes) -> Room (every accepted point) -> upload in batches every 10 s, with
 * the driver's "live" block for the admin dashboard. Events (overspeed started, back to normal, trip
 * started/ended) carry battery, network, address and mock-location info. Short overspeeds (alarm but no
 * admin alert) are only counted in the trip record.
 *
 * Threading: everything runs on the main thread through [scope]; Room and Firestore calls are suspend
 * functions that do their work elsewhere. Uploads run in their own job so the 1-second ticker never waits
 * on the network. In debug builds the fix source can be DriveSimulator instead of the real GPS.
 */
class TripService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var repo: TripRepository? = null
    private lateinit var alarm: AlarmPlayer
    private lateinit var fused: FusedLocationProviderClient

    private val smoother = SpeedSmoother()
    private val overspeed = OverspeedStateMachine()
    private val autoEnd = AutoEndDetector()

    private var settings = DriverSettings.DEFAULT
    private var trip: TripEntity? = null
    private var uid = ""
    private var adminNames = ""
    private var starting = false
    private var ending = false
    private var startEventPending = false

    private var lastAccepted: Location? = null
    private var lastAcceptedAtMs = 0L
    private var distanceM = 0.0
    private var topSpeedKmh = 0.0
    private var overspeedCount = 0
    private var shortOverspeedCount = 0
    private var currentIntervalMs = 0L
    private var lastFlushAtMs = 0L
    private var lastFlushOk = true
    private var flushCount = 0
    private var tick = 0

    private var locationJob: Job? = null
    private var tickerJob: Job? = null
    private var settingsJob: Job? = null
    private var simulationJob: Job? = null
    private var flushJob: Job? = null
    private val eventJobs = CopyOnWriteArraySet<Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        alarm = AlarmPlayer(this, scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (trip == null && !starting && !ending) {
                adminNames = intent.getStringExtra(EXTRA_ADMIN_NAMES).orEmpty()
                beginTrip()
            }

            ACTION_END -> scope.launch { endTrip(TripEndReason.DRIVER) }

            // Restarted by the system without a trip: nothing to resume.
            else -> if (trip == null && !starting) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        alarm.release()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }

    // ---- Start ----------------------------------------------------------------------------------

    private fun beginTrip() {
        starting = true
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            starting = false
            stopSelf()
            return
        }
        uid = user.uid
        val repository = TripRepository(this, uid).also { repo = it }
        // Android requires the foreground notification within a few seconds of startForegroundService().
        try {
            showForeground(Notifications.trip(this, null, settings.speedLimitKmh, false))
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground service", e)
            starting = false
            stopSelf()
            return
        }
        Notifications.cancelMonitoring(this)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DriverSafety:trip")
            .apply { acquire(MAX_TRIP_MS) }
        TripStateHolder.update { it.copy(starting = true) }

        scope.launch {
            try {
                settings = withTimeoutOrNull(5_000L) { repository.settingsFlow().first() } ?: DriverSettings.DEFAULT
                val nowUtc = System.currentTimeMillis()
                val nowMs = SystemClock.elapsedRealtime()
                val started = repository.startTrip(settings, nowUtc)
                trip = started
                smoother.reset()
                overspeed.reset(nowMs)
                autoEnd.start(nowMs)
                lastAccepted = null
                lastAcceptedAtMs = nowMs
                distanceM = 0.0
                topSpeedKmh = 0.0
                overspeedCount = 0
                shortOverspeedCount = 0
                tick = 0
                flushCount = 0
                lastFlushAtMs = nowUtc
                lastFlushOk = true
                // Recorded with the first accurate fix, so the event has a real position.
                startEventPending = true
                TripStateHolder.update {
                    TripLiveState(
                        tripActive = true,
                        tripId = started.id,
                        startedAtMs = nowUtc,
                        limitKmh = settings.speedLimitKmh,
                        driverCanEndTrip = settings.driverCanEndTrip,
                        lastSyncAtMs = it.lastSyncAtMs,
                        simulating = DriveSimulator.enabled.value
                    )
                }
                settingsJob = launch {
                    repository.settingsFlow().collect { fresh ->
                        settings = fresh
                        TripStateHolder.update { it.copy(limitKmh = fresh.speedLimitKmh, driverCanEndTrip = fresh.driverCanEndTrip) }
                    }
                }
                // Emits the current value at once, so this also starts the first location source.
                simulationJob = launch { DriveSimulator.enabled.collect { startLocationUpdates() } }
                tickerJob = launch {
                    while (isActive) {
                        delay(1_000L)
                        onTick()
                    }
                }
            } finally {
                starting = false
            }
        }
    }

    private fun showForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, Notifications.ID_TRIP, notification, type)
    }

    // ---- Location -------------------------------------------------------------------------------

    /** 2 s normally, 5 s below 20% battery, 10 s below 10%. */
    private fun desiredIntervalMs(): Long {
        val percent = DeviceInfo.battery(this).percent
        return when {
            percent in 0..9 -> 10_000L
            percent in 10..19 -> 5_000L
            else -> 2_000L
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        val simulating = DriveSimulator.enabled.value
        val interval = desiredIntervalMs()
        currentIntervalMs = interval
        smoother.reset()
        TripStateHolder.update { it.copy(simulating = simulating, gpsOk = false, locationPermissionMissing = false) }
        val source: Flow<Location> = if (simulating) DriveSimulator.locations() else fusedLocations(interval)
        locationJob = scope.launch {
            source
                .catch { e ->
                    Log.w(TAG, "location source failed", e)
                    if (e is SecurityException) {
                        TripStateHolder.update { it.copy(locationPermissionMissing = true, gpsOk = false) }
                    }
                }
                .collect { onFix(it) }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun fusedLocations(intervalMs: Long): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission missing"))
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) TripStateHolder.update { it.copy(gpsOk = false) }
            }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    private fun onFix(location: Location) {
        val current = trip ?: return
        val repository = repo ?: return
        val nowMs = SystemClock.elapsedRealtime()
        val nowUtc = System.currentTimeMillis()
        // A fix without an accuracy estimate is treated as unusable, not as perfect.
        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.NaN
        autoEnd.onRawFix(location.latitude, location.longitude, accuracy, nowMs)
        val rawKmh = if (location.hasSpeed()) location.speed * 3.6 else -1.0
        // Rounded to 0.1 km/h so the numbers in the database read cleanly.
        val smoothed = smoother.accept(rawKmh, accuracy, location.hasSpeed(), nowMs)?.let { Math.round(it * 10) / 10.0 }
        TripStateHolder.update { it.copy(gpsOk = smoothed != null, lastFixAtMs = nowUtc, speedKmh = smoothed ?: it.speedKmh) }
        if (smoothed == null) return
        lastAcceptedAtMs = nowMs

        lastAccepted?.let { previous ->
            if (smoothed >= 1.0) {
                distanceM += Geo.distanceMeters(previous.latitude, previous.longitude, location.latitude, location.longitude)
            }
        }
        lastAccepted = location
        topSpeedKmh = max(topSpeedKmh, smoothed)
        autoEnd.onFix(smoothed, nowMs)

        if (startEventPending) {
            startEventPending = false
            recordEvent(EventType.TRIP_STARTED, location, smoothed, null, null, current.startedAtUtc)
        }

        val battery = DeviceInfo.battery(this)
        scope.launch {
            repository.addPoint(
                PointEntity(
                    driverId = uid,
                    tripId = current.id,
                    timestampUtc = nowUtc,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speedKmh = smoothed,
                    accuracyM = accuracy,
                    batteryPercent = battery.percent,
                    isCharging = battery.charging
                )
            )
        }

        val actions = overspeed.update(
            speedKmh = smoothed,
            limitKmh = settings.speedLimitKmh,
            toleranceKmh = settings.toleranceKmh,
            adminAlertDelayMs = settings.adminAlertDelaySec * 1_000L,
            nowMs = nowMs
        )
        handleActions(actions, location, smoothed, nowUtc)
        TripStateHolder.update {
            it.copy(
                distanceKm = distanceM / 1000.0,
                topSpeedKmh = topSpeedKmh,
                overspeed = overspeed.isAlarmOn,
                adminsAlerted = overspeed.adminsAlerted
            )
        }
        // Battery dropped into another band: slow the GPS down.
        if (!DriveSimulator.enabled.value && desiredIntervalMs() != currentIntervalMs) startLocationUpdates()
    }

    // ---- Overspeed actions ----------------------------------------------------------------------

    private fun handleActions(actions: List<OverspeedAction>, location: Location?, speedKmh: Double, nowUtc: Long) {
        for (action in actions) {
            when (action) {
                OverspeedAction.StartAlarm -> {
                    alarm.start()
                    updateTripNotification()
                }

                OverspeedAction.StopAlarm -> {
                    alarm.stop()
                    updateTripNotification()
                }

                is OverspeedAction.AlertAdmins ->
                    recordEvent(EventType.OVERSPEED_STARTED, location, action.speedKmh, action.topSpeedKmh, null, nowUtc)

                is OverspeedAction.BackToNormal -> {
                    overspeedCount++
                    recordEvent(EventType.BACK_TO_NORMAL, location, speedKmh, action.topSpeedKmh, action.durationSec, nowUtc)
                }

                // Alarm but no admin alert: counted in the trip record only, no event.
                is OverspeedAction.ShortOverspeed -> shortOverspeedCount++
            }
        }
    }

    private fun recordEvent(
        type: EventType,
        location: Location?,
        speedKmh: Double,
        topSpeedKmh: Double?,
        durationSec: Int?,
        nowUtc: Long
    ) {
        val current = trip ?: return
        val repository = repo ?: return
        val battery = DeviceInfo.battery(this)
        val network = DeviceInfo.networkType(this)
        val mock = location?.let { DeviceInfo.isMockLocation(it) } ?: false
        val limit = settings.speedLimitKmh
        val job = scope.launch {
            val address = location?.let {
                withContext(Dispatchers.IO) { Geocoding.reverse(this@TripService, it.latitude, it.longitude) }
            }
            repository.addEvent(
                EventEntity(
                    id = UUID.randomUUID().toString(),
                    driverId = uid,
                    tripId = current.id,
                    eventType = type.wireName,
                    timestampUtc = nowUtc,
                    timezoneId = DeviceInfo.timezoneId(),
                    speedKmh = speedKmh,
                    speedLimitKmh = limit,
                    topSpeedKmh = topSpeedKmh,
                    durationSec = durationSec,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracyM = location?.let { if (it.hasAccuracy()) it.accuracy else null },
                    address = address,
                    batteryPercent = battery.percent,
                    isCharging = battery.charging,
                    networkType = network,
                    mockLocationSuspected = mock
                )
            )
            scheduleFlush()
        }
        eventJobs += job
        job.invokeOnCompletion { eventJobs -= job }
    }

    // ---- Ticker, upload, notification -----------------------------------------------------------

    private suspend fun onTick() {
        val current = trip ?: return
        tick++
        val nowMs = SystemClock.elapsedRealtime()
        val nowUtc = System.currentTimeMillis()
        TripStateHolder.update { it.copy(elapsedSec = ((nowUtc - current.startedAtUtc) / 1000L).toInt()) }
        if (autoEnd.shouldEnd(nowMs, settings.autoEndMinutes)) {
            // Never run endTrip inside the ticker: endTrip cancels the ticker, which would cancel itself mid-way.
            scope.launch { endTrip(TripEndReason.AUTO) }
            return
        }
        // No usable fix for a while (tunnel, basement): nothing to show, and no data to keep an alarm on.
        if (lastAccepted != null && nowMs - lastAcceptedAtMs > GPS_STALE_MS) {
            val state = TripStateHolder.state.value
            if (state.speedKmh != null || overspeed.isAlarmOn) {
                handleActions(overspeed.reset(lastAcceptedAtMs), lastAccepted, state.speedKmh ?: 0.0, nowUtc)
                TripStateHolder.update { it.copy(speedKmh = null, gpsOk = false, overspeed = false, adminsAlerted = false) }
                updateTripNotification()
            }
        }
        val flushEvery = if (lastFlushOk) FLUSH_EVERY_MS else FLUSH_RETRY_MS
        if (nowUtc - lastFlushAtMs >= flushEvery) scheduleFlush()
        if (tick % 10 == 0) updateTripNotification()
    }

    /** Runs one upload round in its own job, so callers never wait on the network. */
    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch { flushNow() }
    }

    /** Uploads pending points and events, refreshes the trip summary now and then, and the live block every time. */
    private suspend fun flushNow() {
        val current = trip ?: return
        val repository = repo ?: return
        lastFlushAtMs = System.currentTimeMillis()
        if (DeviceInfo.networkType(this) == "none") {
            lastFlushOk = false
            TripStateHolder.update { it.copy(pendingUploads = repository.pendingCount()) }
            return
        }
        flushCount++
        if (flushCount % TRIP_SUMMARY_EVERY_FLUSHES == 1) repository.saveTrip(progress(current, null, null))
        val uploadsOk = repository.flush()
        val liveOk = repository.updateLive(liveMap("on_trip"))
        lastFlushOk = uploadsOk && liveOk
        val pending = repository.pendingCount()
        val now = System.currentTimeMillis()
        TripStateHolder.update {
            it.copy(lastSyncAtMs = if (lastFlushOk) now else it.lastSyncAtMs, pendingUploads = pending)
        }
    }

    private fun progress(current: TripEntity, endedAtUtc: Long?, reason: TripEndReason?): TripEntity {
        val until = endedAtUtc ?: System.currentTimeMillis()
        return current.copy(
            endedAtUtc = endedAtUtc,
            endReason = reason?.wireName,
            lastPointAtUtc = lastAccepted?.time,
            distanceKm = distanceM / 1000.0,
            topSpeedKmh = topSpeedKmh,
            durationSec = ((until - current.startedAtUtc) / 1000L).toInt().coerceAtLeast(0),
            overspeedCount = overspeedCount,
            shortOverspeedCount = shortOverspeedCount
        )
    }

    private fun liveMap(status: String): Map<String, Any?> {
        val state = TripStateHolder.state.value
        val battery = DeviceInfo.battery(this)
        val location = lastAccepted
        return mapOf(
            "status" to status,
            "tripId" to trip?.id,
            "tripStartedAtUtc" to trip?.startedAtUtc,
            "speedKmh" to (state.speedKmh ?: 0.0),
            "speedLimitKmh" to settings.speedLimitKmh,
            "latitude" to location?.latitude,
            "longitude" to location?.longitude,
            "lastFixAtUtc" to location?.time,
            "lastSyncAtUtc" to System.currentTimeMillis(),
            "batteryPercent" to battery.percent,
            "isCharging" to battery.charging,
            "networkType" to DeviceInfo.networkType(this),
            "overspeedNow" to overspeed.isAlarmOn,
            "adminsAlerted" to overspeed.adminsAlerted,
            "distanceKm" to distanceM / 1000.0,
            "topSpeedKmh" to topSpeedKmh,
            "simulated" to DriveSimulator.enabled.value
        )
    }

    private fun updateTripNotification() {
        if (trip == null) return
        val state = TripStateHolder.state.value
        // Calling startForeground again replaces the notification without needing the notification permission.
        try {
            showForeground(Notifications.trip(this, state.speedKmh, settings.speedLimitKmh, overspeed.isAlarmOn))
        } catch (e: Exception) {
            Log.w(TAG, "notification update failed", e)
        }
    }

    // ---- End ------------------------------------------------------------------------------------

    private suspend fun endTrip(reason: TripEndReason) {
        if (ending) return
        val current = trip
        if (current == null) {
            if (!starting) stopSelf()
            return
        }
        ending = true
        var pending = 0
        try {
            locationJob?.cancel()
            tickerJob?.cancel()
            settingsJob?.cancel()
            simulationJob?.cancel()
            flushJob?.cancelAndJoin()
            val nowMs = SystemClock.elapsedRealtime()
            val nowUtc = System.currentTimeMillis()
            val lastSpeed = TripStateHolder.state.value.speedKmh ?: 0.0
            handleActions(overspeed.reset(nowMs), lastAccepted, lastSpeed, nowUtc)
            alarm.stop()
            if (startEventPending) {
                startEventPending = false
                recordEvent(EventType.TRIP_STARTED, lastAccepted, 0.0, null, null, current.startedAtUtc)
            }
            val ended = progress(current, nowUtc, reason)
            recordEvent(EventType.TRIP_ENDED, lastAccepted, lastSpeed, topSpeedKmh, ended.durationSec, nowUtc)
            // The rest must finish even if this coroutine is cancelled, and never wait long on the network:
            // whatever is left is uploaded by SyncWorker, and fixed document ids make re-sends harmless.
            withContext(NonCancellable) {
                withTimeoutOrNull(15_000L) { eventJobs.toList().joinAll() }
                val repository = repo
                repository?.saveTrip(ended)
                trip = null
                if (repository != null) {
                    withTimeoutOrNull(15_000L) {
                        repository.flush()
                        repository.updateLive(liveMap("idle"))
                    }
                    pending = repository.pendingCount()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "endTrip failed", e)
        } finally {
            trip = null
            SyncWorker.enqueue(this)
            TripStateHolder.update {
                TripLiveState(
                    limitKmh = settings.speedLimitKmh,
                    driverCanEndTrip = settings.driverCanEndTrip,
                    lastSyncAtMs = it.lastSyncAtMs,
                    pendingUploads = pending
                )
            }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // Back to "Monitoring active" even when the app is not open (auto-end with the screen off).
            if (adminNames.isNotBlank()) Notifications.showMonitoring(this, adminNames)
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "TripService"
        const val ACTION_START = "com.lifesafety.driversafety.action.START_TRIP"
        const val ACTION_END = "com.lifesafety.driversafety.action.END_TRIP"
        private const val EXTRA_ADMIN_NAMES = "adminNames"
        private const val FLUSH_EVERY_MS = 10_000L
        private const val FLUSH_RETRY_MS = 60_000L
        private const val GPS_STALE_MS = 10_000L
        private const val TRIP_SUMMARY_EVERY_FLUSHES = 6
        private const val MAX_TRIP_MS = 12L * 60 * 60 * 1000

        fun start(context: Context, adminNames: String) {
            val intent = Intent(context, TripService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ADMIN_NAMES, adminNames)
            ContextCompat.startForegroundService(context, intent)
        }

        fun end(context: Context) {
            context.startService(Intent(context, TripService::class.java).setAction(ACTION_END))
        }
    }
}
