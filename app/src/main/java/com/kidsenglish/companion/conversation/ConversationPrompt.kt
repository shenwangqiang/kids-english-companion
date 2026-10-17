package com.kidsenglish.companion.conversation

import com.kidsenglish.companion.model.AgeBand

object ConversationPrompt {
    fun messages(request: ConversationRequest): List<ConversationTurn> {
        val wordLimit = when (request.ageBand) {
            AgeBand.AGE_6_8 -> 30
            AgeBand.AGE_9_10 -> 50
            AgeBand.AGE_11_12 -> 75
        }
        val facts = request.memory.rememberedFacts
            .take(20)
            .joinToString(separator = "\n") { "- ${it.take(300)}" }
            .ifBlank { "- None yet" }
        val system = ConversationTurn(
            role = ConversationRole.SYSTEM,
            content = """
                You are a friendly English conversation coach for a child aged ${request.ageBand.wireValue}.
                Use age-appropriate English and no more than $wordLimit English words in reply.
                Briefly reflect the child's latest answer, then ask exactly one natural next question.
                Never ask for a real name, address, school, contact details, photos, location, or secrets.
                Do not invent facts about the child. Use the saved memory only when relevant.

                Saved conversation summary:
                ${request.memory.summary.take(1_500).ifBlank { "None yet" }}

                Remembered facts:
                $facts

                Planned direction:
                ${request.memory.nextDirection.take(500).ifBlank { "Continue a friendly age-appropriate conversation." }}

                Return JSON only, without markdown, with exactly these keys:
                reply (string), conversationSummary (string), nextDirection (string), rememberedFacts (array of strings).
            """.trimIndent(),
        )
        val recentHistory = request.history.takeLast(MAX_HISTORY_TURNS).map { turn ->
            turn.copy(content = turn.content.take(MAX_MESSAGE_CHARS))
        }
        val current = ConversationTurn(
            role = ConversationRole.USER,
            content = request.childMessage.trim().take(MAX_MESSAGE_CHARS),
        )
        return listOf(system) + recentHistory + current
    }

    private const val MAX_HISTORY_TURNS = 8
    private const val MAX_MESSAGE_CHARS = 500
}
