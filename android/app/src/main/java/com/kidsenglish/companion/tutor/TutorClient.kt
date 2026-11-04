package com.kidsenglish.companion.tutor

import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.model.Story
import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole(val wireValue: String) { SYSTEM("system"), USER("user"), ASSISTANT("assistant") }

@Serializable
data class ChatMessage(val role: ChatRole, val content: String)

data class TutorRequest(
    val ageBand: AgeBand,
    val story: Story,
    val currentParagraphId: String?,
    val childMessage: String,
    val history: List<ChatMessage> = emptyList(),
)

sealed interface TutorResult {
    data class Success(val text: String) : TutorResult
    data class AuthenticationRequired(val message: String = "Model authentication is required.") : TutorResult
    data class Unavailable(val message: String = "AI tutor is offline") : TutorResult
    data class InvalidResponse(val message: String = "The AI tutor returned an invalid response.") : TutorResult
    data class Blocked(val message: String = TutorPolicy.SAFE_REDIRECT) : TutorResult
}

interface TutorClient {
    suspend fun chat(request: TutorRequest): TutorResult
    suspend fun connectionTest(): TutorResult
}
