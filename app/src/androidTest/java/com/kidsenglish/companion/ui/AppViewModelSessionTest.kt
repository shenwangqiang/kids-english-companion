package com.kidsenglish.companion.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.kidsenglish.companion.KidsEnglishApplication
import com.kidsenglish.companion.R
import com.kidsenglish.companion.conversation.ConversationClient
import com.kidsenglish.companion.conversation.ConversationOutcome
import com.kidsenglish.companion.conversation.ConversationRequest
import com.kidsenglish.companion.conversation.ConversationResult
import com.kidsenglish.companion.conversation.ConversationTopic
import com.kidsenglish.companion.conversation.ConversationTopicCatalog
import com.kidsenglish.companion.conversation.ModelDiscoveryResult
import com.kidsenglish.companion.conversation.VoicePhase
import com.kidsenglish.companion.conversation.VoiceSessionState
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.speech.SpeechController
import com.kidsenglish.companion.speech.SpeechStyle
import com.kidsenglish.companion.speech.TtsPreference
import com.kidsenglish.companion.speech.UtteranceCallbackRouter
import com.kidsenglish.companion.tutor.TutorClient
import com.kidsenglish.companion.tutor.TutorRequest
import com.kidsenglish.companion.tutor.TutorResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    @Test
    fun heldRecognitionCannotCrossHistorySessionSwitch() = runBlocking<Unit> {
        val voice = prepareVoiceSessions()
        val recognition = voice.speech.recognitions.single()
        onMain { viewModel.openConversation(voice.second) }
        awaitState { it.activeSessionId == voice.second }
        onMain { recognition.onResult("Speech from the ocean session.") }
        assertVoiceSessionUntouched(voice.second, VoiceSessionState())
        assertEquals(0, voice.client.requestCount)
    }

    @Test
    fun heldRecognitionCannotCrossClearAndFallback() = runBlocking<Unit> {
        val voice = prepareVoiceSessions()
        val recognition = voice.speech.recognitions.single()
        onMain { viewModel.clearConversation() }
        awaitState { it.activeSessionId == voice.second }
        onMain { recognition.onResult("Speech from a deleted session.") }
        assertVoiceSessionUntouched(voice.second, VoiceSessionState())
        assertFalse(repository.sessionExists(voice.first))
        assertEquals(0, voice.client.requestCount)
    }

    @Test
    fun heldRecognitionCannotCrossAgeBandSwitch() = runBlocking<Unit> {
        val voice = prepareVoiceSessions(AgeBand.AGE_11_12)
        val recognition = voice.speech.recognitions.single()
        onMain { viewModel.chooseAgeBand(AgeBand.AGE_11_12) }
        awaitState { it.activeSessionId == voice.second }
        onMain { recognition.onResult("Speech from the younger child.") }
        assertVoiceSessionUntouched(voice.second, VoiceSessionState())
        assertEquals(0, voice.client.requestCount)
    }

    @Test
    fun stoppedRecognitionCannotMutateRestartedOperationInSameSession() = runBlocking<Unit> {
        val voice = prepareVoiceSessions()
        val previous = voice.speech.recognitions.single()
        onMain {
            viewModel.stopVoiceConversation()
            assertTrue(viewModel.startVoiceConversation())
            previous.onResult("Stale recognition.")
            previous.onError("Stale recognition failure.")
        }
        assertVoiceSessionUntouched(voice.first, VoiceSessionState(phase = VoicePhase.LISTENING))
        assertEquals(0, voice.client.requestCount)
    }

    @Test
    fun lateVoiceHttpFailureCannotFailAnotherSessionsNewOperation() = runBlocking<Unit> {
        val voice = prepareVoiceSessions()
        onMain { voice.speech.recognitions.single().onResult("Dolphins.") }
        val pending = voice.client.nextCall()
        assertEquals(ocean, pending.request.topic)
        assertEquals(AgeBand.AGE_6_8, pending.request.ageBand)
        onMain { viewModel.openConversation(voice.second) }
        awaitState { it.activeSessionId == voice.second }
        onMain {
            viewModel.stopVoiceConversation()
            assertTrue(viewModel.startVoiceConversation())
        }
        pending.result.complete(ConversationResult.Unavailable())
        pending.returned.await()
        onMain { }
        assertVoiceSessionUntouched(voice.second, VoiceSessionState(phase = VoicePhase.LISTENING))
    }

    @Test
    fun lateVoiceHttpSuccessCannotLeaveNewSessionThinking() = runBlocking<Unit> {
        val voice = prepareVoiceSessions()
        onMain { voice.speech.recognitions.single().onResult("Dolphins.") }
        val pending = voice.client.nextCall()
        onMain { viewModel.openConversation(voice.second) }
        awaitState { it.activeSessionId == voice.second }
        pending.result.complete(voiceSuccess("Old ocean reply."))
        pending.returned.await()
        onMain { }
        assertVoiceSessionUntouched(voice.second, VoiceSessionState())
        assertTrue(voice.speech.playbacks.isEmpty())
    }

    @Test
    fun lateTtsCompletionCannotRestartAnotherSessionsSpeakingOperation() = runBlocking<Unit> {
        val (voice, previousPlayback) = prepareTwoSpeakingSessions()
        val expected = viewModel.uiState.value.voiceSession
        assertEquals(VoicePhase.SPEAKING, expected.phase)
        onMain { previousPlayback.onDone() }
        onMain { }
        assertEquals(expected, viewModel.uiState.value.voiceSession)
        assertEquals(2, voice.speech.recognitions.size)
        assertEquals(listOf("A new space message.", "New space reply."), repository.observe(voice.second).first().turns.map { it.content })
        assertEquals("New space reply.", viewModel.uiState.value.conversationMemory.summary)
    }

    @Test
    fun lateTtsFailureCannotFailAnotherSessionsSpeakingOperation() = runBlocking<Unit> {
        val (voice, previousPlayback) = prepareTwoSpeakingSessions()
        val expected = viewModel.uiState.value.voiceSession
        onMain { previousPlayback.onError("Old playback failed.") }
        onMain { }
        assertEquals(expected, viewModel.uiState.value.voiceSession)
        assertEquals(2, voice.speech.recognitions.size)
        assertEquals("New space reply.", repository.observe(voice.second).first().memory.summary)
    }

    private suspend fun prepareVoiceSessions(secondAge: AgeBand = AgeBand.AGE_6_8): VoiceHarness {
        val first = repository.createSession(AgeBand.AGE_6_8, ocean)
        val second = repository.createSession(secondAge, ConversationTopicCatalog.general)
        settings.setActiveSessionId(AgeBand.AGE_6_8, first)
        if (secondAge != AgeBand.AGE_6_8) settings.setActiveSessionId(secondAge, second)
        settings.setBaseUrl("http://127.0.0.1:11434")
        val speech = HeldSpeechController()
        val client = HeldConversationClient()
        startViewModel(speech, client)
        awaitState { it.activeSessionId == first }
        onMain { assertTrue(viewModel.startVoiceConversation()) }
        return VoiceHarness(first, second, speech, client)
    }

    @Test fun unsafeTextNeverEntersConversationStorageOrTransport() = runBlocking {
        val voice = prepareVoiceSessions()
        onMain {
            viewModel.stopVoiceConversation()
            viewModel.sendConversationMessage("My home address is 123 Secret Road.")
            assertEquals("我们聊聊安全的学习话题吧，请不要分享个人信息。", viewModel.uiState.value.conversationStatus)
        }
        assertTrue(repository.observe(voice.first).first().turns.isEmpty())
        assertEquals(0, voice.client.requestCount)
    }

    @Test fun unsafeRecognitionNeverEntersConversationStorageOrTransport() = runBlocking {
        val voice = prepareVoiceSessions()
        onMain {
            voice.speech.recognitions.single().onResult("Please share your phone number.")
            assertEquals(VoicePhase.ERROR, viewModel.uiState.value.voiceSession.phase)
            assertEquals("我们聊聊安全的学习话题吧，请不要分享个人信息。", viewModel.uiState.value.voiceSession.message)
        }
        assertTrue(repository.observe(voice.first).first().turns.isEmpty())
        assertEquals(0, voice.client.requestCount)
    }

    @Test fun openingAndTextRequestsRejectVoiceUntilTheirMemoryIsCommitted() = runBlocking<Unit> {
        settings.setBaseUrl("http://127.0.0.1:11434")
        val speech = HeldSpeechController()
        val client = HeldConversationClient()
        startViewModel(speech, client)
        awaitState { it.activeSessionId != null }
        onMain { viewModel.createConversation(ocean) }
        val opening = client.nextCall()
        awaitState { it.activeTopic == ocean }
        onMain { assertFalse("Opening owns the same operation gate as voice", viewModel.startVoiceConversation()) }
        opening.result.complete(voiceSuccess("Opening memory."))
        awaitState { !it.conversationLoading && it.conversationMemory.summary == "Opening memory." }
        onMain { viewModel.sendConversationMessage("Dolphins.") }
        val textCall = client.nextCall()
        assertEquals("Opening memory.", textCall.request.memory.summary)
        onMain { assertFalse("Text owns the same operation gate as voice", viewModel.startVoiceConversation()) }
        textCall.result.complete(voiceSuccess("Latest memory."))
        awaitState { !it.conversationLoading && it.conversationMemory.summary == "Latest memory." }
        onMain {
            assertTrue(viewModel.startVoiceConversation())
            speech.recognitions.last().onResult("Whales.")
        }
        val voiceCall = client.nextCall()
        assertEquals("Latest memory.", voiceCall.request.memory.summary)
        assertEquals(3, voiceCall.request.history.size)
        voiceCall.result.complete(voiceSuccess("Voice memory."))
        awaitState { it.voiceSession.phase == VoicePhase.SPEAKING }
    }

    @Test fun voiceGateRejectsTextAndStoppedInFlightVoiceCannotOverwriteNewMemory() = runBlocking {
        val voice = prepareVoiceSessions()
        onMain { viewModel.sendConversationMessage("Overlapping text.") }
        assertTrue(repository.observe(voice.first).first().turns.isEmpty())
        assertEquals(0, voice.client.requestCount)
        onMain { voice.speech.recognitions.single().onResult("First voice.") }
        val pending = voice.client.nextCall()
        onMain {
            viewModel.stopVoiceConversation()
            viewModel.sendConversationMessage("Still overlapping.")
            assertTrue(viewModel.uiState.value.conversationLoading)
        }
        assertEquals(1, voice.client.requestCount)
        pending.result.complete(voiceSuccess("Obsolete memory."))
        awaitState { !it.conversationLoading }
        onMain { viewModel.sendConversationMessage("Fresh text.") }
        val fresh = voice.client.nextCall()
        assertEquals("", fresh.request.memory.summary)
        fresh.result.complete(voiceSuccess("Fresh memory."))
        awaitState { !it.conversationLoading && it.conversationMemory.summary == "Fresh memory." }
        val snapshot = repository.observe(voice.first).first()
        assertEquals(listOf("First voice.", "Fresh text.", "Fresh memory."), snapshot.turns.map { it.content })
    }

    @Test fun practiceRecordingCannotScoreAnotherSentenceOrStoryAndAlwaysStopsListening() = runBlocking {
        val speech = HeldSpeechController()
        startViewModel(speech)
        awaitState { it.stories.isNotEmpty() }
        val stories = viewModel.uiState.value.stories
        val baseline = application.container.progressRepository.observeAll().first().associate { it.storyId to it.speakingAttempts }
        onMain {
            viewModel.openStory(stories[0])
            viewModel.showPractice()
            viewModel.startListening()
        }
        val first = speech.recognitions.last()
        onMain {
            viewModel.nextSentence()
            assertFalse(viewModel.uiState.value.isListening)
            first.onResult(stories[0].paragraphs.first().sentences.first().text)
            viewModel.startListening()
            viewModel.openStory(stories[1])
            speech.recognitions.last().onResult("An old recording.")
            assertFalse(viewModel.uiState.value.isListening)
            assertEquals(null, viewModel.uiState.value.speakingFeedback)
        }
        val attempts = application.container.progressRepository.observeAll().first()
        assertTrue(attempts.all { it.speakingAttempts == (baseline[it.storyId] ?: 0) })
        onMain {
            viewModel.showPractice()
            viewModel.startListening()
            viewModel.chooseAgeBand(AgeBand.AGE_11_12)
            speech.recognitions.last().onResult("No current story.")
            assertFalse(viewModel.uiState.value.isListening)
        }
    }

    @Test fun practiceTerminalCallbacksAreConsumedOnceAndCannotEndNewListening() = runBlocking {
        val speech = HeldSpeechController()
        startViewModel(speech)
        awaitState { it.stories.isNotEmpty() }
        val story = viewModel.uiState.value.stories.first()
        val baseline = application.container.progressRepository.observeAll().first().firstOrNull { it.storyId == story.id }?.speakingAttempts ?: 0
        onMain {
            viewModel.openStory(story)
            viewModel.showPractice()
            viewModel.startListening()
        }
        val previous = speech.recognitions.last()
        onMain { previous.onResult(viewModel.uiState.value.currentSentence!!.text) }
        withTimeout(10_000) { application.container.progressRepository.observeAll().first { list -> list.any { it.storyId == story.id && it.speakingAttempts == baseline + 1 } } }
        onMain {
            viewModel.startListening()
            previous.onError("A delayed old error.")
            assertTrue(viewModel.uiState.value.isListening)
            speech.recognitions.last().onError("Current error.")
            assertFalse(viewModel.uiState.value.isListening)
        }
    }

    @Test fun stoppingSharedSpeechResourcesCannotLeavePracticeListeningStuck() = runBlocking {
        val speech = HeldSpeechController()
        startViewModel(speech)
        awaitState { it.stories.isNotEmpty() }
        onMain {
            viewModel.openStory(viewModel.uiState.value.stories.first())
            viewModel.showPractice()
            viewModel.startListening()
            viewModel.stopVoiceConversation()
            assertFalse(viewModel.uiState.value.isListening)
            speech.recognitions.last().onResult("A cancelled recording.")
            assertEquals(null, viewModel.uiState.value.speakingFeedback)
        }
    }

    @Test fun tutorHistoryAndDelayedRepliesCannotCrossStoryOrAgeScope() = runBlocking {
        val release = CountDownLatch(1)
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.body.clone().readUtf8().contains("Hold this reply")) release.await(10, TimeUnit.SECONDS)
                return MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"Scoped reply"}}]}""")
            }
        }
        server.start()
        try {
            settings.setBaseUrl(server.url("/v1").toString())
            startViewModel(HeldSpeechController())
            awaitState { it.stories.isNotEmpty() }
            val stories = viewModel.uiState.value.stories
            onMain {
                viewModel.openStory(stories[0])
                viewModel.sendTutorMessage("Hold this reply")
            }
            server.takeRequest(5, TimeUnit.SECONDS)!!
            onMain {
                viewModel.openStory(stories[1])
                assertTrue(viewModel.uiState.value.tutorMessages.isEmpty())
                assertFalse(viewModel.uiState.value.tutorLoading)
                viewModel.sendTutorMessage("Where is the kite?")
            }
            val second = server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()
            assertFalse(second.contains("Hold this reply"))
            awaitState { !it.tutorLoading && it.tutorMessages.size == 2 }
            release.countDown()
            onMain {
                viewModel.chooseAgeBand(AgeBand.AGE_11_12)
                assertTrue(viewModel.uiState.value.tutorMessages.isEmpty())
                assertFalse(viewModel.uiState.value.tutorLoading)
            }
        } finally {
            release.countDown()
            server.shutdown()
        }
    }

    @Test fun everySessionTransitionReleasesForegroundAndLockScreenResources() = runBlocking {
        val transitions: List<suspend (VoiceHarness) -> Unit> = listOf(
            { onMain { viewModel.stopVoiceConversation() } },
            { voice -> onMain { viewModel.openConversation(voice.second) }; awaitState { it.activeSessionId == voice.second }; Unit },
            { onMain { viewModel.chooseAgeBand(AgeBand.AGE_11_12) } },
            { onMain { viewModel.createConversation(ocean) } },
            { onMain { viewModel.clearConversation() } },
            { onMain { store.clear() } },
        )
        transitions.forEach { transition -> assertVoiceResourceTeardown(transition = transition) }
    }

    @Test fun delayedTutorSuccessCannotMutateAReopenedStoryScope() = runBlocking {
        val tutor = HeldTutorClient()
        settings.setBaseUrl("http://127.0.0.1:11434")
        startViewModel(HeldSpeechController(), tutor = tutor)
        awaitState { it.stories.isNotEmpty() }
        val stories = viewModel.uiState.value.stories
        onMain { viewModel.openStory(stories[0]); viewModel.sendTutorMessage("Old question.") }
        val old = tutor.nextCall()
        onMain {
            viewModel.openStory(stories[1])
            viewModel.openStory(stories[0])
            viewModel.sendTutorMessage("Current question.")
        }
        val current = tutor.nextCall()
        assertTrue(current.request.history.isEmpty())
        old.result.complete(TutorResult.Success("Obsolete answer."))
        old.returned.await()
        onMain {
            assertTrue(viewModel.uiState.value.tutorLoading)
            assertEquals(listOf("Current question."), viewModel.uiState.value.tutorMessages.map { it.content })
        }
        current.result.complete(TutorResult.Success("Current answer."))
        val state = awaitState { !it.tutorLoading }
        assertEquals(listOf("Current question.", "Current answer."), state.tutorMessages.map { it.content })
    }

    @Test fun delayedTutorFailureCannotClearLoadingOrSetStatusInAnotherAgeBand() = runBlocking {
        val tutor = HeldTutorClient()
        settings.setBaseUrl("http://127.0.0.1:11434")
        startViewModel(HeldSpeechController(), tutor = tutor)
        awaitState { it.stories.isNotEmpty() }
        onMain { viewModel.openStory(viewModel.uiState.value.stories.first()); viewModel.sendTutorMessage("Old question.") }
        val old = tutor.nextCall()
        onMain { viewModel.chooseAgeBand(AgeBand.AGE_11_12) }
        awaitState { it.settings.ageBand == AgeBand.AGE_11_12 }
        onMain { viewModel.openStory(viewModel.uiState.value.stories.first()); viewModel.sendTutorMessage("Current question.") }
        val current = tutor.nextCall()
        assertEquals(AgeBand.AGE_11_12, current.request.ageBand)
        assertTrue(current.request.history.isEmpty())
        old.result.complete(TutorResult.Unavailable())
        old.returned.await()
        onMain {
            assertTrue(viewModel.uiState.value.tutorLoading)
            assertEquals("", viewModel.uiState.value.tutorStatus)
            assertEquals(listOf("Current question."), viewModel.uiState.value.tutorMessages.map { it.content })
        }
        current.result.complete(TutorResult.Success("Current answer."))
        awaitState { !it.tutorLoading }
        Unit
    }

    @Test fun recognitionHttpAndTtsErrorsReleaseResourcesButKeepRetryAvailable() = runBlocking {
        assertVoiceResourceTeardown(expectError = true) { voice -> onMain { voice.speech.recognitions.last().onError("Recognition stopped.") } }
        assertVoiceResourceTeardown(expectError = true) { voice ->
            onMain { voice.speech.recognitions.last().onResult("Dolphins.") }
            voice.client.nextCall().result.complete(ConversationResult.Unavailable())
        }
        assertVoiceResourceTeardown(expectError = true) { voice ->
            onMain { voice.speech.recognitions.last().onResult("Dolphins.") }
            voice.client.nextCall().result.complete(voiceSuccess("Hello."))
            awaitState { it.voiceSession.phase == VoicePhase.SPEAKING }
            onMain { voice.speech.playbacks.last().onError("Playback stopped.") }
        }
    }

    private suspend fun assertVoiceResourceTeardown(expectError: Boolean = false, transition: suspend (VoiceHarness) -> Unit) {
        settings.setAgeBand(AgeBand.AGE_6_8)
        val voice = prepareVoiceSessions()
        val resources = Channel<Boolean>(Channel.UNLIMITED)
        val collector = onMain {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                viewModel.voiceResourcesActive.collect { resources.send(it) }
            }
        }
        try {
            assertTrue(withTimeout(10_000) { resources.receive() })
            transition(voice)
            assertFalse("Both platform effects must observe deactivation", withTimeout(10_000) { resources.receive() })
            if (expectError) {
                assertEquals(VoicePhase.ERROR, viewModel.uiState.value.voiceSession.phase)
                onMain { viewModel.retryVoiceConversation() }
                assertTrue("Retry reacquires the service and lock-screen resources", withTimeout(10_000) { resources.receive() })
            }
        } finally {
            collector.cancel()
            onMain { store.clear() }
        }
    }

    private suspend fun prepareTwoSpeakingSessions(): Pair<VoiceHarness, HeldSpeechController.Playback> {
        val voice = prepareVoiceSessions()
        onMain { voice.speech.recognitions.single().onResult("An ocean message.") }
        voice.client.nextCall().result.complete(voiceSuccess("Old ocean reply."))
        awaitState { it.voiceSession.phase == VoicePhase.SPEAKING }
        val previousPlayback = onMain { voice.speech.playbacks.single() }
        onMain { viewModel.openConversation(voice.second) }
        awaitState { it.activeSessionId == voice.second }
        onMain {
            viewModel.stopVoiceConversation()
            assertTrue(viewModel.startVoiceConversation())
            voice.speech.recognitions.last().onResult("A new space message.")
        }
        voice.client.nextCall().result.complete(voiceSuccess("New space reply."))
        awaitState { it.voiceSession.phase == VoicePhase.SPEAKING && it.conversationMemory.summary == "New space reply." }
        onMain { }
        return voice to previousPlayback
    }

    private suspend fun assertVoiceSessionUntouched(sessionId: String, expectedVoice: VoiceSessionState) {
        val state = viewModel.uiState.value
        assertEquals(sessionId, state.activeSessionId)
        assertEquals(expectedVoice, state.voiceSession)
        val snapshot = repository.observe(sessionId).first()
        assertTrue(snapshot.turns.isEmpty())
        assertEquals("", snapshot.memory.summary)
        assertTrue(snapshot.memory.rememberedFacts.isEmpty())
        assertEquals("", snapshot.memory.nextDirection)
    }

    private fun voiceSuccess(reply: String) = ConversationResult.Success(
        ConversationOutcome(reply, reply, "Next", listOf(reply)),
    )

    private data class VoiceHarness(
        val first: String,
        val second: String,
        val speech: HeldSpeechController,
        val client: HeldConversationClient,
    )

    private class HeldSpeechController : SpeechController {
        data class Recognition(val onResult: (String) -> Unit, val onError: (String) -> Unit)
        data class Playback(val onDone: () -> Unit, val onError: (String) -> Unit)
        val recognitions = mutableListOf<Recognition>()
        val playbacks = mutableListOf<Playback>()
        private val utteranceCallbacks = UtteranceCallbackRouter()

        override fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
            recognitions += Recognition(onResult, onError)
        }

        override fun speak(text: String, rate: Float, style: SpeechStyle, onDone: () -> Unit, onError: (String) -> Unit) {
            val utteranceId = utteranceCallbacks.register(onDone, onError)
            playbacks += Playback(
                onDone = { utteranceCallbacks.onDone(utteranceId) },
                onError = { utteranceCallbacks.onError(utteranceId, it) },
            )
        }

        // Retain event handles after stop; TTS events pass through the production identity router.
        override fun stopListening() = Unit
        override fun stopSpeaking() = utteranceCallbacks.clear()
        override fun setTtsPreference(preference: TtsPreference) = Unit
        override fun shutdown() = utteranceCallbacks.clear()
    }

    private class HeldConversationClient : ConversationClient {
        data class Call(
            val request: ConversationRequest,
            val result: CompletableDeferred<ConversationResult> = CompletableDeferred(),
            val returned: CompletableDeferred<Unit> = CompletableDeferred(),
        )
        private val calls = Channel<Call>(Channel.UNLIMITED)
        var requestCount = 0
            private set

        override suspend fun chat(request: ConversationRequest): ConversationResult {
            val call = Call(request)
            requestCount++
            calls.send(call)
            return call.result.await().also { call.returned.complete(Unit) }
        }

        suspend fun nextCall(): Call = withTimeout(10_000) { calls.receive() }
        override suspend fun connectionTest(): ConversationResult = ConversationResult.Unavailable()
        override suspend fun availableModels(): ModelDiscoveryResult = ModelDiscoveryResult.Unavailable
    }

    private class HeldTutorClient : TutorClient {
        data class Call(
            val request: TutorRequest,
            val result: CompletableDeferred<TutorResult> = CompletableDeferred(),
            val returned: CompletableDeferred<Unit> = CompletableDeferred(),
        )
        private val calls = Channel<Call>(Channel.UNLIMITED)
        override suspend fun chat(request: TutorRequest): TutorResult {
            val call = Call(request)
            calls.send(call)
            return call.result.await().also { call.returned.complete(Unit) }
        }
        suspend fun nextCall(): Call = withTimeout(10_000) { calls.receive() }
        override suspend fun connectionTest(): TutorResult = TutorResult.Unavailable()
    }

    private suspend fun startViewModel(
        speech: SpeechController = application.container.speechController,
        client: ConversationClient = application.container.conversationClient,
        tutor: TutorClient = application.container.tutorClient,
    ) = onMain {
        viewModel = AppViewModel(application, speech, client, tutorClient = tutor)
        store.put("sessions", viewModel)
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

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
