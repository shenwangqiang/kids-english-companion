package com.kidsenglish.companion.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsEngineSelectorTest {
    private val engines = listOf(
        TtsEngine("com.iflytek.speechsuite", "讯飞语音"),
        TtsEngine("com.google.android.tts", "Speech Recognition and Synthesis from Google"),
        TtsEngine("com.vendor.tts", "System speech"),
    )

    @Test
    fun automaticPrefersGoogleThenIflytekThenSystemDefault() {
        assertEquals(
            listOf("com.google.android.tts", "com.iflytek.speechsuite", null),
            TtsEngineSelector.candidates(TtsPreference.AUTO, engines),
        )
    }

    @Test
    fun explicitEngineStillFallsBackToSystemDefault() {
        assertEquals(
            listOf("com.iflytek.speechsuite", null),
            TtsEngineSelector.candidates(TtsPreference.IFLYTEK, engines),
        )
    }

    @Test
    fun recognizesPreferenceFromPersistedValue() {
        assertEquals(TtsPreference.GOOGLE, TtsPreference.fromWireValue("google"))
        assertEquals(TtsPreference.AUTO, TtsPreference.fromWireValue("unknown"))
    }
}
