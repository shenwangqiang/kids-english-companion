package com.kidsenglish.companion.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** The platform engine boundary; lifecycle and utterance ownership remain in the controller. */
internal interface TtsOutput {
    fun configureEnglish(): Boolean
    fun setListener(listener: UtteranceProgressListener)
    fun speak(text: String, rate: Float, pitch: Float, utteranceId: String): Boolean
    fun stop()
    fun shutdown()
}

internal class AndroidTtsOutput(context: Context, engine: String?, onInit: (Int) -> Unit) : TtsOutput {
    private val tts = if (engine == null) TextToSpeech(context, onInit) else TextToSpeech(context, onInit, engine)
    override fun configureEnglish() = tts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE
    override fun setListener(listener: UtteranceProgressListener) { tts.setOnUtteranceProgressListener(listener) }
    override fun speak(text: String, rate: Float, pitch: Float, utteranceId: String): Boolean {
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
    }
    override fun stop() { tts.stop() }
    override fun shutdown() { tts.shutdown() }
}
