package com.kidsenglish.companion.conversation

import com.kidsenglish.companion.model.AgeBand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ConversationRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
}

@Serializable
data class ConversationTurn(
    val role: ConversationRole,
    val content: String,
)

data class ConversationMemory(
    val summary: String = "",
    val rememberedFacts: List<String> = emptyList(),
    val nextDirection: String = "",
)

data class ConversationRequest(
    val ageBand: AgeBand,
    val childMessage: String,
    val history: List<ConversationTurn> = emptyList(),
    val memory: ConversationMemory = ConversationMemory(),
)

@Serializable
data class ConversationOutcome(
    val reply: String,
    val conversationSummary: String,
    val nextDirection: String,
    val rememberedFacts: List<String>,
)

object ConversationOutcomeParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(content: String): ConversationOutcome? {
        val decoded = runCatching { json.decodeFromString<ConversationOutcome>(content.trim()) }.getOrNull()
            ?: return null
        val reply = decoded.reply.trim().take(MAX_REPLY_CHARS)
        if (reply.isBlank()) return null
        return decoded.copy(
            reply = reply,
            conversationSummary = decoded.conversationSummary.trim().take(MAX_SUMMARY_CHARS),
            nextDirection = decoded.nextDirection.trim().take(MAX_DIRECTION_CHARS),
            rememberedFacts = decoded.rememberedFacts
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(MAX_FACTS),
        )
    }

    private const val MAX_REPLY_CHARS = 2_000
    private const val MAX_SUMMARY_CHARS = 1_500
    private const val MAX_DIRECTION_CHARS = 500
    private const val MAX_FACTS = 20
}
