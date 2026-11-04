package com.kidsenglish.companion.progress

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.kidsenglish.companion.conversation.ConversationDao
import com.kidsenglish.companion.conversation.ConversationSessionEntity
import com.kidsenglish.companion.conversation.ConversationTurnEntity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "learning_progress", primaryKeys = ["storyId"])
data class LearningProgressEntity(
    val storyId: String,
    val openedAt: Long,
    val completedAt: Long? = null,
    val speakingAttempts: Int = 0,
    val bestSpeakingScore: Int = 0,
)

@Dao
interface ProgressDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(progress: LearningProgressEntity)

    @Query("UPDATE learning_progress SET completedAt = :timestamp WHERE storyId = :storyId")
    suspend fun markCompleted(storyId: String, timestamp: Long)

    @Query("UPDATE learning_progress SET speakingAttempts = speakingAttempts + 1, bestSpeakingScore = CASE WHEN :score > bestSpeakingScore THEN :score ELSE bestSpeakingScore END WHERE storyId = :storyId")
    suspend fun recordAttempt(storyId: String, score: Int)

    @Query("SELECT * FROM learning_progress ORDER BY openedAt DESC")
    fun observeAll(): Flow<List<LearningProgressEntity>>
}

@Database(
    entities = [LearningProgressEntity::class, ConversationSessionEntity::class, ConversationTurnEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun conversationDao(): ConversationDao
}

class ProgressRepository(
    private val dao: ProgressDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<LearningProgressEntity>> = dao.observeAll()

    suspend fun markStoryOpened(storyId: String) {
        dao.insertIfMissing(LearningProgressEntity(storyId = storyId, openedAt = clock()))
    }

    suspend fun markStoryCompleted(storyId: String) {
        markStoryOpened(storyId)
        dao.markCompleted(storyId, clock())
    }

    suspend fun recordSpeakingAttempt(storyId: String, score: Int) {
        markStoryOpened(storyId)
        dao.recordAttempt(storyId, score.coerceIn(0, 100))
    }
}
