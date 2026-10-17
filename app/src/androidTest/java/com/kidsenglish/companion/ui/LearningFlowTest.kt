package com.kidsenglish.companion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import com.kidsenglish.companion.KidsEnglishApplication
import com.kidsenglish.companion.MainActivity
import com.kidsenglish.companion.conversation.ConversationTopicCatalog
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.settings.AppSettingsRepository
import com.kidsenglish.companion.speech.TtsPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LearningFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun resetModelConnection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            AppSettingsRepository(context).setBaseUrl("")
            AppSettingsRepository(context).setAllowDebugHttp(false)
            AppSettingsRepository(context).setModelName("qwen3.5:9b")
            AppSettingsRepository(context).setTtsPreference(TtsPreference.AUTO)
            val repository = (context.applicationContext as KidsEnglishApplication).container.conversationRepository
            repository.observeHistory(AgeBand.AGE_6_8).first().forEach { repository.clear(it.sessionId) }
            val sessionId = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
            AppSettingsRepository(context).setActiveSessionId(AgeBand.AGE_6_8, sessionId)
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test fun chooseBandOpensConversationFirst() {
        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithText("开始对话").assertIsDisplayed()
        compose.onNodeWithText("输入英语消息").assertIsDisplayed()
        compose.onNodeWithText("对话记忆").assertIsDisplayed()
        compose.onNodeWithText("英语故事").assertIsDisplayed()
    }

    @Test fun conversationUnavailableStillAllowsStoriesAndPractice() {
        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithText("对话服务当前不可用").assertIsDisplayed()
        compose.onNodeWithText("英语故事").performClick()
        compose.onNodeWithText("Mia and the Red Kite").performClick()
        compose.onNodeWithText("练习这个句子").performClick()
        compose.onNodeWithText("按下开始朗读").assertIsDisplayed()
    }

    @Test fun longConversationOpensAtLatestMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val repository = (context.applicationContext as KidsEnglishApplication).container.conversationRepository
            val sessionId = AppSettingsRepository(context).activeSessionId(AgeBand.AGE_6_8).first()!!
            repeat(14) { index -> repository.appendUserTurn(sessionId, "History message ${index + 1}") }
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText("6–8 岁").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("History message 14").assertIsDisplayed()
    }

    @Test fun unavailableTutorDoesNotBlockReading() {
        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithText("英语故事").performClick()
        compose.onNodeWithText("Mia and the Red Kite").performClick()
        compose.onNodeWithText("询问故事内容").performClick()
        compose.onNodeWithText("AI 辅导暂不可用").assertIsDisplayed()
    }

    @Test fun settingsAllowModelRefreshAndPersistTtsEngineSelection() {
        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("刷新模型列表").assertIsDisplayed()
        compose.onNodeWithText("qwen3.5:9b").assertIsDisplayed()

        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("朗读引擎"))
        compose.onNodeWithText("自动（Google → 讯飞 → 系统默认）").performClick()
        compose.onNodeWithText("Google").performClick()
        compose.waitForIdle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = runBlocking {
            withTimeout(10_000) {
                AppSettingsRepository(context).settings.first { it.ttsPreference == TtsPreference.GOOGLE }.ttsPreference
            }
        }
        assertEquals(TtsPreference.GOOGLE, saved)
    }

    @Test fun voiceConversationCanStartAndStopExplicitly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runBlocking {
            AppSettingsRepository(context).setBaseUrl("http://127.0.0.1:11434")
            AppSettingsRepository(context).setAllowDebugHttp(true)
        }
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithText("开始语音对话").performClick()
        compose.onNodeWithText("结束语音对话").assertIsDisplayed().performClick()
        compose.onNodeWithText("开始语音对话").assertIsDisplayed()
    }
}
