package com.kidsenglish.companion.conversation

import com.kidsenglish.companion.model.AgeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPromptTest {
    @Test
    fun includesDurableMemoryAndOnlyTheLatestEightTurns() {
        val history = (0 until 14).map { index ->
            ConversationTurn(
                role = if (index % 2 == 0) ConversationRole.USER else ConversationRole.ASSISTANT,
                content = "turn-$index",
            )
        }
        val request = ConversationRequest(
            ageBand = AgeBand.AGE_9_10,
            childMessage = "What should we talk about?",
            history = history,
            memory = ConversationMemory(
                summary = "The child likes ocean animals.",
                rememberedFacts = listOf("The child likes dolphins."),
                nextDirection = "Practice describing a beach memory.",
            ),
        )

        val messages = ConversationPrompt.messages(request)

        assertEquals(10, messages.size)
        assertEquals(ConversationRole.SYSTEM, messages.first().role)
        assertTrue(messages.first().content.contains("The child likes ocean animals."))
        assertTrue(messages.first().content.contains("The child likes dolphins."))
        assertTrue(messages.first().content.contains("Practice describing a beach memory."))
        assertFalse(messages.any { it.content == "turn-0" })
        assertFalse(messages.any { it.content == "turn-5" })
        assertEquals("turn-6", messages[1].content)
        assertEquals("What should we talk about?", messages.last().content)
    }

    @Test
    fun appliesTheYoungestAgeReplyLimit() {
        val messages = ConversationPrompt.messages(
            ConversationRequest(AgeBand.AGE_6_8, "Hello"),
        )

        assertTrue(messages.first().content.contains("no more than 30 English words"))
        assertTrue(messages.first().content.contains("ask exactly one"))
        assertTrue(messages.first().content.contains("Return JSON only"))
    }

    @Test
    fun parsesACompleteStructuredOutcome() {
        val content = """
            {
              "reply": "Dolphins are clever! What do they like to eat?",
              "conversationSummary": "The child likes dolphins because they are smart.",
              "nextDirection": "Discuss dolphin food using simple nouns.",
              "rememberedFacts": ["The child likes dolphins."]
            }
        """.trimIndent()

        val outcome = ConversationOutcomeParser.parse(content)

        assertEquals("Dolphins are clever! What do they like to eat?", outcome?.reply)
        assertEquals("The child likes dolphins because they are smart.", outcome?.conversationSummary)
        assertEquals(listOf("The child likes dolphins."), outcome?.rememberedFacts)
        assertEquals("Discuss dolphin food using simple nouns.", outcome?.nextDirection)
    }

    @Test
    fun rejectsMalformedOrEmptyReplies() {
        assertNull(ConversationOutcomeParser.parse("not-json"))
        assertNull(
            ConversationOutcomeParser.parse(
                """{"reply":"  ","conversationSummary":"summary","nextDirection":"next","rememberedFacts":[]}""",
            ),
        )
    }
}
