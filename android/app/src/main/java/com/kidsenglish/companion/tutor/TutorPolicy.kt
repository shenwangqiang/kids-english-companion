package com.kidsenglish.companion.tutor

sealed interface PolicyDecision {
    data object Allowed : PolicyDecision
    data class Blocked(val message: String) : PolicyDecision
}

object TutorPolicy {
    const val SAFE_REDIRECT = "Let's stay with our story and learning activity."

    private val blockedPatterns = listOf(
        Regex("\\b(home )?address\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(phone|mobile|contact) number\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(my|your) school\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(send|share|show).{0,20}(photo|picture|secret)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(self[- ]?harm|suicide|kill myself)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(make|build).{0,20}(bomb|weapon)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(nude|sexual|porn)\\b", RegexOption.IGNORE_CASE),
    )

    fun validateInput(input: String): PolicyDecision {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > 500) return PolicyDecision.Blocked(SAFE_REDIRECT)
        if (blockedPatterns.any { it.containsMatchIn(trimmed) }) return PolicyDecision.Blocked(SAFE_REDIRECT)
        return PolicyDecision.Allowed
    }
}
