package com.lifesafety.driversafety.trip

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import com.lifesafety.driversafety.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The driver alarm: a beep every second on the ALARM audio stream (loud even when media volume is low)
 * plus the voice "Please slow down" / "Kripya speed kam karein" every few seconds.
 * The voice uses the phone's text-to-speech engine in the app language; if that language is not installed,
 * it falls back to English.
 */
class AlarmPlayer(private val context: Context, private val scope: CoroutineScope) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var voiceText: String = context.getString(R.string.alarm_voice)
    private var job: Job? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val engine = tts ?: return@TextToSpeech
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val wanted = Locale.forLanguageTag(context.getString(R.string.alarm_voice_locale))
            val result = engine.setLanguage(wanted)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
                voiceText = context.getString(R.string.alarm_voice_fallback)
            }
            ttsReady = true
        }
    }

    val isPlaying: Boolean get() = job?.isActive == true

    fun start() {
        if (isPlaying) return
        job = scope.launch {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            var tick = 0
            try {
                while (isActive) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                    if (tick % 4 == 0) speak()
                    tick++
                    delay(1_000L)
                }
            } finally {
                tone.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        tts?.stop()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun speak() {
        if (!ttsReady) return
        tts?.speak(voiceText, TextToSpeech.QUEUE_FLUSH, null, "slow-down")
    }
}
