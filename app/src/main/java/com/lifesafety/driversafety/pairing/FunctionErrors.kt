package com.lifesafety.driversafety.pairing

import com.google.firebase.functions.FirebaseFunctionsException
import com.lifesafety.driversafety.R
import com.lifesafety.driversafety.ui.UiText

/**
 * Turns an error from a Cloud Function into a translated message.
 * The functions attach a short "key" (see fail() in functions/src/index.ts); each key maps to a string resource here.
 */
object FunctionErrors {

    private val byKey = mapOf(
        "wrong_code" to R.string.error_wrong_code,
        "code_used" to R.string.error_code_used,
        "code_expired" to R.string.error_code_expired,
        "code_invalid_now" to R.string.error_code_invalid_now,
        "invalid_code_format" to R.string.error_invalid_code_format,
        "already_linked" to R.string.error_already_linked,
        "too_many_attempts" to R.string.error_too_many_attempts,
        "not_admin" to R.string.error_not_admin,
        "not_driver" to R.string.error_not_driver,
        "not_primary" to R.string.error_not_primary,
        "has_secondary" to R.string.error_has_secondary,
        "no_secondary" to R.string.error_no_secondary,
        "not_linked" to R.string.error_not_linked,
        "nothing_pending" to R.string.error_nothing_pending,
        "remove_links_first" to R.string.error_remove_links_first,
        "own_code" to R.string.error_own_code,
        "sign_in" to R.string.error_sign_in_again,
        "invalid_settings" to R.string.error_invalid_settings,
        "invalid_phone" to R.string.error_invalid_phone,
        "already_on_trip" to R.string.error_already_on_trip,
        "no_active_trip" to R.string.error_no_active_trip,
        "no_role" to R.string.error_sign_in_again
    )

    fun toUiText(error: Throwable): UiText {
        val functionsError = error as? FirebaseFunctionsException
            ?: return UiText.Res(R.string.error_generic, listOf(error.message ?: error.javaClass.simpleName))
        val key = (functionsError.details as? Map<*, *>)?.get("key") as? String
        key?.let { byKey[it] }?.let { return UiText.Res(it) }
        return when (functionsError.code) {
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> UiText.Res(R.string.error_network)
            // A NOT_FOUND without our key means the function itself is missing on the server.
            FirebaseFunctionsException.Code.NOT_FOUND -> UiText.Res(R.string.error_functions_not_deployed)
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> UiText.Res(R.string.error_sign_in_again)
            else -> UiText.Res(R.string.error_generic, listOf(functionsError.message ?: functionsError.code.name))
        }
    }
}
