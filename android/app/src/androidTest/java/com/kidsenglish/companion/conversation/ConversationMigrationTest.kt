package com.kidsenglish.companion.conversation

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kidsenglish.companion.app.AppContainer
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.progress.ProgressDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMigrationTest {
    @Test
    fun migratesVersionTwoAdditivelyAndPreservesLegacySessionAndLearningData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "conversation-migration-${UUID.randomUUID()}.db"
        var database: ProgressDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { legacy ->
                legacy.execSQL(
                    "CREATE TABLE learning_progress (storyId TEXT NOT NULL PRIMARY KEY, " +
                        "openedAt INTEGER NOT NULL, completedAt INTEGER, speakingAttempts INTEGER NOT NULL, " +
                        "bestSpeakingScore INTEGER NOT NULL)",
                )
                legacy.execSQL(
                    "CREATE TABLE conversation_sessions (sessionId TEXT NOT NULL PRIMARY KEY, " +
                        "ageBand TEXT NOT NULL, summary TEXT NOT NULL, rememberedFactsJson TEXT NOT NULL, " +
                        "nextDirection TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                )
                legacy.execSQL(
                    "CREATE TABLE conversation_turns (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sessionId TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES " +
                        "conversation_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                legacy.execSQL("CREATE INDEX index_conversation_turns_sessionId ON conversation_turns(sessionId)")
                legacy.execSQL("INSERT INTO learning_progress VALUES ('dragon-story', 10, 20, 3, 95)")
                legacy.execSQL(
                    "INSERT INTO conversation_sessions VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>("conversation-6-8", "6-8", "The child likes dragons.", "[\"Likes dragons\"]", "Discuss flying.", 100L, 200L),
                )
                legacy.execSQL(
                    "INSERT INTO conversation_turns VALUES (7, 'conversation-6-8', 'user', 'I like dragons.', 150)",
                )
                legacy.version = 2
            }

            val migratedDatabase = Room.databaseBuilder(context, ProgressDatabase::class.java, databaseName)
                .addMigrations(AppContainer.MIGRATION_2_3)
                .build()
            database = migratedDatabase
            assertEquals(3, migratedDatabase.openHelper.writableDatabase.version)
            val migrated = migratedDatabase.conversationDao().observeSession("conversation-6-8").first()!!
            assertEquals("general", migrated.topicId)
            assertEquals("General Conversation", migrated.topicTitle)
            assertEquals("", migrated.title)
            assertEquals("conversation-6-8", migrated.sessionId)
            assertEquals(100L, migrated.createdAt)
            assertEquals(200L, migrated.updatedAt)
            val repository = ConversationRepository(migratedDatabase.conversationDao())
            val snapshot = repository.observe("conversation-6-8").first { it.turns.isNotEmpty() }
            assertEquals("I like dragons.", snapshot.turns.single().content)
            assertEquals("conversation-6-8", snapshot.sessionId)
            assertEquals(ConversationTopicCatalog.general, snapshot.topic)
            assertEquals("The child likes dragons.", snapshot.memory.summary)
            assertEquals(listOf("Likes dragons"), snapshot.memory.rememberedFacts)
            assertEquals("Discuss flying.", snapshot.memory.nextDirection)
            val turn = migratedDatabase.conversationDao().observeTurns("conversation-6-8").first().single()
            assertEquals(7L, turn.id)
            assertEquals(150L, turn.createdAt)
            assertTrue(repository.sessionExists("conversation-6-8"))
            assertEquals("conversation-6-8", repository.latestSessionId(AgeBand.AGE_6_8))
            val history = repository.observeHistory(AgeBand.AGE_6_8).first().single()
            assertEquals("General Conversation", history.title)
            assertEquals("I like dragons.", history.preview)
            val progress = migratedDatabase.progressDao().observeAll().first().single()
            assertEquals("dragon-story", progress.storyId)
            assertEquals(10L, progress.openedAt)
            assertEquals(20L, progress.completedAt)
            assertEquals(3, progress.speakingAttempts)
            assertEquals(95, progress.bestSpeakingScore)
        } finally {
            database?.close()
            context.deleteDatabase(databaseName)
        }
    }
}
