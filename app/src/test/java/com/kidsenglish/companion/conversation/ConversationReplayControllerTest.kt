package com.kidsenglish.companion.conversation

import com.kidsenglish.companion.speech.SpeechController
import com.kidsenglish.companion.speech.SpeechStyle
import com.kidsenglish.companion.speech.TtsPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationReplayControllerTest {
    private val speech = RecordingSpeechController()
    private val controller = ConversationReplayController(speech)

    @Test fun blankReplayDoesNotInterruptSpeech() {
        controller.replay(" \n\t ", rate = 0.9f)

        assertTrue(speech.calls.isEmpty())
    }

    @Test fun replayStopsCurrentSpeechAndUsesChildConversationStyle() {
        controller.replay("  Hello, explorer!  ", rate = 0.82f)

        assertEquals(listOf("stop", "speak"), speech.calls)
        assertEquals("Hello, explorer!", speech.lastText)
        assertEquals(0.82f, speech.lastRate)
        assertEquals(SpeechStyle.CHILD_CONVERSATION, speech.lastStyle)
    }

    @Test fun completingReplayDoesNotStartMicrophoneOrAnotherUtterance() {
        controller.replay("Your turn.", rate = 1.1f)
        speech.onDone()

        assertEquals(listOf("stop", "speak"), speech.calls)
    }

    @Test fun replayForwardsSpeechError() {
        val errors = mutableListOf<String>()
        controller.replay("Hello!", rate = 0.9f, onError = { message: String -> errors.add(message) })
        speech.onError("English voice is unavailable")

        assertEquals(listOf("English voice is unavailable"), errors)
        assertEquals(listOf("stop", "speak"), speech.calls)
    }

    @Test fun anotherTapStopsPreviousReplayBeforeSpeaking() {
        controller.replay("First message.", rate = 0.9f)
        controller.replay("Second message.", rate = 1.1f)

        assertEquals(listOf("stop", "speak", "stop", "speak"), speech.calls)
        assertEquals("Second message.", speech.lastText)
        assertEquals(1.1f, speech.lastRate)
    }

    private class RecordingSpeechController : SpeechController {
        val calls = mutableListOf<String>()
        var lastText = ""
        var lastRate = 0f
        var lastStyle: SpeechStyle? = null
        var onDone: () -> Unit = {}
        var onError: (String) -> Unit = {}

        override fun speak(text: String, rate: Float, style: SpeechStyle, onDone: () -> Unit, onError: (String) -> Unit) {
            calls += "speak"
            lastText = text
            lastRate = rate
            lastStyle = style
            this.onDone = onDone
            this.onError = onError
        }

        override fun stopSpeaking() { calls += "stop" }
        override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) { calls += "listen" }
        override fun stopListening() { calls += "stopListening" }
        override fun setTtsPreference(preference: TtsPreference) = Unit
        override fun shutdown() = Unit
    }
}
