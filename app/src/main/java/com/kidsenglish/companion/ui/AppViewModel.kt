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
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        (application as KidsEnglishApplication).container.speechController,
        application.container.conversationClient,
        application.container.conversationReplayController,
    )

    private val container = (application as KidsEnglishApplication).container
    private val navigator = LearningNavigator()
    private val pendingConversationSessions = mutableSetOf<String>()
    private var voiceOperationGeneration = 0L
    private var voiceOperation: VoiceOperation? = null
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                if (_uiState.value.selectedAgeBand != settings.ageBand) stopVoiceConversation()
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
                            conversationLoading = snapshot.sessionId in pendingConversationSessions,
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
        navigator.showConversation()
        _uiState.update {
            it.copy(
                screen = navigator.screen,
                conversationStatus = if (it.settings.baseUrl.isBlank()) text(R.string.conversation_offline) else "",
            )
        }
    }

    fun showLibrary() {
        navigator.showLibrary()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    fun showTopicPicker() {
        navigator.showTopicPicker()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    fun showConversationHistory() {
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
        val ageBand = _uiState.value.selectedAgeBand
        stopVoiceConversation()
        viewModelScope.launch {
            val sessionId = container.conversationRepository.createSession(ageBand, topic)
            pendingConversationSessions += sessionId
            container.settingsRepository.setActiveSessionId(ageBand, sessionId)
            val snapshot = container.conversationRepository.observe(sessionId).first()
            if (_uiState.value.selectedAgeBand == ageBand) showConversation()
            _uiState.update {
                if (it.activeSessionId == sessionId) it.copy(conversationLoading = true, conversationStatus = "") else it
            }
            val result = conversationClient.chat(
                ConversationRequest(
                    ageBand = ageBand,
                    childMessage = "",
                    history = snapshot.turns,
                    memory = snapshot.memory,
                    topic = snapshot.topic,
                    opening = true,
                ),
            )
            applyConversationResult(sessionId, result)
        }
    }

    fun sendConversationMessage(message: String) {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        val normalized = message.trim()
        if (normalized.isBlank() || normalized.length > 500 || state.conversationLoading) return
        if (state.settings.baseUrl.isBlank()) {
            _uiState.update { it.copy(conversationStatus = text(R.string.conversation_offline)) }
            return
        }
        pendingConversationSessions += sessionId
        _uiState.update { it.copy(conversationLoading = true, conversationStatus = "") }
        viewModelScope.launch {
            container.conversationRepository.appendUserTurn(sessionId, normalized)
            val result = conversationClient.chat(
                ConversationRequest(
                    ageBand = state.selectedAgeBand,
                    childMessage = normalized,
                    history = state.conversationTurns,
                    memory = state.conversationMemory,
                    topic = state.activeTopic,
                ),
            )
            applyConversationResult(sessionId, result)
        }
    }

    private suspend fun applyConversationResult(sessionId: String, result: ConversationResult) {
        pendingConversationSessions -= sessionId
        when (result) {
            is ConversationResult.Success -> {
                if (container.conversationRepository.sessionExists(sessionId)) {
                    container.conversationRepository.applyOutcome(sessionId, result.outcome)
                }
                _uiState.update { if (it.activeSessionId == sessionId) it.copy(conversationLoading = false) else it }
            }
            is ConversationResult.AuthenticationRequired -> conversationFailure(sessionId, text(R.string.authentication_required))
            is ConversationResult.Unavailable -> conversationFailure(sessionId, text(R.string.conversation_offline_period))
            is ConversationResult.InvalidResponse -> conversationFailure(sessionId, text(R.string.invalid_conversation_response))
        }
    }

    private fun conversationFailure(sessionId: String, message: String) {
        _uiState.update {
            if (it.activeSessionId == sessionId) it.copy(conversationLoading = false, conversationStatus = message) else it
        }
    }

    fun clearConversation() {
        val state = _uiState.value
        val sessionId = state.activeSessionId ?: return
        stopVoiceConversation()
        viewModelScope.launch {
            container.conversationRepository.clear(sessionId)
            resolveActiveSession(state.selectedAgeBand, null)
            _uiState.update {
                if (it.selectedAgeBand == state.selectedAgeBand) it.copy(conversationStatus = text(R.string.conversation_cleared)) else it
            }
        }
    }

    fun startVoiceConversation(): Boolean {
        val state = _uiState.value
        if (state.activeSessionId == null) return false
        if (state.voiceSession.isActive) return true
        if (state.settings.baseUrl.isBlank()) {
            conversationFailure(text(R.string.conversation_offline_period))
            return false
        }
        updateVoice(VoiceEvent.Start)
        listenForVoiceTurn()
        return true
    }

    fun retryVoiceConversation() {
        invalidateVoiceOperation()
        updateVoice(VoiceEvent.Retry)
        listenForVoiceTurn()
    }

    fun reportVoicePermissionError(message: String) {
        conversationFailure(message)
    }

    fun stopVoiceConversation() {
        invalidateVoiceOperation()
        updateVoice(VoiceEvent.Stop)
    }

    private fun invalidateVoiceOperation() {
        voiceOperationGeneration++
        voiceOperation = null
        speechController.stopListening()
        speechController.stopSpeaking()
    }

    private data class VoiceOperation(
        val generation: Long,
        val sessionId: String,
        val ageBand: AgeBand,
        val topic: ConversationTopic,
        val history: List<ConversationTurn>,
        val memory: ConversationMemory,
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
        val operation = VoiceOperation(
            // Retries and consecutive turns also need distinct identities within the same session.
            generation = ++voiceOperationGeneration,
            sessionId = sessionId,
            ageBand = state.selectedAgeBand,
            topic = state.activeTopic,
            history = state.conversationTurns,
            memory = state.conversationMemory,
            speechRate = state.settings.speechRate,
        )
        voiceOperation = operation
        speechController.startListening(
            onResult = { recognized ->
                viewModelScope.launch {
                    if (!isCurrentVoiceOperation(operation) || _uiState.value.voiceSession.phase != VoicePhase.LISTENING) {
                        return@launch
                    }
                    updateVoice(VoiceEvent.Heard(recognized))
                    sendVoiceTurn(operation, recognized)
                }
            },
            onError = { message ->
                viewModelScope.launch {
                    if (isCurrentVoiceOperation(operation) && _uiState.value.voiceSession.phase == VoicePhase.LISTENING) {
                        updateVoice(VoiceEvent.Failure(message))
                    }
                }
            },
        )
    }

    private suspend fun sendVoiceTurn(operation: VoiceOperation, message: String) {
        if (!isCurrentVoiceOperation(operation)) return
        container.conversationRepository.appendUserTurn(operation.sessionId, message)
        if (!isCurrentVoiceOperation(operation)) return
        val result = conversationClient.chat(
            ConversationRequest(
                ageBand = operation.ageBand,
                childMessage = message,
                history = operation.history,
                memory = operation.memory,
                topic = operation.topic,
            ),
        )
        if (!isCurrentVoiceOperation(operation)) return
        when (result) {
            is ConversationResult.Success -> {
                if (!container.conversationRepository.sessionExists(operation.sessionId) || !isCurrentVoiceOperation(operation)) {
                    return
                }
                container.conversationRepository.applyOutcome(operation.sessionId, result.outcome)
                if (!isCurrentVoiceOperation(operation)) return
                updateVoice(VoiceEvent.ReplyReady(result.outcome.reply))
                speechController.speak(
                    result.outcome.reply,
                    operation.speechRate,
                    style = SpeechStyle.CHILD_CONVERSATION,
                    onDone = {
                        viewModelScope.launch {
                            if (isCurrentVoiceOperation(operation) && _uiState.value.voiceSession.phase == VoicePhase.SPEAKING) {
                                updateVoice(VoiceEvent.SpeechFinished)
                                listenForVoiceTurn()
                            }
                        }
                    },
                    onError = { message ->
                        viewModelScope.launch {
                            if (isCurrentVoiceOperation(operation)) updateVoice(VoiceEvent.Failure(message))
                        }
                    },
                )
            }
            is ConversationResult.AuthenticationRequired -> updateVoice(VoiceEvent.Failure(text(R.string.authentication_required)))
            is ConversationResult.Unavailable -> updateVoice(VoiceEvent.Failure(text(R.string.conversation_offline_period)))
            is ConversationResult.InvalidResponse -> updateVoice(VoiceEvent.Failure(text(R.string.invalid_conversation_response)))
        }
    }

    private fun updateVoice(event: VoiceEvent) {
        _uiState.update { it.copy(voiceSession = VoiceSessionReducer.reduce(it.voiceSession, event)) }
    }

    private fun conversationFailure(message: String) {
        _uiState.update { it.copy(conversationLoading = false, conversationStatus = message) }
    }

    fun openStory(story: Story) {
        navigator.showReader()
        _uiState.update {
            it.copy(screen = navigator.screen, currentStory = story, sentenceIndex = 0, speakingFeedback = null)
        }
        viewModelScope.launch { container.progressRepository.markStoryOpened(story.id) }
    }

    fun nextSentence() = moveSentence(1)
    fun previousSentence() = moveSentence(-1)

    private fun moveSentence(delta: Int) {
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
        navigator.showPractice()
        _uiState.update { it.copy(screen = navigator.screen, speakingFeedback = null) }
    }

    fun startListening() {
        if (_uiState.value.isListening) return
        _uiState.update { it.copy(isListening = true, speakingFeedback = null) }
        speechController.startListening(
            onResult = { recognized ->
                val state = _uiState.value
                val target = state.currentSentence?.text.orEmpty()
                if (target.isNotBlank()) {
                    val feedback = SpeakingScore.compare(target, recognized)
                    _uiState.update { it.copy(isListening = false, speakingFeedback = feedback) }
                    state.currentStory?.let { story ->
                        viewModelScope.launch { container.progressRepository.recordSpeakingAttempt(story.id, feedback.percent) }
                    }
                }
            },
            onError = { message ->
                _uiState.update {
                    it.copy(
                        isListening = false,
                        speakingFeedback = SpeakingFeedback(0, message, emptyList(), emptyList(), emptyList()),
                    )
                }
            },
        )
    }

    fun showTutor() {
        navigator.showTutor()
        val offline = _uiState.value.settings.baseUrl.isBlank()
        _uiState.update { it.copy(screen = navigator.screen, tutorStatus = if (offline) text(R.string.tutor_offline) else "") }
    }

    fun sendTutorMessage(message: String) {
        val state = _uiState.value
        val story = state.currentStory ?: return
        if (message.isBlank() || message.length > 500 || state.tutorLoading) return
        if (state.settings.baseUrl.isBlank()) {
            _uiState.update { it.copy(tutorStatus = text(R.string.tutor_offline)) }
            return
        }
        val userMessage = ChatMessage(ChatRole.USER, message.trim())
        _uiState.update { it.copy(tutorMessages = it.tutorMessages + userMessage, tutorLoading = true, tutorStatus = "") }
        viewModelScope.launch {
            val result = container.tutorClient.chat(
                TutorRequest(
                    ageBand = state.selectedAgeBand,
                    story = story,
                    currentParagraphId = story.paragraphs.firstOrNull()?.id,
                    childMessage = message.trim(),
                    history = state.tutorMessages,
                ),
            )
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
        navigator.showProgress()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    fun showSettings() {
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
            }
            _uiState.update { it.copy(connectionStatus = status) }
        }
    }

    fun back() {
        navigator.back()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    override fun onCleared() {
        stopVoiceConversation()
        speechController.shutdown()
        super.onCleared()
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
}
