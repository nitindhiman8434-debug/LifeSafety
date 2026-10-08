package com.lifesafety.driversafety.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A message that is either a string resource (translated) or raw text (for example an error from the server).
 * ViewModels produce UiText; screens turn it into a String with asString().
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Raw -> text
}
