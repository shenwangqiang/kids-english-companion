package com.kidsenglish.companion.conversation

import com.kidsenglish.companion.speech.SpeechController
import com.kidsenglish.companion.speech.SpeechStyle

class ConversationReplayController(
    private val speechController: SpeechController,
) {
    fun replay(text: String, rate: Float, onError: (String) -> Unit = {}) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        speechController.stopSpeaking()
        speechController.speak(
            text = normalized,
            rate = rate,
            style = SpeechStyle.CHILD_CONVERSATION,
            onError = onError,
        )
    }
}
