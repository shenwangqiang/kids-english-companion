package com.kidsenglish.companion.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

interface SpeechController {
    fun speak(text: String, rate: Float)
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
    fun shutdown()
}

class AndroidSpeechController(private val context: Context) : SpeechController {
    private var ttsReady = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
    }
    private var recognizer: SpeechRecognizer? = null

    override fun speak(text: String, rate: Float) {
        if (!ttsReady || text.isBlank()) return
        tts?.stop()
        tts?.setSpeechRate(rate.coerceIn(0.6f, 1.2f))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "story-speech")
    }

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val reportError = onError
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            reportError("English speech recognition is not available on this device.")
            return
        }
        stopListening()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { speechRecognizer ->
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) reportError("I could not hear the sentence. Try again.") else onResult(text)
                }
                override fun onError(error: Int) = reportError(recognitionError(error))
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speechRecognizer.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                },
            )
        }
    }

    override fun stopListening() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun shutdown() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private fun recognitionError(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech service is offline."
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I could not hear the sentence. Try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again."
        else -> "Speech recognition stopped. Try again."
    }
}
