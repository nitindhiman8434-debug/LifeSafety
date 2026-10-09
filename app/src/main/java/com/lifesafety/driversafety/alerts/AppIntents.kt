package com.lifesafety.driversafety.alerts

import android.content.Context
import android.content.Intent
import com.lifesafety.driversafety.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What a tapped notification asks the app to do. MainActivity reads the intent extra and raises the flag;
 * the screen that can act on it clears the flag.
 */
object AppIntents {
    const val EXTRA_OPEN = "open"
    const val OPEN_ALERTS = "alerts"
    const val OPEN_START_TRIP = "start_trip"

    /** Admin: show the alerts inbox. */
    val openAlerts = MutableStateFlow(false)

    /** Driver: an admin asked for a trip; start it as soon as the trip screen can. */
    val startTripRequested = MutableStateFlow(false)

    fun handle(intent: Intent?) {
        if (intent == null) return
        when (intent.getStringExtra(EXTRA_OPEN)) {
            OPEN_ALERTS -> openAlerts.value = true
            OPEN_START_TRIP -> startTripRequested.value = true
            else -> return
        }
        // Consumed: a rotated or re-created activity must not raise the flag again.
        intent.removeExtra(EXTRA_OPEN)
    }

    fun openApp(context: Context, open: String?): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (open != null) putExtra(EXTRA_OPEN, open) }
}
