package com.kidsenglish.companion.app

import android.content.Context
import androidx.room.Room
import com.kidsenglish.companion.BuildConfig
import com.kidsenglish.companion.BuildContract
import com.kidsenglish.companion.content.AssetStoryRepository
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
    private val database = Room.databaseBuilder(context, ProgressDatabase::class.java, BuildContract.DATABASE_NAME).build()
    val progressRepository = ProgressRepository(database.progressDao())
    val speechController = AndroidSpeechController(context)
    private val passageRetriever = PassageRetriever()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
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
}
