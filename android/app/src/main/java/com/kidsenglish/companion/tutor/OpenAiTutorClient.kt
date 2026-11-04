package com.kidsenglish.companion.tutor

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

data class ModelConnection(
    val baseUrl: String,
    val modelName: String,
    val token: String,
    val allowDebugHttp: Boolean,
)

class OpenAiTutorClient(
    private val httpClient: OkHttpClient,
    private val promptBuilder: PromptBuilder,
    private val connectionProvider: suspend () -> ModelConnection,
    private val isDebug: Boolean,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TutorClient {
    override suspend fun chat(request: TutorRequest): TutorResult {
        val decision = TutorPolicy.validateInput(request.childMessage)
        if (decision is PolicyDecision.Blocked) return TutorResult.Blocked(decision.message)
        val maxTokens = when (request.ageBand) {
            AgeBand.AGE_6_8 -> 90
            AgeBand.AGE_9_10 -> 160
            AgeBand.AGE_11_12 -> 240
        }
        return send(promptBuilder.messages(request), maxTokens)
    }

    override suspend fun connectionTest(): TutorResult = send(
        messages = listOf(ChatMessage(ChatRole.USER, "Reply with OK.")),
        maxTokens = 4,
    )

    private suspend fun send(messages: List<ChatMessage>, maxTokens: Int): TutorResult = withContext(Dispatchers.IO) {
        val connection = connectionProvider()
        if (!EndpointPolicy.isAllowed(connection.baseUrl, isDebug, connection.allowDebugHttp)) {
            return@withContext TutorResult.Unavailable("Model endpoint is not allowed.")
        }
        val root = connection.baseUrl.trim().trimEnd('/').removeSuffix("/v1")
        val endpoint = "$root/v1/chat/completions"
        val payload = ChatCompletionRequest(
            model = connection.modelName.ifBlank { "qwen3:4b" },
            messages = messages.map { TransportMessage(it.role.wireValue, it.content) },
            // Qwen thinking variants spend completion tokens on hidden reasoning
            // before emitting message.content. Keep enough room for a final answer.
            maxTokens = maxTokens.coerceAtLeast(MIN_COMPLETION_TOKENS),
        )

        repeat(2) { attempt ->
            val request = Request.Builder()
                .url(endpoint)
                .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .apply {
                    if (connection.token.isNotBlank()) header("Authorization", "Bearer ${connection.token}")
                }
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 401 || response.code == 403) return@withContext TutorResult.AuthenticationRequired()
                    if (response.code in RETRYABLE_CODES && attempt == 0) return@use
                    if (!response.isSuccessful) return@withContext TutorResult.Unavailable()
                    val body = response.body?.string().orEmpty()
                    val content = runCatching {
                        json.decodeFromString<ChatCompletionResponse>(body)
                            .choices.firstOrNull()?.message?.content?.trim().orEmpty()
                    }.getOrDefault("")
                    if (content.isBlank()) return@withContext TutorResult.InvalidResponse()
                    return@withContext TutorResult.Success(content.take(2_000))
                }
            } catch (_: IOException) {
                if (attempt == 1) return@withContext TutorResult.Unavailable()
            }
        }
        TutorResult.Unavailable()
    }

    @Serializable
    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<TransportMessage>,
        val stream: Boolean = false,
        val temperature: Double = 0.3,
        @SerialName("max_tokens") val maxTokens: Int,
    )

    @Serializable
    private data class TransportMessage(val role: String, val content: String)

    @Serializable
    private data class ChatCompletionResponse(val choices: List<Choice> = emptyList())

    @Serializable
    private data class Choice(val message: TransportMessage)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val RETRYABLE_CODES = setOf(502, 503, 504)
        private const val MIN_COMPLETION_TOKENS = 512
    }
}
