package com.kidsenglish.companion.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakingScoreTest {
    @Test
    fun showsMissedAndExtraWords() {
        val result = SpeakingScore.compare("The red kite flies high", "the kite flies high today")
        assertTrue("red" in result.missedWords)
        assertTrue("today" in result.extraWords)
        assertEquals(80, result.percent)
    }

    @Test
    fun ignoresCaseAndPunctuation() {
        val result = SpeakingScore.compare("Hello, Mia!", "hello mia")
        assertEquals(100, result.percent)
        assertTrue(result.missedWords.isEmpty())
        assertTrue(result.extraWords.isEmpty())
    }

    @Test
    fun emptyRecognitionScoresZero() {
        assertEquals(0, SpeakingScore.compare("Fly the kite", "").percent)
    }
}
