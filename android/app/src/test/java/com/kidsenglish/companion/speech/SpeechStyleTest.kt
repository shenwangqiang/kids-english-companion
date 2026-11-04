package com.kidsenglish.companion.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechStyleTest {
    @Test
    fun childConversationUsesABrighterPitchAndAvoidsSlowSpeech() {
        assertEquals(1.08f, SpeechStyle.CHILD_CONVERSATION.pitch)
        assertEquals(1.0f, SpeechStyle.CHILD_CONVERSATION.resolveRate(0.8f))
        assertEquals(1.1f, SpeechStyle.CHILD_CONVERSATION.resolveRate(1.1f))
    }

    @Test
    fun narrationKeepsTheSelectedReadingRateAndNaturalPitch() {
        assertEquals(1.0f, SpeechStyle.NARRATION.pitch)
        assertEquals(0.8f, SpeechStyle.NARRATION.resolveRate(0.8f))
    }
}
