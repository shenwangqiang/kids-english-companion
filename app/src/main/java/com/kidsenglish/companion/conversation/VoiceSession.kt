package com.kidsenglish.companion.conversation

enum class VoicePhase { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val recognizedText: String = "",
    val pendingReply: String = "",
    val message: String = "",
) {
    val isActive: Boolean get() = phase != VoicePhase.IDLE
}

sealed interface VoiceEvent {
    data object Start : VoiceEvent
    data class Heard(val text: String) : VoiceEvent
    data class ReplyReady(val text: String) : VoiceEvent
    data object SpeechFinished : VoiceEvent
    data class Failure(val message: String) : VoiceEvent
    data object Retry : VoiceEvent
    data object Stop : VoiceEvent
}

object VoiceSessionReducer {
    fun reduce(state: VoiceSessionState, event: VoiceEvent): VoiceSessionState = when (event) {
        VoiceEvent.Start, VoiceEvent.Retry, VoiceEvent.SpeechFinished ->
            state.copy(phase = VoicePhase.LISTENING, message = "")
        is VoiceEvent.Heard -> state.copy(
            phase = VoicePhase.THINKING,
            recognizedText = event.text.trim(),
            pendingReply = "",
            message = "",
        )
        is VoiceEvent.ReplyReady -> state.copy(
            phase = VoicePhase.SPEAKING,
            pendingReply = event.text.trim(),
            message = "",
        )
        is VoiceEvent.Failure -> state.copy(phase = VoicePhase.ERROR, message = event.message)
        VoiceEvent.Stop -> VoiceSessionState()
    }
}
