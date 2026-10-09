package com.lifesafety.driversafety.alerts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.trip.Notifications

/**
 * Notifications built from push messages (FCM data messages carry only key/value pairs; the phone writes
 * the text in its own language):
 *  - admin side: alerts (overspeed started, back to normal, link changes) on the "alerts" channel
 *  - driver side: "please start a trip" requests on the "requests" channel
 * Phase 4 adds a separate loud channel for SOS.
 */
object AlertNotifications {
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_REQUESTS = "requests"
    private const val ID_START_REQUEST = 2001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.notif_channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notif_channel_alerts_desc)
                setSound(sound, attributes)
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REQUESTS, context.getString(R.string.notif_channel_requests), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.notif_channel_requests_desc)
                setSound(sound, attributes)
                enableVibration(true)
            }
        )
    }

    /** Admin phone: one notification per alert, replaced if the same alert arrives twice. */
    fun showAlert(context: Context, alert: Alert) {
        val urgent = alert.type == AlertType.OVERSPEED_STARTED
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(AlertText.title(context, alert))
            .setContentText(AlertText.body(context, alert))
            .setStyle(NotificationCompat.BigTextStyle().bigText(AlertText.body(context, alert)))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent(context, 1, AppIntents.OPEN_ALERTS))
            .build()
        post(context, alert.id.hashCode(), notification)
    }

    /** Driver phone: an admin asks for a trip. Tapping opens the app and starts the trip. */
    fun showStartRequest(context: Context, adminName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_start_request_title))
            .setContentText(context.getString(R.string.notif_start_request_text, adminName))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_start_request_text, adminName)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent(context, 2, AppIntents.OPEN_START_TRIP))
            .build()
        post(context, ID_START_REQUEST, notification)
    }

    fun cancelStartRequest(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_START_REQUEST)
    }

    @SuppressLint("MissingPermission") // Notifications.canPost() checks POST_NOTIFICATIONS first
    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!Notifications.canPost(context)) return
        ensureChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call. Nothing to do.
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, open: String): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode, AppIntents.openApp(context, open),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
