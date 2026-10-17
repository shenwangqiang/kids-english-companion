package com.kidsenglish.companion.ui

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.kidsenglish.companion.KidsEnglishApplication
import com.kidsenglish.companion.MainActivity
import com.kidsenglish.companion.conversation.ConversationOutcome
import com.kidsenglish.companion.conversation.ConversationTopicCatalog
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.settings.AppSettingsRepository
import com.kidsenglish.companion.speech.TtsPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LearningFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mainActivityIsLockedToPortrait() {
        val info = compose.activity.packageManager.getActivityInfo(compose.activity.componentName, 0)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
    }

    @Before fun resetModelConnection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            AppSettingsRepository(context).setAgeBand(AgeBand.AGE_6_8)
            AppSettingsRepository(context).setBaseUrl("")
            AppSettingsRepository(context).setAllowDebugHttp(false)
            AppSettingsRepository(context).setModelName("qwen3.5:9b")
            AppSettingsRepository(context).setTtsPreference(TtsPreference.AUTO)
            AppSettingsRepository(context).setSpeechRate(0.9f)
            val repository = (context.applicationContext as KidsEnglishApplication).container.conversationRepository
            AgeBand.entries.forEach { band ->
                repository.observeHistory(band).first().forEach { repository.clear(it.sessionId) }
                AppSettingsRepository(context).setActiveSessionId(band, "missing-${band.wireValue}")
            }
            val sessionId = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
            AppSettingsRepository(context).setActiveSessionId(AgeBand.AGE_6_8, sessionId)
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test fun newConversationShowsCatalogAndOpensSelectedTopic() {
        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithText("新建对话").performClick()
        compose.onNodeWithText("选择今天想聊的话题").assertIsDisplayed()
        listOf("How to Train Your Dragon", "Harry Potter", "Space", "Dinosaurs", "Animals", "School Life", "自定义话题")
            .forEach { title ->
                compose.onNodeWithTag("topic_list").performScrollToNode(hasText(title))
                compose.onNodeWithText(title).assertIsDisplayed()
            }
        compose.onNodeWithTag("topic_list").performScrollToNode(hasText("Space"))
        compose.onNodeWithText("Space").performClick()
        compose.onNodeWithTag("conversation_topic").assertIsDisplayed()
        compose.onNodeWithText("Space").assertIsDisplayed()
        compose.onNodeWithText("输入英语消息").assertIsDisplayed()
    }

    @Test fun historyShowsNewestFirstAndReopensOnlySelectedSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val repository = (context.applicationContext as KidsEnglishApplication).container.conversationRepository
            val older = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.predefined[2])
            repository.appendUserTurn(older, "Older space conversation")
            repository.applyOutcome(older, ConversationOutcome("The Moon is near Earth.", "", "", emptyList()))
            delay(10)
            val newer = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.predefined[4])
            repository.appendUserTurn(newer, "Newer animal conversation")
            repository.applyOutcome(newer, ConversationOutcome("Otters can swim.", "", "", emptyList()))
            AppSettingsRepository(context).setActiveSessionId(AgeBand.AGE_6_8, newer)
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithTag("conversation_list").performScrollToNode(hasText("历史对话"))
        compose.onNodeWithText("历史对话").performClick()
        val newer = compose.onNodeWithText("Newer animal conversation").assertIsDisplayed().fetchSemanticsNode()
        val older = compose.onNodeWithText("Older space conversation").assertIsDisplayed().fetchSemanticsNode()
        assertTrue("Newest session must appear first", newer.boundsInRoot.top < older.boundsInRoot.top)
        compose.onNodeWithText("Space").assertIsDisplayed()
        compose.onNodeWithText("The Moon is near Earth.").assertIsDisplayed()
        compose.onNodeWithText("Older space conversation").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("conversation_list").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("The Moon is near Earth.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("The Moon is near Earth.").assertIsDisplayed()
        compose.onNodeWithText("Otters can swim.").assertDoesNotExist()
        compose.onNodeWithText("Newer animal conversation").assertDoesNotExist()
    }

    @Test fun customTopicRequiresNonblankInputAndBoundsTitleToSixtyCharacters() {
        compose.onNodeWithText("6–8 岁").performClick()
        compose.onNodeWithText("新建对话").performClick()
        compose.onNodeWithTag("topic_list").performScrollToNode(hasText("自定义话题"))
        compose.onNodeWithText("自定义话题").performClick()
        compose.onNodeWithTag("topic_list").performScrollToNode(hasText("开始聊这个话题"))
        compose.onNodeWithText("开始聊这个话题").assertIsNotEnabled()
        compose.onNodeWithTag("custom_topic_input").performTextInput("   ")
        compose.onNodeWithText("开始聊这个话题").assertIsNotEnabled()
        compose.onNodeWithTag("custom_topic_input").performTextReplacement("A".repeat(61))
        compose.onNodeWithText("60/60").assertIsDisplayed()
        compose.onNodeWithText("开始聊这个话题").assertIsEnabled().performClick()
        compose.onNodeWithText("A".repeat(60)).assertIsDisplayed()
        compose.onNodeWithText("输入英语消息").assertIsDisplayed()
    }

    @Test fun tappingEitherMessageReplaysWithoutStartingVoiceConversation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val repository = (context.applicationContext as KidsEnglishApplication).container.conversationRepository
            val sessionId = AppSettingsRepository(context).activeSessionId(AgeBand.AGE_6_8).first()!!
            repository.appendUserTurn(sessionId, "I can see a rocket.")
            repository.applyOutcome(sessionId, ConversationOutcome("Hello, explorer!", "", "", emptyList()))
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("6–8 岁").performClick()

        compose.onNodeWithText("Hello, explorer!").assertHasClickAction()
        listOf("I can see a rocket.", "Hello, explorer!").forEach { message ->
            compose.onNodeWithTag("conversation_list").performScrollToNode(hasText(message))
            compose.onNodeWithText(message)
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            compose.onNodeWithText(message).assertIsDisplayed()
            compose.onNodeWithText("输入英语消息").assertIsDisplayed()
        }
        compose.onNodeWithTag("conversation_list").performScrollToNode(hasText("开始语音对话"))
        compose.onNodeWithText("开始语音对话").assertIsDisplayed()
        compose.onNodeWithText("结束语音对话").assertDoesNotExist()
        compose.onNodeWithText("点击英文消息可重新朗读").assertIsDisplayed()
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
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("自动（Google → 讯飞 → 系统默认）").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("Google").fetchSemanticsNodes().size == 1
        }

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
