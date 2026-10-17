package com.kidsenglish.companion.conversation

import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.settings.EndpointPolicy
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OllamaConversationClient(
    private val httpClient: OkHttpClient,
    private val connectionProvider: suspend () -> ConversationConnection,
    private val isDebug: Boolean,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : ConversationClient {
    override suspend fun chat(request: ConversationRequest): ConversationResult {
        val messages = ConversationPrompt.messages(request)
        val first = send(messages, maxTokens(request.ageBand))
        if (first !is RawResult.Success) return first.toConversationResult()
        ConversationOutcomeParser.parse(first.content)?.let { return ConversationResult.Success(it) }

        val repairMessages = messages + listOf(
            ConversationTurn(ConversationRole.ASSISTANT, first.content.take(2_000)),
            ConversationTurn(
                ConversationRole.USER,
                "The previous response was invalid. Return JSON only with reply, conversationSummary, " +
                    "nextDirection, and rememberedFacts. Do not use markdown.",
            ),
        )
        return when (val repaired = send(repairMessages, maxTokens(request.ageBand))) {
            is RawResult.Success -> ConversationOutcomeParser.parse(repaired.content)
                ?.let(ConversationResult::Success)
                ?: ConversationResult.InvalidResponse()
            else -> repaired.toConversationResult()
        }
    }

    override suspend fun connectionTest(): ConversationResult {
        val result = send(
            messages = listOf(
                ConversationTurn(ConversationRole.USER, "Reply only with OK."),
            ),
            maxTokens = 32,
        )
        return when (result) {
            is RawResult.Success -> if (result.content.isBlank()) {
                ConversationResult.InvalidResponse()
            } else {
                ConversationResult.Success(
                    ConversationOutcome(
                        reply = result.content.trim(),
                        conversationSummary = "",
                        nextDirection = "",
                        rememberedFacts = emptyList(),
                    ),
                )
            }
            else -> result.toConversationResult()
        }
    }

    override suspend fun availableModels(): ModelDiscoveryResult = withContext(Dispatchers.IO) {
        val connection = connectionProvider()
        if (!EndpointPolicy.isAllowed(connection.baseUrl, isDebug, connection.allowDebugHttp)) {
            return@withContext ModelDiscoveryResult.Unavailable
        }
        val root = connection.baseUrl.trim().trimEnd('/').removeSuffix("/v1")
        val request = Request.Builder()
            .url("$root/api/tags")
            .get()
            .apply {
                if (connection.token.isNotBlank()) {
                    header("Authorization", "Bearer ${connection.token}")
                }
            }
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext ModelDiscoveryResult.Unavailable
                val tags = runCatching {
                    json.decodeFromString<OllamaTagsResponse>(response.body?.string().orEmpty())
                }.getOrNull() ?: return@withContext ModelDiscoveryResult.Unavailable
                ModelDiscoveryResult.Success(ModelSelection.ordered(tags.models.map { it.name }))
            }
        } catch (_: IOException) {
            ModelDiscoveryResult.Unavailable
        }
    }

    private suspend fun send(messages: List<ConversationTurn>, maxTokens: Int): RawResult = withContext(Dispatchers.IO) {
        val connection = connectionProvider()
        if (!EndpointPolicy.isAllowed(connection.baseUrl, isDebug, connection.allowDebugHttp)) {
            return@withContext RawResult.Unavailable
        }
        val root = connection.baseUrl.trim().trimEnd('/').removeSuffix("/v1")
        val requestBody = OllamaChatRequest(
            model = connection.modelName.ifBlank { DEFAULT_CONVERSATION_MODEL },
            messages = messages.map { OllamaMessage(it.role.wireValue, it.content) },
            options = OllamaOptions(numPredict = maxTokens),
        )
        val body = json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE)

        repeat(2) { attempt ->
            val request = Request.Builder()
                .url("$root/api/chat")
                .post(body)
                .header("Content-Type", "application/json")
                .apply {
                    if (connection.token.isNotBlank()) {
                        header("Authorization", "Bearer ${connection.token}")
                    }
                }
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401 || response.code == 403) return@withContext RawResult.AuthenticationRequired
                    if (response.code in RETRYABLE_CODES && attempt == 0) return@use
                    if (!response.isSuccessful) return@withContext RawResult.Unavailable
                    val responseBody = response.body?.string().orEmpty()
                    val content = runCatching {
                        json.decodeFromString<OllamaChatResponse>(responseBody).message?.content.orEmpty()
                    }.getOrDefault("")
                    return@withContext RawResult.Success(content)
                }
            } catch (_: IOException) {
                if (attempt == 1) return@withContext RawResult.Unavailable
            }
        }
        RawResult.Unavailable
    }

    private fun maxTokens(ageBand: AgeBand): Int = when (ageBand) {
        AgeBand.AGE_6_8 -> 180
        AgeBand.AGE_9_10 -> 240
        AgeBand.AGE_11_12 -> 300
    }

    private fun RawResult.toConversationResult(): ConversationResult = when (this) {
        is RawResult.Success -> ConversationResult.InvalidResponse()
        RawResult.AuthenticationRequired -> ConversationResult.AuthenticationRequired()
        RawResult.Unavailable -> ConversationResult.Unavailable()
    }

    private sealed interface RawResult {
        data class Success(val content: String) : RawResult
        data object AuthenticationRequired : RawResult
        data object Unavailable : RawResult
    }

    @Serializable
    private data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = false,
        val think: Boolean = false,
        @SerialName("keep_alive") val keepAlive: String = "30m",
        val options: OllamaOptions,
    )

    @Serializable
    private data class OllamaOptions(
        val temperature: Double = 0.2,
        @SerialName("num_predict") val numPredict: Int,
    )

    @Serializable
    private data class OllamaMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class OllamaChatResponse(
        val message: OllamaMessage? = null,
    )

    @Serializable
    private data class OllamaTagsResponse(
        val models: List<OllamaModel> = emptyList(),
    )

    @Serializable
    private data class OllamaModel(
        val name: String,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val RETRYABLE_CODES = setOf(502, 503, 504)
    }
}
