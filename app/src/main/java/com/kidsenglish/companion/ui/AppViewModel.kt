package com.kidsenglish.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kidsenglish.companion.KidsEnglishApplication
import com.kidsenglish.companion.R
import com.kidsenglish.companion.conversation.ConversationMemory
import com.kidsenglish.companion.conversation.ConversationRequest
import com.kidsenglish.companion.conversation.ConversationResult
import com.kidsenglish.companion.conversation.DEFAULT_CONVERSATION_MODEL
import com.kidsenglish.companion.conversation.ConversationTurn
import com.kidsenglish.companion.conversation.ModelDiscoveryResult
import com.kidsenglish.companion.conversation.ModelSelection
import com.kidsenglish.companion.conversation.VoiceEvent
import com.kidsenglish.companion.conversation.VoicePhase
import com.kidsenglish.companion.conversation.VoiceSessionReducer
import com.kidsenglish.companion.conversation.VoiceSessionState
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
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as KidsEnglishApplication).container
    private val navigator = LearningNavigator()
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
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
                .collect(container.speechController::setTtsPreference)
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
                    container.conversationRepository.observe(ageBand).map { ageBand to it }
                }
                .collect { (ageBand, snapshot) ->
                    _uiState.update { state ->
                        if (state.selectedAgeBand != ageBand) state else state.copy(
                            conversationTurns = snapshot.turns,
                            conversationMemory = snapshot.memory,
                        )
                    }
                }
        }
    }

    fun chooseAgeBand(ageBand: AgeBand) {
        navigator.showConversation()
        _uiState.update {
            val ageChanged = it.selectedAgeBand != ageBand
            it.copy(
                screen = navigator.screen,
                selectedAgeBand = ageBand,
                stories = container.storyRepository.stories(ageBand),
                currentStory = null,
                conversationTurns = if (ageChanged) emptyList() else it.conversationTurns,
                conversationMemory = if (ageChanged) ConversationMemory() else it.conversationMemory,
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

    fun sendConversationMessage(message: String) {
        val state = _uiState.value
        val normalized = message.trim()
        if (normalized.isBlank() || normalized.length > 500 || state.conversationLoading) return
        if (state.settings.baseUrl.isBlank()) {
            _uiState.update { it.copy(conversationStatus = text(R.string.conversation_offline)) }
            return
        }
        _uiState.update { it.copy(conversationLoading = true, conversationStatus = "") }
        viewModelScope.launch {
            container.conversationRepository.appendUserTurn(state.selectedAgeBand, normalized)
            val result = container.conversationClient.chat(
                ConversationRequest(
                    ageBand = state.selectedAgeBand,
                    childMessage = normalized,
                    history = state.conversationTurns,
                    memory = state.conversationMemory,
                ),
            )
            when (result) {
                is ConversationResult.Success -> {
                    container.conversationRepository.applyOutcome(state.selectedAgeBand, result.outcome)
                    _uiState.update { it.copy(conversationLoading = false) }
                }
                is ConversationResult.AuthenticationRequired -> conversationFailure(text(R.string.authentication_required))
                is ConversationResult.Unavailable -> conversationFailure(text(R.string.conversation_offline_period))
                is ConversationResult.InvalidResponse -> conversationFailure(text(R.string.invalid_conversation_response))
            }
        }
    }

    fun clearConversation() {
        val ageBand = _uiState.value.selectedAgeBand
        viewModelScope.launch {
            container.conversationRepository.clear(ageBand)
            _uiState.update { it.copy(conversationStatus = text(R.string.conversation_cleared)) }
        }
    }

    fun startVoiceConversation(): Boolean {
        val state = _uiState.value
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
        updateVoice(VoiceEvent.Retry)
        listenForVoiceTurn()
    }

    fun reportVoicePermissionError(message: String) {
        conversationFailure(message)
    }

    fun stopVoiceConversation() {
        container.speechController.stopListening()
        container.speechController.stopSpeaking()
        updateVoice(VoiceEvent.Stop)
    }

    private fun listenForVoiceTurn() {
        container.speechController.startListening(
            onResult = { recognized ->
                if (!_uiState.value.voiceSession.isActive) return@startListening
                updateVoice(VoiceEvent.Heard(recognized))
                sendVoiceTurn(recognized)
            },
            onError = { message ->
                if (_uiState.value.voiceSession.isActive) updateVoice(VoiceEvent.Failure(message))
            },
        )
    }

    private fun sendVoiceTurn(message: String) {
        val state = _uiState.value
        viewModelScope.launch {
            container.conversationRepository.appendUserTurn(state.selectedAgeBand, message)
            when (val result = container.conversationClient.chat(
                ConversationRequest(
                    ageBand = state.selectedAgeBand,
                    childMessage = message,
                    history = state.conversationTurns,
                    memory = state.conversationMemory,
                ),
            )) {
                is ConversationResult.Success -> {
                    container.conversationRepository.applyOutcome(state.selectedAgeBand, result.outcome)
                    if (!_uiState.value.voiceSession.isActive) return@launch
                    updateVoice(VoiceEvent.ReplyReady(result.outcome.reply))
                    container.speechController.speak(
                        result.outcome.reply,
                        state.settings.speechRate,
                        style = SpeechStyle.CHILD_CONVERSATION,
                        onDone = {
                            viewModelScope.launch {
                                if (_uiState.value.voiceSession.phase == VoicePhase.SPEAKING) {
                                    updateVoice(VoiceEvent.SpeechFinished)
                                    listenForVoiceTurn()
                                }
                            }
                        },
                        onError = { message ->
                            viewModelScope.launch { updateVoice(VoiceEvent.Failure(message)) }
                        },
                    )
                }
                is ConversationResult.AuthenticationRequired -> updateVoice(VoiceEvent.Failure(text(R.string.authentication_required)))
                is ConversationResult.Unavailable -> updateVoice(VoiceEvent.Failure(text(R.string.conversation_offline_period)))
                is ConversationResult.InvalidResponse -> updateVoice(VoiceEvent.Failure(text(R.string.invalid_conversation_response)))
            }
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
        container.speechController.speak(state.currentSentence?.text.orEmpty(), state.settings.speechRate)
    }

    fun showPractice() {
        navigator.showPractice()
        _uiState.update { it.copy(screen = navigator.screen, speakingFeedback = null) }
    }

    fun startListening() {
        if (_uiState.value.isListening) return
        _uiState.update { it.copy(isListening = true, speakingFeedback = null) }
        container.speechController.startListening(
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
        when (val result = container.conversationClient.availableModels()) {
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
            val status = when (val result = container.conversationClient.connectionTest()) {
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
        container.speechController.shutdown()
        super.onCleared()
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)
}
