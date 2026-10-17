package com.kidsenglish.companion.conversation

const val DEFAULT_CONVERSATION_MODEL = "qwen3.5:4b-mlx"

object ModelSelection {
    val fallbackModels = listOf(
        "qwen3.5:4b-mlx",
        "qwen3.5:9b",
        "smollm2:1.7b",
        "qwen3:4b",
    )

    fun ordered(models: List<String>): List<String> {
        val distinct = models.map(String::trim).filter(String::isNotBlank).distinct()
        val rank = fallbackModels.withIndex().associate { (index, name) -> name to index }
        return distinct.sortedWith(compareBy<String> { rank[it] ?: Int.MAX_VALUE }.thenBy { it })
    }

    fun resolveSelection(saved: String, installed: List<String>): String {
        val ordered = ordered(installed)
        return saved.takeIf { it in ordered } ?: ordered.firstOrNull() ?: saved.ifBlank { DEFAULT_CONVERSATION_MODEL }
    }
}

data class ConversationConnection(
    val baseUrl: String,
    val modelName: String,
    val token: String,
    val allowDebugHttp: Boolean,
)

sealed interface ConversationResult {
    data class Success(val outcome: ConversationOutcome) : ConversationResult
    data class AuthenticationRequired(val message: String = "Model authentication is required.") : ConversationResult
    data class Unavailable(val message: String = "Conversation service is offline.") : ConversationResult
    data class InvalidResponse(val message: String = "The conversation model returned an invalid response.") : ConversationResult
    data object Blocked : ConversationResult
}

sealed interface ModelDiscoveryResult {
    data class Success(val models: List<String>) : ModelDiscoveryResult
    data object Unavailable : ModelDiscoveryResult
}

interface ConversationClient {
    suspend fun chat(request: ConversationRequest): ConversationResult
    suspend fun connectionTest(): ConversationResult
    suspend fun availableModels(): ModelDiscoveryResult
}
