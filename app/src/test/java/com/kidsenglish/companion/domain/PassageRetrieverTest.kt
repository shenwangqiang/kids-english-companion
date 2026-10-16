package com.kidsenglish.companion.domain

import com.kidsenglish.companion.sampleStory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageRetrieverTest {
    @Test
    fun currentParagraphIsAlwaysIncludedFirst() {
        val result = PassageRetriever().retrieve(sampleStory(), "Who helps Mia?", "p2", 2)
        assertEquals("p2", result.first().id)
    }

    @Test
    fun relevantPassageWinsTokenOverlapRanking() {
        val result = PassageRetriever().retrieve(sampleStory(), "Where does the red kite land?", null, 1)
        assertEquals("p2", result.single().id)
    }

    @Test
    fun resultDoesNotRepeatCurrentParagraph() {
        val result = PassageRetriever().retrieve(sampleStory(), "tree", "p2", 3)
        assertEquals(result.map { it.id }.distinct(), result.map { it.id })
        assertTrue(result.size <= 3)
    }
}
