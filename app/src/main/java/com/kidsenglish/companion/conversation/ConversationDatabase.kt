package com.kidsenglish.companion.conversation

import androidx.room.ColumnInfo
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    @ColumnInfo(defaultValue = "'general'") val topicId: String = "general",
    @ColumnInfo(defaultValue = "'General Conversation'") val topicTitle: String = "General Conversation",
    @ColumnInfo(defaultValue = "''") val title: String = "",
)

data class ConversationHistoryRow(
    val sessionId: String,
    val ageBand: String,
    val topicId: String,
    val topicTitle: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val preview: String?,
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

    @Query("""
        SELECT s.sessionId, s.ageBand, s.topicId, s.topicTitle, s.title,
               s.createdAt, s.updatedAt,
               (SELECT content FROM conversation_turns t
                WHERE t.sessionId = s.sessionId
                ORDER BY t.id DESC LIMIT 1) AS preview
        FROM conversation_sessions s
        WHERE s.ageBand = :ageBand
        ORDER BY s.updatedAt DESC
    """)
    abstract fun observeHistory(ageBand: String): Flow<List<ConversationHistoryRow>>

    @Query("SELECT sessionId FROM conversation_sessions WHERE ageBand = :ageBand ORDER BY updatedAt DESC LIMIT 1")
    abstract suspend fun latestSessionId(ageBand: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM conversation_sessions WHERE sessionId = :sessionId)")
    abstract suspend fun sessionExists(sessionId: String): Boolean

    @Query("UPDATE conversation_sessions SET title = :title WHERE sessionId = :sessionId AND title = ''")
    abstract suspend fun setTitleIfEmpty(sessionId: String, title: String)

    @Query("UPDATE conversation_sessions SET updatedAt = :timestamp WHERE sessionId = :sessionId")
    abstract suspend fun touchSession(sessionId: String, timestamp: Long)

    @Transaction
    open suspend fun appendUserTurn(turn: ConversationTurnEntity, title: String) {
        insertTurn(turn)
        setTitleIfEmpty(turn.sessionId, title)
        touchSession(turn.sessionId, turn.createdAt)
    }

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
    val sessionId: String,
    val topic: ConversationTopic,
    val turns: List<ConversationTurn> = emptyList(),
    val memory: ConversationMemory = ConversationMemory(),
)

data class ConversationSessionSummary(
    val sessionId: String,
    val ageBand: AgeBand,
    val topic: ConversationTopic,
    val title: String,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
)

class ConversationRepository(
    private val dao: ConversationDao,
    private val json: Json = Json,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun observe(sessionId: String): Flow<ConversationSnapshot> {
        return combine(dao.observeSession(sessionId), dao.observeTurns(sessionId)) { session, turns ->
            ConversationSnapshot(
                sessionId = session?.sessionId ?: sessionId,
                topic = session?.let { ConversationTopicCatalog.resolve(it.topicId, it.topicTitle) }
                    ?: ConversationTopicCatalog.general,
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

    fun observeHistory(ageBand: AgeBand): Flow<List<ConversationSessionSummary>> =
        dao.observeHistory(ageBand.wireValue).map { rows ->
            rows.map { row ->
                ConversationSessionSummary(
                    sessionId = row.sessionId,
                    ageBand = AgeBand.entries.first { it.wireValue == row.ageBand },
                    topic = ConversationTopicCatalog.resolve(row.topicId, row.topicTitle),
                    title = row.title.ifBlank { row.topicTitle },
                    preview = row.preview.orEmpty(),
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                )
            }
        }

    suspend fun createSession(ageBand: AgeBand, topic: ConversationTopic): String =
        insertSession(idFactory(), ageBand, topic)

    suspend fun latestSessionId(ageBand: AgeBand): String? = dao.latestSessionId(ageBand.wireValue)

    suspend fun sessionExists(sessionId: String): Boolean = dao.sessionExists(sessionId)

    suspend fun appendUserTurn(sessionId: String, content: String) {
        val normalized = content.trim().take(500)
        if (normalized.isBlank()) return
        val timestamp = clock()
        dao.appendUserTurn(
            ConversationTurnEntity(
                sessionId = sessionId,
                role = ConversationRole.USER.wireValue,
                content = normalized,
                createdAt = timestamp,
            ),
            title = normalized.replace(Regex("\\s+"), " ").take(40),
        )
    }

    suspend fun applyOutcome(sessionId: String, outcome: ConversationOutcome) {
        val timestamp = clock()
        dao.applyOutcome(
            sessionId = sessionId,
            reply = outcome.reply,
            summary = outcome.conversationSummary,
            factsJson = json.encodeToString(outcome.rememberedFacts),
            nextDirection = outcome.nextDirection,
            timestamp = timestamp,
        )
    }

    suspend fun clear(sessionId: String) {
        dao.deleteSession(sessionId)
    }

    private suspend fun insertSession(id: String, ageBand: AgeBand, topic: ConversationTopic): String {
        val timestamp = clock()
        dao.insertSession(
            ConversationSessionEntity(
                sessionId = id,
                ageBand = ageBand.wireValue,
                summary = "",
                rememberedFactsJson = "[]",
                nextDirection = "",
                createdAt = timestamp,
                updatedAt = timestamp,
                topicId = topic.id,
                topicTitle = topic.title,
                title = "",
            ),
        )
        return id
    }
}
