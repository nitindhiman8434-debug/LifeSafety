package com.lifesafety.driversafety.alerts

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.lifesafety.driversafety.pairing.FUNCTIONS_REGION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps the server's list of this phone's push token up to date.
 * The token is registered once per signed-in user (remembered in SharedPreferences), re-registered when
 * Firebase rotates it, and removed again on sign-out so the next user on this phone does not get the
 * previous user's alerts.
 */
object FcmTokens {
    private const val TAG = "FcmTokens"
    private const val PREFS = "fcm"
    private const val KEY_REGISTERED = "registered" // "<uid>:<token>"
    private const val UNREGISTER_TIMEOUT_MS = 4_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Call whenever a signed-in user with a role is on screen. Cheap when nothing changed. */
    suspend fun sync(context: Context, uid: String) {
        val token = try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Log.w(TAG, "no push token yet", e)
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wanted = "$uid:$token"
        if (prefs.getString(KEY_REGISTERED, null) == wanted) return
        try {
            functions().getHttpsCallable("registerFcmToken").call(mapOf("token" to token)).await()
            prefs.edit().putString(KEY_REGISTERED, wanted).apply()
        } catch (e: Exception) {
            // Offline, or the user has no role yet: tried again next time the app opens.
            Log.w(TAG, "token registration failed", e)
        }
    }

    /** Firebase handed out a new token: forget the old registration and register again. */
    fun onNewToken(context: Context, token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_REGISTERED).apply()
        scope.launch { sync(context.applicationContext, uid) }
    }

    /** Before sign-out: tell the server to stop sending to this phone, and drop the token itself. */
    suspend fun unregister(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val registered = prefs.getString(KEY_REGISTERED, null)
        prefs.edit().remove(KEY_REGISTERED).apply()
        val token = registered?.substringAfter(':', "")?.takeIf { it.isNotBlank() }
        // Best effort, both at once, one short timeout: sign-out must not hang when the phone is offline.
        // If this fails, the next account that registers this token on the server takes it over anyway.
        withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
            coroutineScope {
                if (token != null) {
                    launch {
                        try {
                            functions().getHttpsCallable("unregisterFcmToken").call(mapOf("token" to token)).await()
                        } catch (e: Exception) {
                            Log.w(TAG, "token removal failed", e)
                        }
                    }
                }
                launch {
                    try {
                        // A fresh token for the next sign-in, so the old one cannot deliver anything to this phone.
                        FirebaseMessaging.getInstance().deleteToken().await()
                    } catch (e: Exception) {
                        Log.w(TAG, "token delete failed", e)
                    }
                }
            }
        }
    }

    private fun functions() = FirebaseFunctions.getInstance(FUNCTIONS_REGION)
}
