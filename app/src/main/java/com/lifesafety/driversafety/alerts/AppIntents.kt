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
    const val EXTRA_AT = "at"
    const val OPEN_ALERTS = "alerts"
    const val OPEN_START_TRIP = "start_trip"
    /** A tap older than this (the intent came back after process death) is ignored. */
    private const val MAX_AGE_MS = 10 * 60 * 1000L

    /** Admin: show the alerts inbox. */
    val openAlerts = MutableStateFlow(false)

    /** Driver: an admin asked for a trip; start it as soon as the trip screen can. */
    val startTripRequested = MutableStateFlow(false)

    fun handle(intent: Intent?) {
        if (intent == null) return
        val open = intent.getStringExtra(EXTRA_OPEN) ?: return
        // Consumed: a rotated or re-created activity must not raise the flag again.
        intent.removeExtra(EXTRA_OPEN)
        val age = System.currentTimeMillis() - intent.getLongExtra(EXTRA_AT, 0L)
        if (age > MAX_AGE_MS) return
        when (open) {
            OPEN_ALERTS -> openAlerts.value = true
            OPEN_START_TRIP -> startTripRequested.value = true
        }
    }

    /** The intent behind a notification; built when the notification is posted, so "at" is the posting time. */
    fun openApp(context: Context, open: String?): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply {
                if (open != null) {
                    putExtra(EXTRA_OPEN, open)
                    putExtra(EXTRA_AT, System.currentTimeMillis())
                }
            }
}
