package com.lifesafety.driversafety.alerts

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lifesafety.driversafety.trip.TripService
import com.lifesafety.driversafety.trip.TripStateHolder

/**
 * Receives push messages. Every message is a data message (no "notification" block), so this runs even when
 * the app is in the background and the phone decides what to show:
 *   type=alert          admin phone: an inbox alert (overspeed, back to normal, link change)
 *   type=start_request  driver phone: an admin asks for a trip
 *   type=end_trip       driver phone: the primary admin ends the current trip
 */
class AlertsMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FcmTokens.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // A message meant for a user who has since signed out on this phone is dropped.
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        when (data["type"]) {
            "alert" -> {
                // Addressed to one admin. Another account signed in on this phone must not see it.
                if (data["toUid"] != uid) return
                val alert = Alert.fromFields(data["alertId"] ?: message.messageId ?: System.currentTimeMillis().toString(), data)
                AlertNotifications.showAlert(this, alert)
            }

            "start_request" -> {
                if (data["driverId"] != uid) return
                if (!TripStateHolder.state.value.tripActive) {
                    AlertNotifications.showStartRequest(this, data["adminName"].orEmpty())
                }
            }

            "end_trip" -> {
                if (data["driverId"] != uid) return
                val state = TripStateHolder.state.value
                val tripId = data["tripId"]
                // The service also watches the driver record for this command; the push just makes it immediate.
                if (state.tripActive && (tripId == null || tripId == state.tripId)) {
                    TripService.endByAdmin(this, data["adminName"].orEmpty())
                }
            }

            else -> Log.w(TAG, "unknown push type: ${data["type"]}")
        }
    }

    companion object {
        private const val TAG = "AlertsMessaging"
    }
}
