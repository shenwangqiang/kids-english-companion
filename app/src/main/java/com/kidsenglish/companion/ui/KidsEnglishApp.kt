package com.kidsenglish.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsenglish.companion.R
import com.kidsenglish.companion.conversation.ConversationRole
import com.kidsenglish.companion.conversation.VoicePhase
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.speech.TtsPreference
import com.kidsenglish.companion.tutor.ChatRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsEnglishApp(
    viewModel: AppViewModel,
    onRequestPracticeMicrophone: () -> Unit,
    onRequestVoiceMicrophone: () -> Unit,
    onStopVoiceConversation: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (state.screen != AppScreen.WELCOME) TextButton(onClick = viewModel::back) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        when (state.screen) {
            AppScreen.WELCOME -> WelcomeScreen(Modifier.padding(padding), viewModel::chooseAgeBand)
            // Task 4 replaces the topic/history placeholders with their dedicated screens.
            AppScreen.CONVERSATION, AppScreen.TOPICS, AppScreen.HISTORY -> ConversationScreen(
                Modifier.padding(padding),
                state,
                viewModel,
                onRequestVoiceMicrophone,
                onStopVoiceConversation,
            )
            AppScreen.LIBRARY -> LibraryScreen(Modifier.padding(padding), state, viewModel)
            AppScreen.READER -> ReaderScreen(Modifier.padding(padding), state, viewModel)
            AppScreen.PRACTICE -> PracticeScreen(Modifier.padding(padding), state, viewModel, onRequestPracticeMicrophone)
            AppScreen.TUTOR -> TutorScreen(Modifier.padding(padding), state, viewModel)
            AppScreen.PROGRESS -> ProgressScreen(Modifier.padding(padding), state)
            AppScreen.SETTINGS -> SettingsScreen(Modifier.padding(padding), state, viewModel)
        }
    }
}

@Composable
private fun WelcomeScreen(modifier: Modifier, onChoose: (AgeBand) -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.choose_age), modifier = Modifier.padding(vertical = 16.dp))
        AgeBand.entries.forEach { band ->
            Button(onClick = { onChoose(band) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(band.localizedLabel()) }
        }
    }
}

@Composable
private fun ConversationScreen(
    modifier: Modifier,
    state: AppUiState,
    viewModel: AppViewModel,
    requestVoiceMicrophone: () -> Unit,
    stopVoiceConversation: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.conversationTurns.size) {
        if (state.conversationTurns.isNotEmpty()) {
            listState.animateScrollToItem(state.conversationTurns.size)
        }
    }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "conversation-header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.conversation_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.conversation_subtitle, state.selectedAgeBand.localizedLabel()))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = viewModel::showLibrary) { Text(stringResource(R.string.stories)) }
                        TextButton(onClick = viewModel::showProgress) { Text(stringResource(R.string.progress)) }
                        TextButton(onClick = viewModel::showSettings) { Text(stringResource(R.string.settings)) }
                    }
                    if (state.conversationStatus.isNotBlank()) {
                        Text(state.conversationStatus, color = MaterialTheme.colorScheme.error)
                    }
                    if (state.voiceSession.isActive) {
                        val voiceLabel = when (state.voiceSession.phase) {
                            VoicePhase.LISTENING -> stringResource(R.string.voice_listening)
                            VoicePhase.THINKING -> stringResource(R.string.voice_thinking)
                            VoicePhase.SPEAKING -> stringResource(R.string.voice_speaking)
                            VoicePhase.ERROR -> state.voiceSession.message
                            VoicePhase.IDLE -> ""
                        }
                        Text(stringResource(R.string.voice_status, voiceLabel))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.voiceSession.phase == VoicePhase.ERROR) {
                                OutlinedButton(onClick = viewModel::retryVoiceConversation, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                            Button(onClick = stopVoiceConversation, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.stop_voice_conversation))
                            }
                        }
                    } else {
                        OutlinedButton(onClick = requestVoiceMicrophone, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.start_voice))
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(stringResource(R.string.memory_title), fontWeight = FontWeight.Bold)
                            Text(state.conversationMemory.summary.ifBlank { stringResource(R.string.memory_empty) })
                            state.conversationMemory.rememberedFacts.forEach { fact -> Text("• $fact") }
                            if (state.conversationMemory.nextDirection.isNotBlank()) {
                                Text(stringResource(R.string.next_direction, state.conversationMemory.nextDirection))
                            }
                        }
                    }
                }
            }
            if (state.conversationTurns.isEmpty()) {
                item(key = "conversation-empty") { Text(stringResource(R.string.conversation_empty)) }
            }
            items(state.conversationTurns) { turn ->
                val speaker = if (turn.role == ConversationRole.USER) stringResource(R.string.speaker_user) else stringResource(R.string.speaker_coach)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (turn.role == ConversationRole.USER) Arrangement.End else Arrangement.Start,
                ) {
                    Card(Modifier.widthIn(max = 320.dp)) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(speaker, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(turn.content)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(500) },
            label = { Text(stringResource(R.string.message_input)) },
            supportingText = { Text("${input.length}/500") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (input.isNotBlank()) {
                    viewModel.sendConversationMessage(input)
                    input = ""
                }
            },
            enabled = input.isNotBlank() && !state.conversationLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.conversationLoading) stringResource(R.string.thinking) else stringResource(R.string.send)) }
        TextButton(onClick = viewModel::clearConversation, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.clear_conversation))
        }
    }
}

