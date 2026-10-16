package com.kidsenglish.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kidsenglish.companion.KidsEnglishApplication
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
    val progress: List<LearningProgressEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val connectionStatus: String = "",
) {
    val sentences: List<StorySentence> get() = currentStory?.paragraphs?.flatMap { it.sentences }.orEmpty()
    val currentSentence: StorySentence? get() = sentences.getOrNull(sentenceIndex)
}

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
            container.progressRepository.observeAll().collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
    }

    fun chooseAgeBand(ageBand: AgeBand) {
        navigator.showLibrary()
        _uiState.update {
            it.copy(
                screen = navigator.screen,
                selectedAgeBand = ageBand,
                stories = container.storyRepository.stories(ageBand),
                currentStory = null,
            )
        }
        viewModelScope.launch { container.settingsRepository.setAgeBand(ageBand) }
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
        _uiState.update { it.copy(screen = navigator.screen, tutorStatus = if (offline) "AI tutor is offline" else "") }
    }

    fun sendTutorMessage(message: String) {
        val state = _uiState.value
        val story = state.currentStory ?: return
        if (message.isBlank()) return
        if (state.settings.baseUrl.isBlank()) {
            _uiState.update { it.copy(tutorStatus = "AI tutor is offline") }
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
                is TutorResult.AuthenticationRequired -> tutorFailure(result.message)
                is TutorResult.Unavailable -> tutorFailure(result.message)
                is TutorResult.InvalidResponse -> tutorFailure(result.message)
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
    }

    fun saveSettings(baseUrl: String, model: String, token: String, allowDebugHttp: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setBaseUrl(baseUrl)
            container.settingsRepository.setModelName(model.ifBlank { "qwen3:4b" })
            container.settingsRepository.setAllowDebugHttp(allowDebugHttp)
            if (token.isNotBlank()) container.secureTokenStore.save(token)
            _uiState.update { it.copy(connectionStatus = "Settings saved") }
        }
    }

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch { container.settingsRepository.setSpeechRate(rate) }
    }

    fun testConnection() {
        _uiState.update { it.copy(connectionStatus = "Testing…") }
        viewModelScope.launch {
            val status = when (val result = container.tutorClient.connectionTest()) {
                is TutorResult.Success -> "Connection successful"
                is TutorResult.AuthenticationRequired -> result.message
                is TutorResult.Unavailable -> result.message
                is TutorResult.InvalidResponse -> result.message
                is TutorResult.Blocked -> result.message
            }
            _uiState.update { it.copy(connectionStatus = status) }
        }
    }

    fun back() {
        navigator.back()
        _uiState.update { it.copy(screen = navigator.screen) }
    }

    override fun onCleared() {
        container.speechController.shutdown()
        super.onCleared()
    }
}
