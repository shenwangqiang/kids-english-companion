package com.kidsenglish.companion.content

import com.kidsenglish.companion.model.AgeBand
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoryCatalogTest {
    private lateinit var repository: StoryRepository

    @Before
    fun setUp() {
        val assetRoot = File("src/main/assets/stories")
        repository = JsonStoryRepository { name -> File(assetRoot, name).readText() }
    }

    @Test
    fun catalogContainsSixValidStoriesPerBand() {
        AgeBand.entries.forEach { band ->
            val stories = repository.stories(band)
            assertEquals(6, stories.size)
            assertTrue(stories.all { it.paragraphs.isNotEmpty() })
            assertTrue(stories.all { it.vocabulary.size >= 5 })
            assertTrue(stories.all { it.questions.size == 3 })
        }
    }

    @Test
    fun storyIdsAreUniqueAndSentencesAreSpeakable() {
        val stories = AgeBand.entries.flatMap(repository::stories)
        assertEquals(18, stories.map { it.id }.distinct().size)
        assertTrue(
            stories.flatMap { it.paragraphs }
                .flatMap { it.sentences }
                .all { it.text.isNotBlank() && it.text.length <= 220 },
        )
    }

    @Test
    fun everyQuestionIsGroundedInItsStory() {
        val stories = AgeBand.entries.flatMap(repository::stories)
        stories.forEach { story ->
            val storyText = story.paragraphs.flatMap { it.sentences }.joinToString(" ") { it.text }.lowercase()
            story.questions.forEach { question ->
                assertTrue(
                    "${story.id}/${question.id} must include at least one answer concept",
                    question.answerConcepts.any { it.lowercase() in storyText },
                )
            }
        }
    }
}
