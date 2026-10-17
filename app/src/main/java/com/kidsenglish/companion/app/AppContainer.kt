package com.kidsenglish.companion.app

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kidsenglish.companion.BuildConfig
import com.kidsenglish.companion.BuildContract
import com.kidsenglish.companion.content.AssetStoryRepository
import com.kidsenglish.companion.conversation.ConversationConnection
import com.kidsenglish.companion.conversation.ConversationRepository
import com.kidsenglish.companion.conversation.OllamaConversationClient
import com.kidsenglish.companion.domain.PassageRetriever
import com.kidsenglish.companion.progress.ProgressDatabase
import com.kidsenglish.companion.progress.ProgressRepository
import com.kidsenglish.companion.settings.AppSettingsRepository
import com.kidsenglish.companion.settings.SecureTokenStore
import com.kidsenglish.companion.speech.AndroidSpeechController
import com.kidsenglish.companion.tutor.ModelConnection
import com.kidsenglish.companion.tutor.OpenAiTutorClient
import com.kidsenglish.companion.tutor.PromptBuilder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    val storyRepository = AssetStoryRepository(context)
    val settingsRepository = AppSettingsRepository(context)
    val secureTokenStore = SecureTokenStore(context)
    private val database = Room.databaseBuilder(context, ProgressDatabase::class.java, BuildContract.DATABASE_NAME)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
    val progressRepository = ProgressRepository(database.progressDao())
    val conversationRepository = ConversationRepository(database.conversationDao())
    val speechController = AndroidSpeechController(context)
    private val passageRetriever = PassageRetriever()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    val tutorClient = OpenAiTutorClient(
        httpClient = httpClient,
        promptBuilder = PromptBuilder(passageRetriever),
        connectionProvider = {
            val settings = settingsRepository.settings.first()
            ModelConnection(settings.baseUrl, settings.modelName, secureTokenStore.read(), settings.allowDebugHttp)
        },
        isDebug = BuildConfig.DEBUG,
    )

    val conversationClient = OllamaConversationClient(
        httpClient = httpClient,
        connectionProvider = {
            val settings = settingsRepository.settings.first()
            ConversationConnection(
                baseUrl = settings.baseUrl,
                modelName = settings.modelName,
                token = secureTokenStore.read(),
                allowDebugHttp = settings.allowDebugHttp,
            )
        },
        isDebug = BuildConfig.DEBUG,
    )

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation_sessions ADD COLUMN topicId TEXT NOT NULL DEFAULT 'general'")
                db.execSQL("ALTER TABLE conversation_sessions ADD COLUMN topicTitle TEXT NOT NULL DEFAULT 'General Conversation'")
                db.execSQL("ALTER TABLE conversation_sessions ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_sessions` " +
                        "(`sessionId` TEXT NOT NULL, `ageBand` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                        "`rememberedFactsJson` TEXT NOT NULL, `nextDirection` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`sessionId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_turns` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `conversation_sessions`(`sessionId`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_conversation_turns_sessionId` " +
                        "ON `conversation_turns` (`sessionId`)",
                )
            }
        }
    }
}
