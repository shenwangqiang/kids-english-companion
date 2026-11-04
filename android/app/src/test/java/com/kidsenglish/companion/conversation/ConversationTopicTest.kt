package com.kidsenglish.companion.conversation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTopicTest {
    @Test
    fun exposesTheCuratedTopicsInDisplayOrder() {
        assertEquals(
            listOf("How to Train Your Dragon", "Harry Potter", "Space", "Dinosaurs", "Animals", "School Life"),
            ConversationTopicCatalog.predefined.map { it.title },
        )
    }

    @Test
    fun trimsAndBoundsCustomTopics() {
        assertEquals("Ocean robots", ConversationTopicCatalog.custom("  Ocean robots  ")?.title)
        assertNull(ConversationTopicCatalog.custom("   "))
        assertNull(ConversationTopicCatalog.custom("x".repeat(61)))
    }
}
