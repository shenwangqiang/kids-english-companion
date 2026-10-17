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
import org.junit.Before
import org.junit.Test

class ConversationDatabaseTest {
    private lateinit var database: ProgressDatabase
    private lateinit var repository: ConversationRepository
    private var now = 100L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ConversationRepository(database.conversationDao()) { now++ }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun persistsOrderedTurnsAndAppliesMemoryWithTheAssistantReply() = runBlocking {
        repository.appendUserTurn(AgeBand.AGE_9_10, "I like dolphins.")
        repository.applyOutcome(
            AgeBand.AGE_9_10,
            ConversationOutcome(
                reply = "Dolphins are clever! What do they eat?",
                conversationSummary = "The child likes dolphins.",
                nextDirection = "Discuss ocean food.",
                rememberedFacts = listOf("The child likes dolphins."),
            ),
        )

        val snapshot = repository.observe(AgeBand.AGE_9_10).first { it.turns.size == 2 }

        assertEquals(listOf(ConversationRole.USER, ConversationRole.ASSISTANT), snapshot.turns.map { it.role })
        assertEquals("I like dolphins.", snapshot.turns[0].content)
        assertEquals("Dolphins are clever! What do they eat?", snapshot.turns[1].content)
        assertEquals("The child likes dolphins.", snapshot.memory.summary)
        assertEquals(listOf("The child likes dolphins."), snapshot.memory.rememberedFacts)
        assertEquals("Discuss ocean food.", snapshot.memory.nextDirection)
    }

    @Test
    fun keepsAgeBandsIsolatedAndClearsOnlyTheSelectedConversation() = runBlocking {
        repository.appendUserTurn(AgeBand.AGE_6_8, "I like cats.")
        repository.appendUserTurn(AgeBand.AGE_11_12, "I enjoy science.")

        repository.clear(AgeBand.AGE_6_8)

        val young = repository.observe(AgeBand.AGE_6_8).first()
        val older = repository.observe(AgeBand.AGE_11_12).first { it.turns.isNotEmpty() }
        assertTrue(young.turns.isEmpty())
        assertTrue(young.memory.summary.isEmpty())
        assertEquals(listOf("I enjoy science."), older.turns.map { it.content })
    }
}
