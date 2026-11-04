package com.kidsenglish.companion.conversation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.progress.ProgressDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConversationDatabaseTest {
    private lateinit var database: ProgressDatabase
    private lateinit var repository: ConversationRepository
    private var now = 100L
    private var nextId = 1

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ConversationRepository(
            database.conversationDao(),
            clock = { now++ },
            idFactory = { "session-${nextId++}" },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun keepsSameAgeSessionsIndependentAndRestoresStoredTopic() = runBlocking {
        val dragons = ConversationTopicCatalog.predefined.first()
        val custom = ConversationTopic("custom-ocean-world", "Ocean World")
        val first = repository.createSession(AgeBand.AGE_6_8, dragons)
        val second = repository.createSession(AgeBand.AGE_6_8, custom)
        assertEquals("session-1", first)
        assertEquals("session-2", second)
        repository.appendUserTurn(first, "I like dragons.")
        repository.applyOutcome(first, outcome("Dragons fly!", "Likes dragons.", "Flying", "dragons"))
        repository.appendUserTurn(second, "I like dolphins.")
        repository.applyOutcome(second, outcome("Dolphins swim!", "Likes dolphins.", "Swimming", "dolphins"))
        val reopened = ConversationRepository(database.conversationDao())
        val dragon = reopened.observe(first).first { it.turns.size == 2 }
        val ocean = reopened.observe(second).first { it.turns.size == 2 }
        assertEquals(first, dragon.sessionId)
        assertEquals(dragons, dragon.topic)
        assertEquals(listOf("I like dragons.", "Dragons fly!"), dragon.turns.map { it.content })
        assertEquals(listOf(ConversationRole.USER, ConversationRole.ASSISTANT), dragon.turns.map { it.role })
        assertEquals("Likes dragons.", dragon.memory.summary)
        assertEquals(listOf("dragons"), dragon.memory.rememberedFacts)
        assertEquals("Flying", dragon.memory.nextDirection)
        assertEquals(second, ocean.sessionId)
        assertEquals(custom, ocean.topic)
        assertEquals(listOf("I like dolphins.", "Dolphins swim!"), ocean.turns.map { it.content })
        assertEquals("Likes dolphins.", ocean.memory.summary)
        assertEquals(listOf("dolphins"), ocean.memory.rememberedFacts)
        assertEquals("Swimming", ocean.memory.nextDirection)
    }

    @Test
    fun historyOrdersByActivityAndShowsLatestPreviewWithinAgeBand() = runBlocking {
        val first = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        val second = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.predefined.first())
        repository.createSession(AgeBand.AGE_11_12, ConversationTopicCatalog.general)
        assertEquals(second, repository.latestSessionId(AgeBand.AGE_6_8))
        assertEquals(listOf(second, first), repository.observeHistory(AgeBand.AGE_6_8).first().map { it.sessionId })
        repository.appendUserTurn(first, "I want to talk about flying dragons")
        var history = repository.observeHistory(AgeBand.AGE_6_8).first()
        assertEquals(listOf(first, second), history.map { it.sessionId })
        assertEquals("I want to talk about flying dragons", history.first { it.sessionId == first }.title)
        assertEquals("I want to talk about flying dragons", history.first().preview)
        assertEquals(100L, history.first().createdAt)
        assertEquals(103L, history.first().updatedAt)
        assertEquals(AgeBand.AGE_6_8, history.first().ageBand)
        assertEquals(first, repository.latestSessionId(AgeBand.AGE_6_8))
        repository.applyOutcome(second, outcome("Let's fly!", "Flying", "More flying", "wings"))
        history = repository.observeHistory(AgeBand.AGE_6_8).first()
        assertEquals(listOf(second, first), history.map { it.sessionId })
        assertEquals("Let's fly!", history.first().preview)
        assertEquals(second, repository.latestSessionId(AgeBand.AGE_6_8))
    }

    @Test
    fun titleFallsBackToTopicThenUsesFirstNormalizedUserMessageOnly() = runBlocking {
        val topic = ConversationTopicCatalog.predefined.first()
        val id = repository.createSession(AgeBand.AGE_9_10, topic)
        assertEquals("", database.conversationDao().observeSession(id).first()!!.title)
        assertEquals(topic.title, repository.observeHistory(AgeBand.AGE_9_10).first().single().title)
        assertEquals("", repository.observeHistory(AgeBand.AGE_9_10).first().single().preview)
        repository.appendUserTurn(id, "  \n  ")
        assertEquals("", database.conversationDao().observeSession(id).first()!!.title)
        repository.appendUserTurn(id, "  I want\n to talk about flying dragons and their amazing adventures.  ")
        assertEquals("I want to talk about flying dragons and ", repository.observeHistory(AgeBand.AGE_9_10).first().single().title)
        repository.appendUserTurn(id, "A different title should not replace the first.")
        assertEquals("I want to talk about flying dragons and ", repository.observeHistory(AgeBand.AGE_9_10).first().single().title)
        repository.appendUserTurn(id, "x".repeat(501))
        assertEquals(500, repository.observe(id).first { it.turns.size == 3 }.turns.last().content.length)
    }

    @Test
    fun clearDeletesOnlySelectedSessionAndItsTurns() = runBlocking {
        assertNull(repository.latestSessionId(AgeBand.AGE_6_8))
        assertFalse(repository.sessionExists("missing"))
        val first = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        val second = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        repository.appendUserTurn(first, "Delete me.")
        repository.appendUserTurn(second, "Keep me.")
        repository.clear(first)
        assertFalse(repository.sessionExists(first))
        assertTrue(repository.sessionExists(second))
        assertTrue(repository.observe(first).first().turns.isEmpty())
        assertEquals(listOf("Keep me."), repository.observe(second).first().turns.map { it.content })
        assertEquals(listOf(second), repository.observeHistory(AgeBand.AGE_6_8).first().map { it.sessionId })
    }

    private fun outcome(reply: String, summary: String, next: String, fact: String) = ConversationOutcome(
        reply = reply,
        conversationSummary = summary,
        nextDirection = next,
        rememberedFacts = listOf(fact),
    )
}
