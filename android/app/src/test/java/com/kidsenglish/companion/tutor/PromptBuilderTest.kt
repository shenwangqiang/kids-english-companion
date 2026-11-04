package com.kidsenglish.companion.tutor

import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.sampleStory
import com.kidsenglish.companion.domain.PassageRetriever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {
    @Test
    fun containsEvidenceAndAgeLimit() {
        val request = TutorRequest(
            ageBand = AgeBand.AGE_6_8,
            story = sampleStory(),
            currentParagraphId = "p2",
            childMessage = "Where is the kite?",
        )

        val messages = PromptBuilder(PassageRetriever()).messages(request)

        assertTrue(messages.first().content.contains("Use only the supplied story evidence"))
        assertTrue(messages.first().content.contains("6-8"))
        assertTrue(messages.first().content.contains("45 words"))
        assertTrue(messages.last().content.contains("Mia and the Red Kite"))
        assertTrue(messages.last().content.contains("tall tree"))
    }

    @Test
    fun capsHistoryAtSixMessages() {
        val history = (1..9).map { ChatMessage(if (it % 2 == 0) ChatRole.ASSISTANT else ChatRole.USER, "message $it") }
        val request = TutorRequest(AgeBand.AGE_9_10, sampleStory(), null, "What happened?", history)

        val messages = PromptBuilder(PassageRetriever()).messages(request)

        assertEquals(history.takeLast(6).map { it.content }, messages.drop(2).dropLast(1).map { it.content })
    }
}
