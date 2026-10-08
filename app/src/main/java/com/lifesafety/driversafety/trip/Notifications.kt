package com.lifesafety.driversafety.trip

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifesafety.driversafety.MainActivity
import com.lifesafety.driversafety.R
import java.util.Locale

/**
 * Two notifications, both required by Google Play's monitoring-app rules:
 *  - "Monitoring active" while a driver is linked and idle (posted by the app, cancelled when unlinked)
 *  - the trip notification, which is the foreground service's notification and replaces the first one
 */
object Notifications {
    const val CHANNEL_MONITORING = "monitoring"
    const val CHANNEL_TRIP = "trip"
    const val ID_MONITORING = 1001
    const val ID_TRIP = 1002

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, context.getString(R.string.notif_channel_monitoring), NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TRIP, context.getString(R.string.notif_channel_trip), NotificationManager.IMPORTANCE_LOW)
        )
    }

    /** True when the app may post notifications (permission on Android 13+, and not blocked in settings). */
    fun canPost(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun monitoring(context: Context, adminNames: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_monitoring_title))
            .setContentText(context.getString(R.string.notif_monitoring_text, adminNames))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_monitoring_text, adminNames)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp(context))
            .build()

    fun trip(context: Context, speedKmh: Double?, limitKmh: Int, overspeed: Boolean): Notification {
        val text = when {
            overspeed -> context.getString(R.string.notif_trip_slow_down)
            speedKmh == null -> context.getString(R.string.notif_trip_no_gps)
            else -> context.getString(R.string.notif_trip_text, String.format(Locale.US, "%.0f", speedKmh), limitKmh)
        }
        return NotificationCompat.Builder(context, CHANNEL_TRIP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (overspeed) R.string.trip_slow_down else R.string.notif_trip_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openApp(context))
            .build()
    }

    @SuppressLint("MissingPermission") // canPost() checks POST_NOTIFICATIONS first
    fun showMonitoring(context: Context, adminNames: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(ID_MONITORING, monitoring(context, adminNames))
        } catch (e: SecurityException) {
            // Permission was revoked between the check and the call. Nothing to do.
        }
    }

    fun cancelMonitoring(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_MONITORING)
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