@Composable
private fun LibraryScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(state.selectedAgeBand.localizedLabel(), style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = viewModel::showConversation) { Text(stringResource(R.string.talk)) }
                TextButton(onClick = viewModel::showProgress) { Text(stringResource(R.string.progress)) }
                TextButton(onClick = viewModel::showSettings) { Text(stringResource(R.string.qwen_settings)) }
            }
        }
        items(state.stories, key = { it.id }) { story ->
            Card(onClick = { viewModel.openStory(story) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(story.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${story.cefr} · ${story.summary}", modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel) {
    val story = state.currentStory ?: return
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(story.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.sentence_progress, state.sentenceIndex + 1, state.sentences.size))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(state.currentSentence?.text.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text(state.currentSentence?.chineseHint.orEmpty(), modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(onClick = viewModel::speakCurrentSentence, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.read_sentence)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = viewModel::previousSentence) { Text(stringResource(R.string.previous)) }
            OutlinedButton(onClick = viewModel::nextSentence) { Text(stringResource(R.string.next)) }
        }
        Button(onClick = viewModel::showPractice, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.practice_sentence)) }
        OutlinedButton(onClick = viewModel::showTutor, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ask_story)) }
        TextButton(onClick = viewModel::markStoryComplete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.mark_complete)) }
    }
}

@Composable
private fun PracticeScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel, requestMic: () -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.listen_repeat), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(state.currentSentence?.text.orEmpty(), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = viewModel::speakCurrentSentence, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.read_sentence)) }
        Button(onClick = requestMic, enabled = !state.isListening, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isListening) stringResource(R.string.voice_listening) else stringResource(R.string.hold_to_speak))
        }
        state.speakingFeedback?.let { feedback ->
            Text(if (feedback.percent > 0) stringResource(R.string.speaking_score, feedback.percent) else feedback.recognizedText)
            if (feedback.missedWords.isNotEmpty()) Text(stringResource(R.string.retry_words, feedback.missedWords.joinToString()))
            OutlinedButton(onClick = requestMic, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.retry)) }
        }
        Text(stringResource(R.string.score_disclaimer))
        Text(stringResource(R.string.speech_availability))
    }
}

@Composable
private fun TutorScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel) {
    var input by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.story_helper), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.story_helper_notice))
        if (state.tutorStatus.isNotBlank()) Text(state.tutorStatus, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.tutorMessages) { message ->
                val speaker = if (message.role == ChatRole.USER) stringResource(R.string.speaker_user) else stringResource(R.string.speaker_tutor)
                Card(Modifier.fillMaxWidth()) { Text("$speaker: ${message.content}", Modifier.padding(12.dp)) }
            }
        }
        OutlinedTextField(input, { input = it.take(500) }, label = { Text(stringResource(R.string.ask_story)) }, supportingText = { Text("${input.length}/500") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { viewModel.sendTutorMessage(input) },
            enabled = input.isNotBlank() && !state.tutorLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.tutorLoading) stringResource(R.string.thinking) else stringResource(R.string.ask)) }
    }
}

