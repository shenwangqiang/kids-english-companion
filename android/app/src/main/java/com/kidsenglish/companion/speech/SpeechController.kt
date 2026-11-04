package com.kidsenglish.companion.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.kidsenglish.companion.R
import java.util.Locale

interface SpeechController {
    fun speak(
        text: String,
        rate: Float,
        style: SpeechStyle = SpeechStyle.NARRATION,
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {},
    )
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit)
    fun stopListening()
    fun stopSpeaking()
    fun setTtsPreference(preference: TtsPreference)
    fun shutdown()
}

enum class TtsPreference(val wireValue: String) {
    AUTO("auto"),
    GOOGLE("google"),
    IFLYTEK("iflytek"),
    ;

    companion object {
        fun fromWireValue(value: String?): TtsPreference = entries.firstOrNull { it.wireValue == value } ?: AUTO
    }
}

data class TtsEngine(val packageName: String, val label: String)

object TtsEngineSelector {
    fun candidates(preference: TtsPreference, engines: List<TtsEngine>): List<String?> {
        val google = engines.firstOrNull { engine ->
            engine.packageName.contains("google", ignoreCase = true) ||
                engine.label.contains("google", ignoreCase = true)
        }?.packageName
        val iflytek = engines.firstOrNull { engine ->
            engine.packageName.contains("iflytek", ignoreCase = true) ||
                engine.label.contains("iflytek", ignoreCase = true) ||
                engine.label.contains("讯飞")
        }?.packageName
        val preferred = when (preference) {
            TtsPreference.AUTO -> listOfNotNull(google, iflytek)
            TtsPreference.GOOGLE -> listOfNotNull(google)
            TtsPreference.IFLYTEK -> listOfNotNull(iflytek)
        }
        return (preferred + listOf<String?>(null)).distinct()
    }
}

enum class SpeechStyle(val pitch: Float, private val minimumRate: Float) {
    NARRATION(pitch = 1.0f, minimumRate = 0.6f),
    CHILD_CONVERSATION(pitch = 1.08f, minimumRate = 1.0f),
    ;

    fun resolveRate(selectedRate: Float): Float = selectedRate.coerceIn(minimumRate, 1.2f)
}

class AndroidSpeechController internal constructor(
    private val context: Context,
    private val ttsFactory: (String?, (Int) -> Unit) -> TtsOutput,
    private val engineCatalog: (() -> List<TtsEngine>)? = null,
) : SpeechController {
    constructor(context: Context) : this(context, { engine, onInit -> AndroidTtsOutput(context, engine, onInit) })
    private val mainHandler = Handler(Looper.getMainLooper())
    private var ttsReady = false
    private var tts: TtsOutput? = null
    private val utteranceCallbacks = UtteranceCallbackRouter()
    private var ttsPreference: TtsPreference? = null
    private var initializationGeneration = 0

    init {
        setTtsPreference(TtsPreference.AUTO)
    }
    private var recognizer: SpeechRecognizer? = null

    override fun speak(
        text: String,
        rate: Float,
        style: SpeechStyle,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val currentTts = tts
        if (!ttsReady || currentTts == null || text.isBlank()) {
            onError(context.getString(R.string.tts_not_ready))
            return
        }
        stopSpeaking()
        val utteranceId = utteranceCallbacks.register(onDone, onError)
        if (!currentTts.speak(text, style.resolveRate(rate), style.pitch, utteranceId)) {
            utteranceCallbacks.onError(utteranceId, context.getString(R.string.tts_stopped))
        }
    }

    override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val reportError = onError
        stopListening()
        val speechRecognizer = createSpeechRecognizer()
        if (speechRecognizer == null) {
            reportError(context.getString(R.string.speech_not_available))
            return
        }
        recognizer = speechRecognizer.also {
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) reportError(context.getString(R.string.speech_no_match)) else onResult(text)
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

    private fun createSpeechRecognizer(): SpeechRecognizer? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .getOrNull()
                ?.let { return it }
        }

        @Suppress("DEPRECATION")
        val service = context.packageManager
            .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), PackageManager.MATCH_ALL)
            .asSequence()
            .mapNotNull { it.serviceInfo }
            .firstOrNull { it.enabled && it.applicationInfo.enabled }

        if (service != null) {
            val component = ComponentName(service.packageName, service.name)
            runCatching { SpeechRecognizer.createSpeechRecognizer(context, component) }
                .getOrNull()
                ?.let { return it }
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        return runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
    }

    override fun stopListening() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    override fun stopSpeaking() {
        utteranceCallbacks.clear()
        tts?.stop()
    }

    override fun setTtsPreference(preference: TtsPreference) {
        if (ttsPreference == preference && tts != null) return
        ttsPreference = preference
        ttsReady = false
        initializationGeneration += 1
        val generation = initializationGeneration
        utteranceCallbacks.clear()
        tts?.shutdown()
        tts = null
        initializeCandidate(
            candidates = TtsEngineSelector.candidates(preference, engineCatalog?.invoke() ?: installedTtsEngines()),
            index = 0,
            generation = generation,
        )
    }

    private fun initializeCandidate(candidates: List<String?>, index: Int, generation: Int) {
        if (generation != initializationGeneration || index >= candidates.size) return
        val enginePackage = candidates[index]
        var candidate: TtsOutput? = null
        candidate = ttsFactory(enginePackage) { status ->
            // Serialize binder callbacks with shutdown/preferences and allow even synchronous factories.
            mainHandler.post {
                onTtsInitialized(candidate, status, candidates, index, generation)
            }
        }
        tts = candidate
    }

    private fun onTtsInitialized(
        candidate: TtsOutput?,
        status: Int,
        candidates: List<String?>,
        index: Int,
        generation: Int,
    ) {
        if (generation != initializationGeneration) {
            candidate?.shutdown()
            return
        }
        val readyTts = candidate
        if (status != TextToSpeech.SUCCESS || readyTts == null || !readyTts.configureEnglish()) {
            candidate?.shutdown()
            if (tts === candidate) tts = null
            initializeCandidate(candidates, index + 1, generation)
            return
        }
        tts = readyTts
        ttsReady = true
        readyTts.setListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = utteranceCallbacks.onDone(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = utteranceCallbacks.onError(utteranceId, context.getString(R.string.tts_stopped))
            override fun onError(utteranceId: String?, errorCode: Int) = utteranceCallbacks.onError(utteranceId, context.getString(R.string.tts_stopped))
        })
    }

    private fun installedTtsEngines(): List<TtsEngine> {
        val services = context.packageManager.queryIntentServices(
            Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
            PackageManager.MATCH_ALL,
        )
        return services.mapNotNull { service ->
            val info = service.serviceInfo ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(context.packageManager).toString() }.getOrDefault(info.packageName)
            TtsEngine(info.packageName, label)
        }
    }

    override fun shutdown() {
        initializationGeneration += 1
        ttsPreference = null
        stopListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun recognitionError(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.microphone_permission_required)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.speech_service_offline)
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.speech_no_match)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.speech_busy)
        SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.speech_cancelled)
        else -> context.getString(R.string.speech_stopped)
    }
}
