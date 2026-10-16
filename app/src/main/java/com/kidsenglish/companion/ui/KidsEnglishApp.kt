package com.kidsenglish.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.tutor.ChatRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KidsEnglishApp(viewModel: AppViewModel, onRequestMicrophone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kids English Companion") },
                navigationIcon = {
                    if (state.screen != AppScreen.WELCOME) TextButton(onClick = viewModel::back) { Text("Back") }
                },
            )
        },
    ) { padding ->
        when (state.screen) {
            AppScreen.WELCOME -> WelcomeScreen(Modifier.padding(padding), viewModel::chooseAgeBand)
            AppScreen.LIBRARY -> LibraryScreen(Modifier.padding(padding), state, viewModel)
            AppScreen.READER -> ReaderScreen(Modifier.padding(padding), state, viewModel)
            AppScreen.PRACTICE -> PracticeScreen(Modifier.padding(padding), state, viewModel, onRequestMicrophone)
            AppScreen.TUTOR -> TutorScreen(Modifier.padding(padding), state, viewModel)
            AppScreen.PROGRESS -> ProgressScreen(Modifier.padding(padding), state)
            AppScreen.SETTINGS -> SettingsScreen(Modifier.padding(padding), state, viewModel)
        }
    }
}

@Composable
private fun WelcomeScreen(modifier: Modifier, onChoose: (AgeBand) -> Unit) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Read, listen, speak, and ask", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Choose the reader's age group. You can change it later.", modifier = Modifier.padding(vertical = 16.dp))
        AgeBand.entries.forEach { band ->
            Button(onClick = { onChoose(band) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(band.label) }
        }
    }
}

@Composable
private fun LibraryScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(state.selectedAgeBand.label, style = MaterialTheme.typography.headlineSmall)
            Row {
                TextButton(onClick = viewModel::showProgress) { Text("Progress") }
                TextButton(onClick = viewModel::showSettings) { Text("Qwen settings") }
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
        Text("Sentence ${state.sentenceIndex + 1} of ${state.sentences.size}")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(state.currentSentence?.text.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text(state.currentSentence?.chineseHint.orEmpty(), modifier = Modifier.padding(top = 12.dp))
            }
        }
        Button(onClick = viewModel::speakCurrentSentence, modifier = Modifier.fillMaxWidth()) { Text("Read sentence") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = viewModel::previousSentence) { Text("Previous") }
            OutlinedButton(onClick = viewModel::nextSentence) { Text("Next") }
        }
        Button(onClick = viewModel::showPractice, modifier = Modifier.fillMaxWidth()) { Text("Practice this sentence") }
        OutlinedButton(onClick = viewModel::showTutor, modifier = Modifier.fillMaxWidth()) { Text("Ask about this story") }
        TextButton(onClick = viewModel::markStoryComplete, modifier = Modifier.fillMaxWidth()) { Text("Mark story complete") }
    }
}

@Composable
private fun PracticeScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel, requestMic: () -> Unit) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Listen and repeat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(state.currentSentence?.text.orEmpty(), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = viewModel::speakCurrentSentence, modifier = Modifier.fillMaxWidth()) { Text("Read sentence") }
        Button(onClick = requestMic, enabled = !state.isListening, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.isListening) "Listening…" else "Hold to speak")
        }
        state.speakingFeedback?.let { feedback ->
            Text(if (feedback.percent > 0) "Speaking score: ${feedback.percent}%" else feedback.recognizedText)
            if (feedback.missedWords.isNotEmpty()) Text("Try these words again: ${feedback.missedWords.joinToString()}")
            OutlinedButton(onClick = requestMic, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        }
        Text("This is a word-match practice score, not a professional pronunciation assessment.")
        Text("Speech recognition availability depends on the Android device. Reading and audio still work offline.")
    }
}

@Composable
private fun TutorScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel) {
    var input by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Story helper", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("The tutor answers from the selected story. AI can make mistakes; an adult should review important guidance.")
        if (state.tutorStatus.isNotBlank()) Text(state.tutorStatus, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.tutorMessages) { message ->
                val speaker = if (message.role == ChatRole.USER) "You" else "Tutor"
                Card(Modifier.fillMaxWidth()) { Text("$speaker: ${message.content}", Modifier.padding(12.dp)) }
            }
        }
        OutlinedTextField(input, { input = it.take(500) }, label = { Text("Ask about this story") }, supportingText = { Text("${input.length}/500") }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { viewModel.sendTutorMessage(input) },
            enabled = input.isNotBlank() && !state.tutorLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.tutorLoading) "Thinking…" else "Ask") }
    }
}

@Composable
private fun ProgressScreen(modifier: Modifier, state: AppUiState) {
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Learning progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (state.progress.isEmpty()) item { Text("Open a story to begin tracking progress.") }
        items(state.progress, key = { it.storyId }) { item ->
            val title = state.stories.firstOrNull { it.id == item.storyId }?.title ?: item.storyId
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text("Speaking attempts: ${item.speakingAttempts} · Best: ${item.bestSpeakingScore}%")
                    Text(if (item.completedAt != null) "Completed" else "In progress")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, state: AppUiState, viewModel: AppViewModel) {
    var baseUrl by remember { mutableStateOf(state.settings.baseUrl) }
    var model by remember { mutableStateOf(state.settings.modelName) }
    var token by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(state.settings.allowDebugHttp) }
    LaunchedEffect(state.settings.baseUrl, state.settings.modelName) {
        baseUrl = state.settings.baseUrl
        model = state.settings.modelName
    }
    LazyColumn(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Qwen service", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text("Use an OpenAI-compatible Qwen endpoint from Ollama, vLLM, or another server. The app remains useful without one.") }
        item { OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, placeholder = { Text("http://10.0.2.2:11434/v1") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(token, { token = it }, label = { Text("Bearer token (optional)") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.fillMaxWidth()) {
                Checkbox(allowHttp, { allowHttp = it })
                Text("Allow private-network HTTP in debug builds", Modifier.padding(top = 12.dp))
            }
        }
        item { Button(onClick = { viewModel.saveSettings(baseUrl, model, token, allowHttp) }, modifier = Modifier.fillMaxWidth()) { Text("Save") } }
        item { OutlinedButton(onClick = viewModel::testConnection, modifier = Modifier.fillMaxWidth()) { Text("Test connection") } }
        if (state.connectionStatus.isNotBlank()) item { Text(state.connectionStatus) }
        item { HorizontalDivider() }
        item {
            Text("Reading speed: ${"%.1f".format(state.settings.speechRate)}×")
            Slider(state.settings.speechRate, viewModel::setSpeechRate, valueRange = 0.6f..1.2f)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
