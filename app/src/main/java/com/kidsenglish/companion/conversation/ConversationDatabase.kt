package com.kidsenglish.companion.conversation

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.kidsenglish.companion.model.AgeBand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val sessionId: String,
    val ageBand: String,
    val summary: String,
    val rememberedFactsJson: String,
    val nextDirection: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "conversation_turns",
    foreignKeys = [
        ForeignKey(
            entity = ConversationSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ConversationTurnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
)

@Dao
abstract class ConversationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSession(session: ConversationSessionEntity)

    @Insert
    abstract suspend fun insertTurn(turn: ConversationTurnEntity)

    @Query("SELECT * FROM conversation_sessions WHERE sessionId = :sessionId")
    abstract fun observeSession(sessionId: String): Flow<ConversationSessionEntity?>

    @Query("SELECT * FROM conversation_turns WHERE sessionId = :sessionId ORDER BY id ASC")
    abstract fun observeTurns(sessionId: String): Flow<List<ConversationTurnEntity>>

    @Query(
        "UPDATE conversation_sessions SET summary = :summary, rememberedFactsJson = :factsJson, " +
            "nextDirection = :nextDirection, updatedAt = :updatedAt WHERE sessionId = :sessionId",
    )
    abstract suspend fun updateMemory(
        sessionId: String,
        summary: String,
        factsJson: String,
        nextDirection: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM conversation_sessions WHERE sessionId = :sessionId")
    abstract suspend fun deleteSession(sessionId: String)

    @Transaction
    open suspend fun applyOutcome(
        sessionId: String,
        reply: String,
        summary: String,
        factsJson: String,
        nextDirection: String,
        timestamp: Long,
    ) {
        insertTurn(
            ConversationTurnEntity(
                sessionId = sessionId,
                role = ConversationRole.ASSISTANT.wireValue,
                content = reply,
                createdAt = timestamp,
            ),
        )
        updateMemory(sessionId, summary, factsJson, nextDirection, timestamp)
    }
}

data class ConversationSnapshot(
    val turns: List<ConversationTurn> = emptyList(),
    val memory: ConversationMemory = ConversationMemory(),
)

class ConversationRepository(
    private val dao: ConversationDao,
    private val json: Json = Json,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(ageBand: AgeBand): Flow<ConversationSnapshot> {
        val sessionId = sessionId(ageBand)
        return combine(dao.observeSession(sessionId), dao.observeTurns(sessionId)) { session, turns ->
            ConversationSnapshot(
                turns = turns.mapNotNull { entity ->
                    ConversationRole.entries.firstOrNull { it.wireValue == entity.role }
                        ?.let { ConversationTurn(it, entity.content) }
                },
                memory = session?.let {
                    ConversationMemory(
                        summary = it.summary,
                        rememberedFacts = runCatching {
                            json.decodeFromString<List<String>>(it.rememberedFactsJson)
                        }.getOrDefault(emptyList()),
                        nextDirection = it.nextDirection,
                    )
                } ?: ConversationMemory(),
            )
        }
    }

    suspend fun appendUserTurn(ageBand: AgeBand, content: String) {
        val normalized = content.trim().take(500)
        if (normalized.isBlank()) return
        val timestamp = clock()
        val sessionId = ensureSession(ageBand, timestamp)
        dao.insertTurn(
            ConversationTurnEntity(
                sessionId = sessionId,
                role = ConversationRole.USER.wireValue,
                content = normalized,
                createdAt = timestamp,
            ),
        )
    }

    suspend fun applyOutcome(ageBand: AgeBand, outcome: ConversationOutcome) {
        val timestamp = clock()
        val sessionId = ensureSession(ageBand, timestamp)
        dao.applyOutcome(
            sessionId = sessionId,
            reply = outcome.reply,
            summary = outcome.conversationSummary,
            factsJson = json.encodeToString(outcome.rememberedFacts),
            nextDirection = outcome.nextDirection,
            timestamp = timestamp,
        )
    }

    suspend fun clear(ageBand: AgeBand) {
        dao.deleteSession(sessionId(ageBand))
    }

    private suspend fun ensureSession(ageBand: AgeBand, timestamp: Long): String {
        val id = sessionId(ageBand)
        dao.insertSession(
            ConversationSessionEntity(
                sessionId = id,
                ageBand = ageBand.wireValue,
                summary = "",
                rememberedFactsJson = "[]",
                nextDirection = "",
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return id
    }

    private fun sessionId(ageBand: AgeBand): String = "conversation-${ageBand.wireValue}"
}
