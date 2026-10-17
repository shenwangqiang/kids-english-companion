package com.kidsenglish.companion.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.kidsenglish.companion.KidsEnglishApplication
import com.kidsenglish.companion.R
import com.kidsenglish.companion.conversation.ConversationOutcome
import com.kidsenglish.companion.conversation.ConversationTopic
import com.kidsenglish.companion.conversation.ConversationTopicCatalog
import com.kidsenglish.companion.model.AgeBand
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppViewModelSessionTest {
    private val application = ApplicationProvider.getApplicationContext<KidsEnglishApplication>()
    private val settings = application.container.settingsRepository
    private val repository = application.container.conversationRepository
    private val store = ViewModelStore()
    private lateinit var viewModel: AppViewModel
    private val ocean = ConversationTopic("custom-ocean-world", "Ocean World")

    @Before
    fun resetSessions() = runBlocking<Unit> {
        AgeBand.entries.forEach { ageBand ->
            repository.observeHistory(ageBand).first().forEach { repository.clear(it.sessionId) }
            settings.setActiveSessionId(ageBand, "missing-${ageBand.wireValue}")
        }
        settings.setAgeBand(AgeBand.AGE_6_8)
        settings.setBaseUrl("")
        settings.setAllowDebugHttp(true)
    }

    @After
    fun clearViewModel() = runBlocking {
        withContext(Dispatchers.Main) { store.clear() }
    }

    @Test
    fun missingSavedSessionFallsBackToLatestAndPersistsIt() = runBlocking {
        repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        delay(5)
        val latest = repository.createSession(AgeBand.AGE_6_8, ocean)
        startViewModel()
        val state = awaitState { it.activeSessionId == latest }
        assertEquals(ocean, state.activeTopic)
        assertEquals(latest, settings.activeSessionId(AgeBand.AGE_6_8).first())
        assertEquals(2, stateHistory().size)
    }

    @Test
    fun emptyHistoryCreatesAndPersistsGeneralSession() = runBlocking {
        startViewModel()
        val state = awaitState { it.activeSessionId != null }
        assertTrue(repository.sessionExists(state.activeSessionId!!))
        assertEquals(ConversationTopicCatalog.general, state.activeTopic)
        assertEquals(state.activeSessionId, settings.activeSessionId(AgeBand.AGE_6_8).first())
        assertEquals(listOf(state.activeSessionId), stateHistory().map { it.sessionId })
    }

    @Test
    fun savedSessionWinsAndRestoresSnapshotTopicTurnsAndMemory() = runBlocking {
        val saved = repository.createSession(AgeBand.AGE_6_8, ocean)
        repository.appendUserTurn(saved, "I like dolphins.")
        repository.applyOutcome(saved, ConversationOutcome("Dolphins swim!", "Likes dolphins", "Ocean animals", listOf("dolphins")))
        delay(5)
        repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        settings.setActiveSessionId(AgeBand.AGE_6_8, saved)
        startViewModel()
        val state = awaitState { it.activeSessionId == saved && it.conversationTurns.size == 2 }
        assertEquals(ocean, state.activeTopic)
        assertEquals(listOf("I like dolphins.", "Dolphins swim!"), state.conversationTurns.map { it.content })
        assertEquals("Likes dolphins", state.conversationMemory.summary)
        assertEquals(listOf("dolphins"), state.conversationMemory.rememberedFacts)
        assertEquals("Ocean animals", state.conversationMemory.nextDirection)
        settings.setSpeechRate(1.1f)
        awaitState { it.settings.speechRate == 1.1f }
        assertEquals(2, stateHistory().size)
        assertEquals(saved, viewModel.uiState.value.activeSessionId)
    }

    @Test
    fun ageSwitchAndHistoryOpeningRestoreTheirOwnObservedSessions() = runBlocking {
        val young = repository.createSession(AgeBand.AGE_6_8, ocean)
        val older = repository.createSession(AgeBand.AGE_11_12, ConversationTopicCatalog.predefined.first())
        settings.setActiveSessionId(AgeBand.AGE_6_8, young)
        settings.setActiveSessionId(AgeBand.AGE_11_12, older)
        startViewModel()
        awaitState { it.activeSessionId == young }
        onMain { viewModel.chooseAgeBand(AgeBand.AGE_11_12) }
        val oldState = awaitState { it.activeSessionId == older && it.conversationHistory.singleOrNull()?.sessionId == older }
        assertEquals(ConversationTopicCatalog.predefined.first(), oldState.activeTopic)
        onMain { viewModel.chooseAgeBand(AgeBand.AGE_6_8) }
        awaitState { it.activeSessionId == young && it.conversationHistory.singleOrNull()?.sessionId == young }
        val another = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        onMain { viewModel.showTopicPicker() }
        assertEquals(AppScreen.TOPICS, viewModel.uiState.value.screen)
        onMain { viewModel.showConversationHistory() }
        assertEquals(AppScreen.HISTORY, viewModel.uiState.value.screen)
        onMain { viewModel.openConversation(another) }
        val reopened = awaitState { it.activeSessionId == another && it.screen == AppScreen.CONVERSATION }
        assertEquals(ConversationTopicCatalog.general, reopened.activeTopic)
        assertEquals(another, settings.activeSessionId(AgeBand.AGE_6_8).first())
    }

    @Test
    fun failedOpeningLeavesEmptyActiveSessionAndOfflineStatus() = runBlocking {
        startViewModel()
        val previous = awaitState { it.activeSessionId != null }.activeSessionId
        onMain { viewModel.createConversation(ocean) }
        val state = awaitState {
            it.activeSessionId != null && it.activeSessionId != previous && !it.conversationLoading &&
                it.conversationStatus == application.getString(R.string.conversation_offline_period)
        }
        assertEquals(AppScreen.CONVERSATION, state.screen)
        assertEquals(ocean, state.activeTopic)
        assertTrue(repository.sessionExists(state.activeSessionId!!))
        assertEquals(state.activeSessionId, settings.activeSessionId(AgeBand.AGE_6_8).first())
        assertTrue(repository.observe(state.activeSessionId).first().turns.isEmpty())
    }

    @Test
    fun clearingActiveSessionResolvesAnotherSessionAndPreservesItsTurns() = runBlocking {
        val previous = repository.createSession(AgeBand.AGE_6_8, ocean)
        repository.appendUserTurn(previous, "Keep dolphins.")
        val selected = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        settings.setActiveSessionId(AgeBand.AGE_6_8, selected)
        startViewModel()
        awaitState { it.activeSessionId == selected }
        onMain { viewModel.clearConversation() }
        val state = awaitState { it.activeSessionId == previous && it.conversationTurns.size == 1 }
        assertFalse(repository.sessionExists(selected))
        assertEquals("Keep dolphins.", state.conversationTurns.single().content)
        assertEquals(previous, settings.activeSessionId(AgeBand.AGE_6_8).first())
    }

    @Test
    fun openingUsesSelectedTopicAndStoresOnlyAssistantReply() = runBlocking {
        val server = MockWebServer()
        server.enqueue(successResponse())
        server.start()
        try {
            settings.setBaseUrl(server.url("/").toString())
            startViewModel()
            val previous = awaitState { it.activeSessionId != null }.activeSessionId
            onMain { viewModel.createConversation(ocean) }
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            val body = request.body.readUtf8()
            val state = awaitState { it.activeSessionId != previous && it.conversationTurns.size == 1 && !it.conversationLoading }
            assertEquals(1, server.requestCount)
            assertTrue(body.contains("Ocean World"))
            assertTrue(body.contains("Open a friendly conversation about Ocean World"))
            assertEquals(listOf("Hello, ocean explorer!"), state.conversationTurns.map { it.content })
            assertEquals("assistant", state.conversationTurns.single().role.wireValue)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun returningToSessionKeepsItBusyUntilItsPendingReplyFinishes() = runBlocking<Unit> {
        val first = repository.createSession(AgeBand.AGE_6_8, ocean)
        val second = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        settings.setActiveSessionId(AgeBand.AGE_6_8, first)
        val releaseReply = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                releaseReply.await(10, TimeUnit.SECONDS)
                return successResponse()
            }
        }
        server.start()
        try {
            settings.setBaseUrl(server.url("/").toString())
            startViewModel()
            awaitState { it.activeSessionId == first }
            onMain { viewModel.sendConversationMessage("I like dolphins.") }
            assertEquals("/api/chat", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
            onMain { viewModel.openConversation(second) }
            assertFalse(awaitState { it.activeSessionId == second }.conversationLoading)
            onMain { viewModel.openConversation(first) }
            assertTrue(awaitState { it.activeSessionId == first }.conversationLoading)
            releaseReply.countDown()
            awaitState { it.activeSessionId == first && !it.conversationLoading && it.conversationTurns.size == 2 }
        } finally {
            releaseReply.countDown()
            server.shutdown()
        }
    }

    @Test
    fun lateReplyAndRequestTopicStayWithCapturedSessionAfterOpeningHistory() = runBlocking {
        val first = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.predefined.first())
        val second = repository.createSession(AgeBand.AGE_6_8, ConversationTopicCatalog.general)
        settings.setActiveSessionId(AgeBand.AGE_6_8, first)
        val releaseReply = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                releaseReply.await(10, TimeUnit.SECONDS)
                return successResponse()
            }
        }
        server.start()
        try {
            settings.setBaseUrl(server.url("/").toString())
            startViewModel()
            awaitState { it.activeSessionId == first }
            onMain { viewModel.sendConversationMessage("I like dolphins.") }
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertTrue(request.body.readUtf8().contains("How to Train Your Dragon"))
            onMain { viewModel.openConversation(second) }
            awaitState { it.activeSessionId == second }
            releaseReply.countDown()
            val completed = withTimeout(10_000) { repository.observe(first).first { it.turns.size == 2 } }
            assertEquals(listOf("I like dolphins.", "Hello, ocean explorer!"), completed.turns.map { it.content })
            assertTrue(repository.observe(second).first().turns.isEmpty())
            assertEquals(second, viewModel.uiState.value.activeSessionId)
            assertNotEquals("Ocean memory", viewModel.uiState.value.conversationMemory.summary)
        } finally {
            releaseReply.countDown()
            server.shutdown()
        }
    }

    private suspend fun startViewModel() = onMain {
        viewModel = AppViewModel(application)
        store.put("sessions", viewModel)
    }

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private suspend fun awaitState(predicate: (AppUiState) -> Boolean): AppUiState = try {
        withTimeout(10_000) { viewModel.uiState.first(predicate) }
    } catch (timeout: TimeoutCancellationException) {
        val state = viewModel.uiState.value
        throw AssertionError(
            "Timed out: session=${state.activeSessionId}, topic=${state.activeTopic.id}, " +
                "turns=${state.conversationTurns.size}, loading=${state.conversationLoading}, status=${state.conversationStatus}",
            timeout,
        )
    }

    private suspend fun stateHistory() = awaitState { it.conversationHistory.isNotEmpty() }.conversationHistory

    private fun successResponse() = MockResponse().setBody(
        """{"message":{"role":"assistant","content":"{\"reply\":\"Hello, ocean explorer!\",\"conversationSummary\":\"Ocean memory\",\"nextDirection\":\"Dolphins\",\"rememberedFacts\":[\"dolphins\"]}"}}""",
    )
}
