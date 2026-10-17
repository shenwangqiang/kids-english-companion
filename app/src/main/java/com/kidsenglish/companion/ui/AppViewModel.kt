package com.kidsenglish.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kidsenglish.companion.KidsEnglishApplication
import com.kidsenglish.companion.R
import com.kidsenglish.companion.conversation.ConversationClient
import com.kidsenglish.companion.conversation.ConversationMemory
import com.kidsenglish.companion.conversation.ConversationRequest
import com.kidsenglish.companion.conversation.ConversationReplayController
import com.kidsenglish.companion.conversation.ConversationResult
import com.kidsenglish.companion.conversation.ConversationSessionSummary
import com.kidsenglish.companion.conversation.ConversationTopic
import com.kidsenglish.companion.conversation.ConversationTopicCatalog
import com.kidsenglish.companion.conversation.DEFAULT_CONVERSATION_MODEL
import com.kidsenglish.companion.conversation.ConversationTurn
import com.kidsenglish.companion.conversation.ModelDiscoveryResult
import com.kidsenglish.companion.conversation.ModelSelection
import com.kidsenglish.companion.conversation.VoiceEvent
import com.kidsenglish.companion.conversation.VoicePhase
import com.kidsenglish.companion.conversation.VoiceSessionReducer
import com.kidsenglish.companion.conversation.VoiceSessionState
import com.kidsenglish.companion.speech.SpeechController
import com.kidsenglish.companion.speech.SpeechStyle
import com.kidsenglish.companion.speech.TtsPreference
import com.kidsenglish.companion.domain.SpeakingFeedback
import com.kidsenglish.companion.domain.SpeakingScore
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.model.Story
import com.kidsenglish.companion.model.StorySentence
import com.kidsenglish.companion.progress.LearningProgressEntity
import com.kidsenglish.companion.settings.AppSettings
import com.kidsenglish.companion.tutor.ChatMessage
import com.kidsenglish.companion.tutor.ChatRole
import com.kidsenglish.companion.tutor.TutorRequest
import com.kidsenglish.companion.tutor.TutorResult
import com.kidsenglish.companion.tutor.TutorClient
import com.kidsenglish.companion.tutor.TutorPolicy
import com.kidsenglish.companion.tutor.PolicyDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val screen: AppScreen = AppScreen.WELCOME,
    val selectedAgeBand: AgeBand = AgeBand.AGE_6_8,
    val stories: List<Story> = emptyList(),
    val currentStory: Story? = null,
    val sentenceIndex: Int = 0,
    val speakingFeedback: SpeakingFeedback? = null,
    val isListening: Boolean = false,
    val tutorMessages: List<ChatMessage> = emptyList(),
    val tutorStatus: String = "",
    val tutorLoading: Boolean = false,
    val conversationTurns: List<ConversationTurn> = emptyList(),
    val activeSessionId: String? = null,
    val activeTopic: ConversationTopic = ConversationTopicCatalog.general,
    val conversationHistory: List<ConversationSessionSummary> = emptyList(),
    val conversationMemory: ConversationMemory = ConversationMemory(),
    val conversationStatus: String = "",
    val conversationLoading: Boolean = false,
    val voiceSession: VoiceSessionState = VoiceSessionState(),
    val progress: List<LearningProgressEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val connectionStatus: String = "",
    val availableModels: List<String> = ModelSelection.fallbackModels,
    val modelsLoading: Boolean = false,
    val modelDiscoveryStatus: String = "",
) {
    val sentences: List<StorySentence> get() = currentStory?.paragraphs?.flatMap { it.sentences }.orEmpty()
    val currentSentence: StorySentence? get() = sentences.getOrNull(sentenceIndex)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel internal constructor(
    application: Application,
    private val speechController: SpeechController,
    private val conversationClient: ConversationClient,
    private val conversationReplayController: ConversationReplayController = ConversationReplayController(speechController),
    private val tutorClient: TutorClient = (application as KidsEnglishApplication).container.tutorClient,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        (application as KidsEnglishApplication).container.speechController,
        application.container.conversationClient,
        application.container.conversationReplayController,
    )

    private val container = (application as KidsEnglishApplication).container
    private val navigator = LearningNavigator()
    // All entry points reserve a session synchronously on Main, before any suspension.
    private val conversationOperations = mutableMapOf<String, ConversationOperation>()
    private val clearingSessions = mutableSetOf<String>()
    private var voiceOperationGeneration = 0L
    private var voiceOperation: VoiceOperation? = null
    private var tutorGeneration = 0L
    private var practiceOperation: PracticeOperation? = null
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    // Activity observes this even while stopped, so lock-screen voice may continue.
    // ERROR keeps retry controls visible but releases microphone-service/window resources.
    val voiceResourcesActive = uiState.map {
        it.voiceSession.phase in setOf(VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING)
    }.distinctUntilChanged()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                if (_uiState.value.selectedAgeBand != settings.ageBand) {
                    stopVoiceConversation()
                    invalidatePractice()
                    resetTutorScope()
                    _uiState.update { it.copy(currentStory = null) }
                }
                _uiState.update {
                    it.copy(
                        settings = settings,
                        selectedAgeBand = settings.ageBand,
                        stories = container.storyRepository.stories(settings.ageBand),
                    )
                }
            }
        }
        viewModelScope.launch {
            container.settingsRepository.settings
                .map { it.ttsPreference }
                .distinctUntilChanged()
                .collect(speechController::setTtsPreference)
        }
        viewModelScope.launch {
            container.progressRepository.observeAll().collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
        viewModelScope.launch {
            container.settingsRepository.settings
                .map { it.ageBand }
                .distinctUntilChanged()
                .flatMapLatest { ageBand ->
                    container.settingsRepository.activeSessionId(ageBand)
                        .distinctUntilChanged()
                        .map { resolveActiveSession(ageBand, it) }
                        .distinctUntilChanged()
                        .flatMapLatest { container.conversationRepository.observe(it) }
                        .map { ageBand to it }
                }
                .collect { (ageBand, snapshot) ->
                    if (_uiState.value.selectedAgeBand == ageBand && _uiState.value.activeSessionId != snapshot.sessionId) {
                        stopVoiceConversation()
                    }
                    _uiState.update { state ->
                        if (state.selectedAgeBand != ageBand) state else state.copy(
                            activeSessionId = snapshot.sessionId,
                            activeTopic = snapshot.topic,
                            conversationTurns = snapshot.turns,
                            conversationMemory = snapshot.memory,
                            conversationLoading = isConversationBusy(snapshot.sessionId),
                        )
                    }
                }
        }
        viewModelScope.launch {
            container.settingsRepository.settings
                .map { it.ageBand }
                .distinctUntilChanged()
                .flatMapLatest { ageBand ->
                    container.conversationRepository.observeHistory(ageBand).map { ageBand to it }
                }
                .collect { (ageBand, history) ->
                    _uiState.update { state ->
                        if (state.selectedAgeBand != ageBand) state else state.copy(conversationHistory = history)
                    }
                }
        }
    }

    private suspend fun resolveActiveSession(ageBand: AgeBand, savedId: String?): String {
        val repository = container.conversationRepository
        val resolved = savedId?.takeIf { repository.sessionExists(it) }
            ?: repository.latestSessionId(ageBand)
            ?: repository.createSession(ageBand, ConversationTopicCatalog.general)
        if (savedId != resolved) container.settingsRepository.setActiveSessionId(ageBand, resolved)
        return resolved
    }

    fun chooseAgeBand(ageBand: AgeBand) {
        invalidatePractice()
        resetTutorScope()
        if (_uiState.value.selectedAgeBand != ageBand) stopVoiceConversation()
        navigator.showConversation()
        _uiState.update {
            val ageChanged = it.selectedAgeBand != ageBand
            it.copy(
                screen = navigator.screen,
                selectedAgeBand = ageBand,
                stories = container.storyRepository.stories(ageBand),
                currentStory = null,
                activeSessionId = if (ageChanged) null else it.activeSessionId,
                activeTopic = if (ageChanged) ConversationTopicCatalog.general else it.activeTopic,
                conversationHistory = if (ageChanged) emptyList() else it.conversationHistory,
                conversationTurns = if (ageChanged) emptyList() else it.conversationTurns,
                conversationMemory = if (ageChanged) ConversationMemory() else it.conversationMemory,
                conversationLoading = if (ageChanged) false else it.conversationLoading,
                conversationStatus = if (it.settings.baseUrl.isBlank()) text(R.string.conversation_offline) else "",
            )
        }
        viewModelScope.launch { container.settingsRepository.setAgeBand(ageBand) }
    }

    fun showConversation() {
        invalidatePractice()
        navigator.showConversation()
        _uiState.update {
            it.copy(
                screen = navigator.screen,
                conversationStatus = if (it.settings.baseUrl.isBlank()) text(R.string.conversation_offline) else "",
            )
        }
    }

    fun showLibrary() {
        invalidatePractice()
        navigator.showLibrary()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    fun showTopicPicker() {
        invalidatePractice()
        navigator.showTopicPicker()
        _uiState.update { it.copy(screen = navigator.screen, conversationStatus = "") }
    }

    fun showConversationHistory() {
        invalidatePractice()
        navigator.showHistory()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    fun replayConversationMessage(text: String) {
        val state = _uiState.value
        conversationReplayController.replay(text, state.settings.speechRate) { message ->
            _uiState.update {
                if (it.activeSessionId == state.activeSessionId) it.copy(conversationStatus = message) else it
            }
        }
    }

    fun openConversation(sessionId: String) {
        val ageBand = _uiState.value.selectedAgeBand
        viewModelScope.launch {
            val belongsToAgeBand = container.conversationRepository.observeHistory(ageBand).first()
                .any { it.sessionId == sessionId }
            if (!belongsToAgeBand) return@launch
            if (_uiState.value.activeSessionId != sessionId) stopVoiceConversation()
            container.settingsRepository.setActiveSessionId(ageBand, sessionId)
            if (_uiState.value.selectedAgeBand == ageBand) showConversation()
        }
    }

    fun createConversation(topic: ConversationTopic) {
        if (TutorPolicy.validateInput(topic.title) is PolicyDecision.Blocked) {
            conversationFailure(text(R.string.child_input_blocked))
            return
        }
        val ageBand = _uiState.value.selectedAgeBand
        stopVoiceConversation()
        viewModelScope.launch {
            val sessionId = container.conversationRepository.createSession(ageBand, topic)
            val operation = beginConversationOperation(sessionId) ?: return@launch
            try {
                container.settingsRepository.setActiveSessionId(ageBand, sessionId)
                if (_uiState.value.selectedAgeBand == ageBand) showConversation()
                requestConversation(operation, ageBand, "", opening = true)?.let {
                    showConversationResult(sessionId, it)
                }
            } finally {
                finishConversationOperation(operation)
            }
        }
    }

    fun sendConversationMessage(message: String) {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        val normalized = message.trim()
        if (TutorPolicy.validateInput(normalized) is PolicyDecision.Blocked) {
            conversationFailure(sessionId, text(R.string.child_input_blocked))
            return
        }
        if (state.settings.baseUrl.isBlank()) {
            _uiState.update { it.copy(conversationStatus = text(R.string.conversation_offline)) }
            return
        }
        val operation = beginConversationOperation(sessionId) ?: return
        viewModelScope.launch {
            try {
                requestConversation(operation, state.selectedAgeBand, normalized)?.let {
                    showConversationResult(sessionId, it)
                }
            } finally {
                finishConversationOperation(operation)
            }
        }
    }

    private class ConversationOperation(val sessionId: String) {
        var running = false
        val finished = CompletableDeferred<Unit>()
    }

    private fun isConversationBusy(sessionId: String) = sessionId in conversationOperations || sessionId in clearingSessions

    private fun beginConversationOperation(sessionId: String): ConversationOperation? {
        if (isConversationBusy(sessionId)) return null
        val operation = ConversationOperation(sessionId)
        conversationOperations[sessionId] = operation
        _uiState.update {
            if (it.activeSessionId == sessionId) it.copy(conversationLoading = true, conversationStatus = "") else it
        }
        return operation
    }

    private fun finishConversationOperation(operation: ConversationOperation) {
        if (conversationOperations[operation.sessionId] === operation) conversationOperations.remove(operation.sessionId)
        operation.finished.complete(Unit)
        _uiState.update {
            if (it.activeSessionId == operation.sessionId) it.copy(conversationLoading = isConversationBusy(operation.sessionId)) else it
        }
    }

    private suspend fun requestConversation(
        operation: ConversationOperation,
        ageBand: AgeBand,
        message: String,
        opening: Boolean = false,
        isCurrent: () -> Boolean = { true },
    ): ConversationResult? {
        val repository = container.conversationRepository
        val sessionId = operation.sessionId
        fun canApply() = isCurrent() && sessionId !in clearingSessions
        operation.running = true
        return try {
            if (!canApply() || !repository.sessionExists(sessionId)) return null
            // Read after acquiring the gate; UI/Room collectors may still display the previous memory.
            val snapshot = repository.observe(sessionId).first()
            if (!canApply()) return null
            if (!opening) repository.appendUserTurn(sessionId, message)
            if (!canApply()) return null
            val result = conversationClient.chat(ConversationRequest(
                ageBand, message, snapshot.turns, snapshot.memory, snapshot.topic, opening,
            ))
            if (!canApply()) return null
            if (result is ConversationResult.Success) repository.applyOutcome(sessionId, result.outcome)
            if (canApply()) result else null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (canApply()) ConversationResult.Unavailable() else null
        } finally {
            operation.running = false
        }
    }

    private fun showConversationResult(sessionId: String, result: ConversationResult) {
        when (result) {
            is ConversationResult.Success -> Unit
            is ConversationResult.AuthenticationRequired -> conversationFailure(sessionId, text(R.string.authentication_required))
            is ConversationResult.Unavailable -> conversationFailure(sessionId, text(R.string.conversation_offline_period))
            is ConversationResult.InvalidResponse -> conversationFailure(sessionId, text(R.string.invalid_conversation_response))
            is ConversationResult.Blocked -> conversationFailure(sessionId, text(R.string.child_input_blocked))
        }
    }

    private fun conversationFailure(sessionId: String, message: String) {
        _uiState.update {
            if (it.activeSessionId == sessionId) it.copy(conversationStatus = message) else it
        }
    }

    fun clearConversation() {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        stopVoiceConversation()
        if (!clearingSessions.add(sessionId)) return
        viewModelScope.launch {
            try {
                conversationOperations[sessionId]?.finished?.await()
                container.conversationRepository.clear(sessionId)
                resolveActiveSession(state.selectedAgeBand, null)
                _uiState.update {
                    if (it.selectedAgeBand == state.selectedAgeBand) it.copy(conversationStatus = text(R.string.conversation_cleared)) else it
                }
            } finally {
                clearingSessions -= sessionId
            }
        }
    }

    fun startVoiceConversation(): Boolean {
        val state = _uiState.value
        if (state.activeSessionId == null) return false
        if (state.voiceSession.isActive) return true
        if (isConversationBusy(state.activeSessionId)) return false
        if (state.settings.baseUrl.isBlank()) {
            conversationFailure(text(R.string.conversation_offline_period))
            return false
        }
        invalidatePractice()
        updateVoice(VoiceEvent.Start)
        listenForVoiceTurn()
        return true
    }

    fun retryVoiceConversation() {
        invalidateVoiceOperation()
        val sessionId = _uiState.value.activeSessionId ?: return
        if (isConversationBusy(sessionId)) return
        updateVoice(VoiceEvent.Retry)
        listenForVoiceTurn()
    }

    fun reportVoicePermissionError(message: String) {
        conversationFailure(message)
    }

    fun stopVoiceConversation() {
        invalidatePractice()
        invalidateVoiceOperation()
        updateVoice(VoiceEvent.Stop)
    }

    private fun invalidateVoiceOperation() {
        voiceOperationGeneration++
        val previous = voiceOperation
        voiceOperation = null
        if (previous != null && !previous.lease.running) finishConversationOperation(previous.lease)
        speechController.stopListening()
        speechController.stopSpeaking()
    }

    private data class VoiceOperation(
        val generation: Long,
        val sessionId: String,
        val ageBand: AgeBand,
        val lease: ConversationOperation,
        val speechRate: Float,
    )

    private fun isCurrentVoiceOperation(operation: VoiceOperation): Boolean {
        val state = _uiState.value
        return operation.generation == voiceOperationGeneration && voiceOperation === operation &&
            state.activeSessionId == operation.sessionId && state.selectedAgeBand == operation.ageBand &&
            state.voiceSession.isActive
    }

    private fun listenForVoiceTurn() {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: run {
            stopVoiceConversation()
            return
        }
        val lease = beginConversationOperation(sessionId) ?: run {
            stopVoiceConversation()
            return
        }
        val operation = VoiceOperation(
            // Retries and consecutive turns also need distinct identities within the same session.
            generation = ++voiceOperationGeneration,
            sessionId = sessionId,
            ageBand = state.selectedAgeBand,
            lease = lease,
            speechRate = state.settings.speechRate,
        )
        voiceOperation = operation
        speechController.startListening(
            onResult = { recognized ->
                viewModelScope.launch {
                    if (!isCurrentVoiceOperation(operation) || _uiState.value.voiceSession.phase != VoicePhase.LISTENING) {
                        return@launch
                    }
                    val normalized = recognized.trim()
                    if (TutorPolicy.validateInput(normalized) is PolicyDecision.Blocked) {
                        failVoice(text(R.string.child_input_blocked))
                    } else {
                        updateVoice(VoiceEvent.Heard(normalized))
                        sendVoiceTurn(operation, normalized)
                    }
                }
            },
            onError = { message ->
                viewModelScope.launch {
                    if (isCurrentVoiceOperation(operation) && _uiState.value.voiceSession.phase == VoicePhase.LISTENING) {
                        failVoice(message)
                    }
                }
            },
        )
    }

    private suspend fun sendVoiceTurn(operation: VoiceOperation, message: String) {
        if (!isCurrentVoiceOperation(operation)) return
        val result = try {
            requestConversation(operation.lease, operation.ageBand, message) { isCurrentVoiceOperation(operation) }
        } finally {
            if (!isCurrentVoiceOperation(operation)) finishConversationOperation(operation.lease)
        }
        if (!isCurrentVoiceOperation(operation)) return
        when (result) {
            is ConversationResult.Success -> {
                updateVoice(VoiceEvent.ReplyReady(result.outcome.reply))
                speechController.speak(
                    result.outcome.reply,
                    operation.speechRate,
                    style = SpeechStyle.CHILD_CONVERSATION,
                    onDone = {
                        viewModelScope.launch {
                            if (isCurrentVoiceOperation(operation) && _uiState.value.voiceSession.phase == VoicePhase.SPEAKING) {
                                finishConversationOperation(operation.lease)
                                updateVoice(VoiceEvent.SpeechFinished)
                                listenForVoiceTurn()
                            }
                        }
                    },
                    onError = { message ->
                        viewModelScope.launch {
                            if (isCurrentVoiceOperation(operation)) failVoice(message)
                        }
                    },
                )
            }
            is ConversationResult.AuthenticationRequired -> failVoice(text(R.string.authentication_required))
            is ConversationResult.Unavailable -> failVoice(text(R.string.conversation_offline_period))
            is ConversationResult.InvalidResponse -> failVoice(text(R.string.invalid_conversation_response))
            is ConversationResult.Blocked -> failVoice(text(R.string.child_input_blocked))
            null -> stopVoiceConversation()
        }
    }

    private fun failVoice(message: String) {
        invalidateVoiceOperation()
        updateVoice(VoiceEvent.Failure(message))
    }

    private fun updateVoice(event: VoiceEvent) {
        _uiState.update { it.copy(voiceSession = VoiceSessionReducer.reduce(it.voiceSession, event)) }
    }

    private fun conversationFailure(message: String) {
        _uiState.update { it.copy(conversationStatus = message) }
    }

    fun openStory(story: Story) {
        invalidatePractice()
        if (_uiState.value.currentStory?.id != story.id) resetTutorScope()
        navigator.showReader()
        _uiState.update {
            it.copy(screen = navigator.screen, currentStory = story, sentenceIndex = 0, speakingFeedback = null)
        }
        viewModelScope.launch { container.progressRepository.markStoryOpened(story.id) }
    }

    fun nextSentence() = moveSentence(1)
    fun previousSentence() = moveSentence(-1)

    private fun moveSentence(delta: Int) {
        invalidatePractice()
        _uiState.update { state ->
            val last = (state.sentences.size - 1).coerceAtLeast(0)
            state.copy(sentenceIndex = (state.sentenceIndex + delta).coerceIn(0, last), speakingFeedback = null)
        }
    }

    fun speakCurrentSentence() {
        val state = _uiState.value
        speechController.speak(state.currentSentence?.text.orEmpty(), state.settings.speechRate)
    }

    fun showPractice() {
        stopVoiceConversation()
        invalidatePractice()
        navigator.showPractice()
        _uiState.update { it.copy(screen = navigator.screen, speakingFeedback = null) }
    }

    fun startListening() {
        val state = _uiState.value
        if (state.isListening || state.screen != AppScreen.PRACTICE) return
        val story = state.currentStory ?: return
        val target = state.currentSentence?.text?.takeIf { it.isNotBlank() } ?: return
        val operation = PracticeOperation(state.selectedAgeBand, story.id, state.sentenceIndex, target)
        practiceOperation = operation
        _uiState.update { it.copy(isListening = true, speakingFeedback = null) }
        speechController.startListening(
            onResult = { recognized ->
                viewModelScope.launch {
                    if (!isCurrentPractice(operation)) return@launch
                    invalidatePractice()
                    val feedback = SpeakingScore.compare(operation.target, recognized)
                    _uiState.update { it.copy(isListening = false, speakingFeedback = feedback) }
                    container.progressRepository.recordSpeakingAttempt(operation.storyId, feedback.percent)
                }
            },
            onError = { message ->
                viewModelScope.launch {
                    if (!isCurrentPractice(operation)) return@launch
                    invalidatePractice()
                    _uiState.update {
                        it.copy(speakingFeedback = SpeakingFeedback(0, message, emptyList(), emptyList(), emptyList()))
                    }
                }
            },
        )
    }

    private data class PracticeOperation(val ageBand: AgeBand, val storyId: String, val sentenceIndex: Int, val target: String)

    private fun isCurrentPractice(operation: PracticeOperation): Boolean {
        val state = _uiState.value
        return practiceOperation === operation && state.selectedAgeBand == operation.ageBand &&
            state.currentStory?.id == operation.storyId && state.sentenceIndex == operation.sentenceIndex &&
            state.screen == AppScreen.PRACTICE
    }

    private fun invalidatePractice() {
        val wasListening = practiceOperation != null
        practiceOperation = null
        _uiState.update { it.copy(isListening = false) }
        if (wasListening) speechController.stopListening()
    }

    private fun resetTutorScope() {
        tutorGeneration++
        _uiState.update { it.copy(tutorMessages = emptyList(), tutorLoading = false, tutorStatus = "") }
    }

    fun showTutor() {
        invalidatePractice()
        navigator.showTutor()
        val offline = _uiState.value.settings.baseUrl.isBlank()
        _uiState.update { it.copy(screen = navigator.screen, tutorStatus = if (offline) text(R.string.tutor_offline) else "") }
    }

    fun sendTutorMessage(message: String) {
        val state = _uiState.value
        val story = state.currentStory ?: return
        if (message.isBlank() || message.length > 500 || state.tutorLoading) return
        if (TutorPolicy.validateInput(message) is PolicyDecision.Blocked) {
            tutorFailure(text(R.string.child_input_blocked))
            return
        }
        if (state.settings.baseUrl.isBlank()) {
            _uiState.update { it.copy(tutorStatus = text(R.string.tutor_offline)) }
            return
        }
        val userMessage = ChatMessage(ChatRole.USER, message.trim())
        val generation = tutorGeneration
        _uiState.update { it.copy(tutorMessages = it.tutorMessages + userMessage, tutorLoading = true, tutorStatus = "") }
        viewModelScope.launch {
            val result = tutorClient.chat(
                TutorRequest(
                    ageBand = state.selectedAgeBand,
                    story = story,
                    currentParagraphId = story.paragraphs.firstOrNull()?.id,
                    childMessage = message.trim(),
                    history = state.tutorMessages,
                ),
            )
            if (generation != tutorGeneration || _uiState.value.currentStory?.id != story.id ||
                _uiState.value.selectedAgeBand != state.selectedAgeBand) return@launch
            when (result) {
                is TutorResult.Success -> _uiState.update {
                    it.copy(
                        tutorLoading = false,
                        tutorMessages = it.tutorMessages + ChatMessage(ChatRole.ASSISTANT, result.text),
                    )
                }
                is TutorResult.AuthenticationRequired -> tutorFailure(text(R.string.authentication_required))
                is TutorResult.Unavailable -> tutorFailure(text(R.string.tutor_offline))
                is TutorResult.InvalidResponse -> tutorFailure(text(R.string.invalid_tutor_response))
                is TutorResult.Blocked -> tutorFailure(result.message)
            }
        }
    }

    private fun tutorFailure(message: String) {
        _uiState.update { it.copy(tutorLoading = false, tutorStatus = message) }
    }

    fun markStoryComplete() {
        _uiState.value.currentStory?.let { story ->
            viewModelScope.launch { container.progressRepository.markStoryCompleted(story.id) }
        }
    }

    fun showProgress() {
        invalidatePractice()
        navigator.showProgress()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    fun showSettings() {
        invalidatePractice()
        navigator.showSettings()
        _uiState.update { it.copy(screen = navigator.screen, connectionStatus = "") }
        refreshModels()
    }

    fun refreshModels() {
        if (_uiState.value.modelsLoading) return
        _uiState.update { it.copy(modelsLoading = true, modelDiscoveryStatus = text(R.string.loading_models)) }
        viewModelScope.launch { discoverModels() }
    }

    fun saveAndRefreshModels(baseUrl: String, model: String, token: String, allowDebugHttp: Boolean) {
        if (_uiState.value.modelsLoading) return
        _uiState.update { it.copy(modelsLoading = true, modelDiscoveryStatus = text(R.string.loading_models)) }
        viewModelScope.launch {
            saveSettingsValues(baseUrl, model, token, allowDebugHttp)
            discoverModels()
        }
    }

    private suspend fun discoverModels() {
        when (val result = conversationClient.availableModels()) {
            is ModelDiscoveryResult.Success -> {
                val installed = result.models
                if (installed.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            availableModels = ModelSelection.ordered(ModelSelection.fallbackModels + it.settings.modelName),
                            modelsLoading = false,
                            modelDiscoveryStatus = text(R.string.no_installed_models),
                        )
                    }
                } else {
                    val current = _uiState.value.settings.modelName
                    val selected = ModelSelection.resolveSelection(current, installed)
                    if (selected != current) container.settingsRepository.setModelName(selected)
                    _uiState.update {
                        it.copy(
                            availableModels = ModelSelection.ordered(installed + selected),
                            modelsLoading = false,
                            modelDiscoveryStatus = text(R.string.models_loaded, installed.size),
                        )
                    }
                }
            }
            ModelDiscoveryResult.Unavailable -> _uiState.update {
                it.copy(
                    availableModels = ModelSelection.ordered(ModelSelection.fallbackModels + it.settings.modelName),
                    modelsLoading = false,
                    modelDiscoveryStatus = text(R.string.models_fallback),
                )
            }
        }
    }

    fun saveSettings(baseUrl: String, model: String, token: String, allowDebugHttp: Boolean) {
        viewModelScope.launch {
            saveSettingsValues(baseUrl, model, token, allowDebugHttp)
            _uiState.update { it.copy(connectionStatus = text(R.string.settings_saved)) }
        }
    }

    private suspend fun saveSettingsValues(baseUrl: String, model: String, token: String, allowDebugHttp: Boolean) {
        container.settingsRepository.setBaseUrl(baseUrl)
        container.settingsRepository.setModelName(model.ifBlank { DEFAULT_CONVERSATION_MODEL })
        container.settingsRepository.setAllowDebugHttp(allowDebugHttp)
        if (token.isNotBlank()) container.secureTokenStore.save(token)
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { container.settingsRepository.setSpeechRate(rate) }
    }

    fun setTtsPreference(preference: TtsPreference) {
        viewModelScope.launch { container.settingsRepository.setTtsPreference(preference) }
    }

    fun testConnection() {
        _uiState.update { it.copy(connectionStatus = text(R.string.testing_connection)) }
        viewModelScope.launch {
            val status = when (val result = conversationClient.connectionTest()) {
                is ConversationResult.Success -> text(R.string.connection_success, result.outcome.reply)
                is ConversationResult.AuthenticationRequired -> text(R.string.authentication_required)
                is ConversationResult.Unavailable -> text(R.string.conversation_offline_period)
                is ConversationResult.InvalidResponse -> text(R.string.invalid_conversation_response)
                is ConversationResult.Blocked -> text(R.string.child_input_blocked)
            }
            _uiState.update { it.copy(connectionStatus = status) }
        }
    }

    fun back() {
        invalidatePractice()
        navigator.back()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    override fun onCleared() {
        invalidatePractice()
        resetTutorScope()
        stopVoiceConversation()
        speechController.shutdown()
        super.onCleared()
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
}