@Composable
private fun ProgressScreen(modifier: Modifier, state: AppUiState) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.progress), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (state.progress.isEmpty()) item { Text(stringResource(R.string.progress_empty)) }
        items(state.progress, key = { it.storyId }) { item ->
            val title = state.stories.firstOrNull { it.id == item.storyId }?.title ?: item.storyId
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.speaking_progress, item.speakingAttempts, item.bestSpeakingScore))
                    Text(if (item.completedAt != null) stringResource(R.string.completed) else stringResource(R.string.in_progress))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel) {
    var baseUrl by remember { mutableStateOf(state.settings.baseUrl) }
    var model by remember { mutableStateOf(state.settings.modelName) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var manualModel by remember { mutableStateOf(state.settings.modelName !in state.availableModels) }
    var ttsMenuExpanded by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(state.settings.allowDebugHttp) }
    LaunchedEffect(state.settings.baseUrl, state.settings.modelName) {
        baseUrl = state.settings.baseUrl
        model = state.settings.modelName
        manualModel = state.settings.modelName !in state.availableModels
    }
    LazyColumn(
        modifier.fillMaxSize().testTag("settings_list").padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.qwen_service), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(stringResource(R.string.qwen_service_help)) }
        item { OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text(stringResource(R.string.base_url)) }, placeholder = { Text("http://10.0.2.2:11434") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { modelMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(model.ifBlank { stringResource(R.string.installed_model) })
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false },
                ) {
                    state.availableModels.forEach { availableModel ->
                        DropdownMenuItem(
                            text = { Text(availableModel) },
                            onClick = {
                                model = availableModel
                                manualModel = false
                                modelMenuExpanded = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manual_model)) },
                        onClick = {
                            manualModel = true
                            modelMenuExpanded = false
                        },
                    )
                }
            }
        }
        if (manualModel) {
            item {
                OutlinedTextField(
                    model,
                    { model = it },
                    label = { Text(stringResource(R.string.model_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.saveAndRefreshModels(baseUrl, model, token, allowHttp) },
                enabled = !state.modelsLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.modelsLoading) stringResource(R.string.loading_models) else stringResource(R.string.refresh_models))
            }
        }
        if (state.modelDiscoveryStatus.isNotBlank()) item { Text(state.modelDiscoveryStatus) }
        item { OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.bearer_token)) }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.fillMaxWidth()) {
                Checkbox(allowHttp, { allowHttp = it })
                Text(stringResource(R.string.allow_debug_http), Modifier.padding(top = 12.dp))
            }
        }
        item { Button(onClick = { viewModel.saveSettings(baseUrl, model, token, allowHttp) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) } }
        item { OutlinedButton(onClick = viewModel::testConnection, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.test_connection)) } }
        if (state.connectionStatus.isNotBlank()) item { Text(state.connectionStatus) }
        item { HorizontalDivider() }
        item { Text(stringResource(R.string.tts_engine), fontWeight = FontWeight.Bold) }
        item {
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { ttsMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(state.settings.ttsPreference.localizedLabel()) }
                DropdownMenu(
                    expanded = ttsMenuExpanded,
                    onDismissRequest = { ttsMenuExpanded = false },
                ) {
                    TtsPreference.entries.forEach { preference ->
                        DropdownMenuItem(
                            text = { Text(preference.localizedLabel()) },
                            onClick = {
                                viewModel.setTtsPreference(preference)
                                ttsMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.reading_speed, state.settings.speechRate))
            Slider(state.settings.speechRate, viewModel::setSpeechRate, valueRange = 0.6f..1.2f)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TtsPreference.localizedLabel(): String = when (this) {
    TtsPreference.AUTO -> stringResource(R.string.tts_auto)
    TtsPreference.GOOGLE -> stringResource(R.string.tts_google)
    TtsPreference.IFLYTEK -> stringResource(R.string.tts_iflytek)
}

@Composable
private fun AgeBand.localizedLabel(): String = when (this) {
    AgeBand.AGE_6_8 -> stringResource(R.string.age_6_8)
    AgeBand.AGE_9_10 -> stringResource(R.string.age_9_10)
    AgeBand.AGE_11_12 -> stringResource(R.string.age_11_12)
}
