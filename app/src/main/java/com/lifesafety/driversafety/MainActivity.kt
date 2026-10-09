package com.lifesafety.driversafety

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifesafety.driversafety.alerts.AlertNotifications
import com.lifesafety.driversafety.alerts.AppIntents
import com.lifesafety.driversafety.ui.AppRoot
import com.lifesafety.driversafety.ui.theme.DriverSafetyTheme

/** Single activity. All screens are Compose; AppRoot decides which one to show. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AlertNotifications.ensureChannels(this)
        // A tapped notification says what to open (alerts inbox, or start the requested trip).
        AppIntents.handle(intent)
        addOnNewIntentListener { AppIntents.handle(it) }
        setContent {
            DriverSafetyTheme {
                AppRoot()
            }
        }
    }
}
