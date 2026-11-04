package com.kidsenglish.companion.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VoiceSessionTest {
    @Test fun followsHandsFreeLoop() {
        var state = VoiceSessionReducer.reduce(VoiceSessionState(), VoiceEvent.Start)
        assertEquals(VoicePhase.LISTENING, state.phase)

        state = VoiceSessionReducer.reduce(state, VoiceEvent.Heard("I like space"))
        assertEquals(VoicePhase.THINKING, state.phase)
        assertEquals("I like space", state.recognizedText)

        state = VoiceSessionReducer.reduce(state, VoiceEvent.ReplyReady("Tell me about Mars."))
        assertEquals(VoicePhase.SPEAKING, state.phase)
        assertEquals("Tell me about Mars.", state.pendingReply)

        state = VoiceSessionReducer.reduce(state, VoiceEvent.SpeechFinished)
        assertEquals(VoicePhase.LISTENING, state.phase)
    }

    @Test fun errorCanRetryAndStopAlwaysReturnsIdle() {
        val failed = VoiceSessionReducer.reduce(
            VoiceSessionState(VoicePhase.LISTENING),
            VoiceEvent.Failure("I could not hear you."),
        )
        assertEquals(VoicePhase.ERROR, failed.phase)
        assertEquals("I could not hear you.", failed.message)
        assertEquals(VoicePhase.LISTENING, VoiceSessionReducer.reduce(failed, VoiceEvent.Retry).phase)

        VoicePhase.entries.filter { it != VoicePhase.IDLE }.forEach { phase ->
            val stopped = VoiceSessionReducer.reduce(VoiceSessionState(phase), VoiceEvent.Stop)
            assertEquals(VoicePhase.IDLE, stopped.phase)
            assertFalse(stopped.isActive)
        }
    }
}
