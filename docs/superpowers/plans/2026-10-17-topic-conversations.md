# Topic Conversations and Multi-Session History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add portrait-only, topic-led English conversations with tap-to-replay, independent persisted sessions, new-conversation creation, and history reopening.

**Architecture:** Introduce a small topic domain model, then convert the Room conversation repository from one fixed session per age band to explicit session IDs. Persist the active session for each age band in DataStore, coordinate it in `AppViewModel`, and expose topic/history screens through the existing Compose navigator. Keep Ollama prompts session-local and use the existing TTS controller for message replay.

**Tech Stack:** Kotlin, Android 26+, Jetpack Compose Material 3, Room 2.7, DataStore Preferences, Kotlin Coroutines/Flow, JUnit 4, AndroidX instrumentation, Ollama `/api/chat`.

**Spec:** `docs/superpowers/specs/2026-10-17-topic-conversations-design.md`

## Global Constraints

- Do not bundle, quote, reconstruct, or scrape copyrighted book text.
- `How to Train Your Dragon` and `Harry Potter` are conversation-topic labels only.
- Preserve the existing local Mac Ollama architecture and current model/TTS settings.
- Keep sessions, history, summaries, and active-session choices on the Android device.
- Lock `MainActivity` to portrait orientation.
- Do not add session delete, rename, search, cloud sync, ebook import, or web search.
- Keep existing conversations during the Room version 2 to 3 migration.
- Use the Android emulator for all device verification; the OnePlus phone is offline.
- Follow red-green-refactor for every production behavior.

---

### Task 1: Topic Catalog and Topic-Aware Prompt

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationTopic.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationModels.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationPrompt.kt`
- Create: `app/src/test/java/com/kidsenglish/companion/conversation/ConversationTopicTest.kt`
- Modify: `app/src/test/java/com/kidsenglish/companion/conversation/ConversationPromptTest.kt`

**Interfaces:**
- Produces: `ConversationTopic(id: String, title: String, branded: Boolean)`.
- Produces: `ConversationTopicCatalog.predefined: List<ConversationTopic>` and `ConversationTopicCatalog.custom(raw: String): ConversationTopic?`.
- Changes: `ConversationRequest` gains `topic: ConversationTopic` and `opening: Boolean = false`.
- Consumed later by: session creation, history rows, `AppViewModel`, topic picker.

- [ ] **Step 1: Write failing topic-catalog tests**

```kotlin
class ConversationTopicTest {
    @Test fun exposesTheCuratedTopicsInDisplayOrder() {
        assertEquals(
            listOf("How to Train Your Dragon", "Harry Potter", "Space", "Dinosaurs", "Animals", "School Life"),
            ConversationTopicCatalog.predefined.map { it.title },
        )
    }

    @Test fun trimsAndBoundsCustomTopics() {
        assertEquals("Ocean robots", ConversationTopicCatalog.custom("  Ocean robots  ")?.title)
        assertNull(ConversationTopicCatalog.custom("   "))
        assertNull(ConversationTopicCatalog.custom("x".repeat(61)))
    }
}
```

- [ ] **Step 2: Run the catalog tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.kidsenglish.companion.conversation.ConversationTopicTest
```

Expected: compilation fails because `ConversationTopic` and `ConversationTopicCatalog` do not exist.

- [ ] **Step 3: Implement the topic domain model**

```kotlin
data class ConversationTopic(
    val id: String,
    val title: String,
    val branded: Boolean = false,
)

object ConversationTopicCatalog {
    val general = ConversationTopic("general", "General Conversation")
    val predefined = listOf(
        ConversationTopic("how-to-train-your-dragon", "How to Train Your Dragon", branded = true),
        ConversationTopic("harry-potter", "Harry Potter", branded = true),
        ConversationTopic("space", "Space"),
        ConversationTopic("dinosaurs", "Dinosaurs"),
        ConversationTopic("animals", "Animals"),
        ConversationTopic("school-life", "School Life"),
    )

    fun custom(raw: String): ConversationTopic? {
        val title = raw.trim().replace(Regex("\\s+"), " ")
        if (title.isBlank() || title.length > 60) return null
        return ConversationTopic("custom-${title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}", title)
    }

    fun resolve(id: String, title: String): ConversationTopic =
        (predefined + general).firstOrNull { it.id == id }
            ?: ConversationTopic(id = id, title = title)
}
```

- [ ] **Step 4: Run the catalog tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Write failing prompt tests for topic safety and opening turns**

Add tests asserting that a branded topic prompt contains the exact topic title, says the model does not contain the book, prohibits long quotations and reconstructed chapters, requests uncertainty instead of invented details, and avoids spoilers. Add an opening test using `opening = true` and blank `childMessage`, asserting that the final user instruction asks for a friendly opening plus exactly one question.

```kotlin
@Test fun brandedTopicPromptSetsSafeConversationBoundaries() {
    val system = ConversationPrompt.messages(
        ConversationRequest(
            ageBand = AgeBand.AGE_9_10,
            childMessage = "I like Toothless.",
            topic = ConversationTopicCatalog.predefined.first(),
        ),
    ).first().content

    assertTrue(system.contains("How to Train Your Dragon"))
    assertTrue(system.contains("does not contain the book"))
    assertTrue(system.contains("Do not quote long passages"))
    assertTrue(system.contains("do not reconstruct chapters"))
    assertTrue(system.contains("If an exact detail is uncertain"))
    assertTrue(system.contains("Avoid spoilers"))
}

@Test fun openingRequestProducesOneTopicQuestionWithoutAChildTurn() {
    val messages = ConversationPrompt.messages(
        ConversationRequest(
            ageBand = AgeBand.AGE_6_8,
            childMessage = "",
            topic = ConversationTopicCatalog.predefined.first { it.id == "space" },
            opening = true,
        ),
    )
    assertTrue(messages.last().content.contains("Open a friendly conversation about Space"))
    assertTrue(messages.last().content.contains("exactly one question"))
}
```

- [ ] **Step 6: Run prompt tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.kidsenglish.companion.conversation.ConversationPromptTest
```

Expected: compilation fails because `ConversationRequest` does not accept `topic` or `opening`.

- [ ] **Step 7: Extend `ConversationRequest` and build topic-aware messages**

Give `ConversationRequest.topic` a default of `ConversationTopicCatalog.general` so existing callers remain source-compatible. When `opening` is true, do not add an empty child message; add a generated user instruction:

```kotlin
val currentContent = if (request.opening) {
    "Open a friendly conversation about ${request.topic.title}. Use one short welcome and ask exactly one question."
} else {
    request.childMessage.trim()
}
```

Add branded-topic boundaries to the system prompt only when `topic.branded` is true. Retain the current age word limits, JSON-only response contract, memory, and latest-eight-turn behavior.

- [ ] **Step 8: Run all conversation unit tests**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.kidsenglish.companion.conversation.*'
```

Expected: PASS.

- [ ] **Step 9: Commit Task 1**

```bash
git add app/src/main/java/com/kidsenglish/companion/conversation/ConversationTopic.kt app/src/main/java/com/kidsenglish/companion/conversation/ConversationModels.kt app/src/main/java/com/kidsenglish/companion/conversation/ConversationPrompt.kt app/src/test/java/com/kidsenglish/companion/conversation/ConversationTopicTest.kt app/src/test/java/com/kidsenglish/companion/conversation/ConversationPromptTest.kt
git commit -m "feat: add safe topic conversation prompts"
```

---

### Task 2: Multi-Session Room Storage and Migration

**Files:**
- Modify: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationDatabase.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/progress/ProgressDatabase.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/app/AppContainer.kt`
- Modify: `app/src/androidTest/java/com/kidsenglish/companion/conversation/ConversationDatabaseTest.kt`
- Create: `app/src/androidTest/java/com/kidsenglish/companion/conversation/ConversationMigrationTest.kt`

**Interfaces:**
- Consumes: `ConversationTopic` from Task 1.
- Produces:

```kotlin
data class ConversationSessionSummary(
    val sessionId: String,
    val ageBand: AgeBand,
    val topic: ConversationTopic,
    val title: String,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- Changes `ConversationSnapshot` to include `sessionId` and `topic`, so the active conversation header and prompt are derived from the same observed database row as its turns and memory.
- Produces repository methods:
  - `fun observe(sessionId: String): Flow<ConversationSnapshot>`
  - `fun observeHistory(ageBand: AgeBand): Flow<List<ConversationSessionSummary>>`
  - `suspend fun createSession(ageBand: AgeBand, topic: ConversationTopic): String`
  - `suspend fun latestSessionId(ageBand: AgeBand): String?`
  - `suspend fun sessionExists(sessionId: String): Boolean`
  - `suspend fun appendUserTurn(sessionId: String, content: String)`
  - `suspend fun applyOutcome(sessionId: String, outcome: ConversationOutcome)`
  - `suspend fun clear(sessionId: String)`

- [ ] **Step 1: Replace the existing repository tests with explicit-session failing tests**

Create deterministic repositories with `idFactory = { "session-${nextId++}" }`. Add tests that:

1. create two sessions in the same age band;
2. append different messages and memory to each;
3. assert each `observe(sessionId)` contains only its own turns and memory;
4. assert history is ordered by `updatedAt DESC`;
5. assert the first user message sets a short local title;
6. assert a session with no user message uses its topic title.

Use this title expectation:

```kotlin
assertEquals("I want to talk about flying dragons", history.first { it.sessionId == first }.title)
```

and ensure input longer than 40 characters is truncated to 40 characters without a model call.

- [ ] **Step 2: Run the database test and verify RED**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.conversation.ConversationDatabaseTest
```

Expected: compilation fails because the repository still uses age-band-derived session IDs.

- [ ] **Step 3: Add session metadata and history queries**

Add these fields to `ConversationSessionEntity`:

```kotlin
val topicId: String = "general",
val topicTitle: String = "General Conversation",
val title: String = "",
```

Add a Room projection:

```kotlin
data class ConversationHistoryRow(
    val sessionId: String,
    val ageBand: String,
    val topicId: String,
    val topicTitle: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val preview: String?,
)
```

Add DAO methods with these SQL contracts:

```kotlin
@Query("""
    SELECT s.sessionId, s.ageBand, s.topicId, s.topicTitle, s.title,
           s.createdAt, s.updatedAt,
           (SELECT content FROM conversation_turns t
            WHERE t.sessionId = s.sessionId
            ORDER BY t.id DESC LIMIT 1) AS preview
    FROM conversation_sessions s
    WHERE s.ageBand = :ageBand
    ORDER BY s.updatedAt DESC
""")
abstract fun observeHistory(ageBand: String): Flow<List<ConversationHistoryRow>>

@Query("SELECT sessionId FROM conversation_sessions WHERE ageBand = :ageBand ORDER BY updatedAt DESC LIMIT 1")
abstract suspend fun latestSessionId(ageBand: String): String?

@Query("SELECT EXISTS(SELECT 1 FROM conversation_sessions WHERE sessionId = :sessionId)")
abstract suspend fun sessionExists(sessionId: String): Boolean

@Query("UPDATE conversation_sessions SET title = :title WHERE sessionId = :sessionId AND title = ''")
abstract suspend fun setTitleIfEmpty(sessionId: String, title: String)
```

Map each `ConversationHistoryRow` to `ConversationSessionSummary` in the repository. Resolve its topic with `ConversationTopicCatalog.resolve(row.topicId, row.topicTitle)`, use `row.title.ifBlank { row.topicTitle }` for display, and convert a null preview to an empty string. Do not add paging.

- [ ] **Step 4: Implement the explicit-session repository API**

Inject both `clock` and `idFactory`:

```kotlin
class ConversationRepository(
    private val dao: ConversationDao,
    private val json: Json = Json,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
)
```

`createSession` inserts a row with `title = ""`; the history mapper falls back to the topic title until the child sends a message. `appendUserTurn` trims content to 500 characters, inserts the turn, derives a one-line title of at most 40 characters, and calls `setTitleIfEmpty`. Later messages must not overwrite the first-message title.

Update `observe(sessionId)` so the returned `ConversationSnapshot` contains the stored session ID and `ConversationTopicCatalog.resolve(session.topicId, session.topicTitle)`. This keeps the active topic correct after process recreation or history reopening.

- [ ] **Step 5: Run the database test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 6: Write a failing version-2-to-3 migration test**

The test creates a file-backed version-2 database containing:

- the existing `learning_progress` table;
- the version-2 `conversation_sessions` and `conversation_turns` tables;
- session `conversation-6-8` with one user turn and non-empty memory.

Open it with Room version 3 and `AppContainer.MIGRATION_2_3`, then assert:

```kotlin
assertEquals("general", migrated.topicId)
assertEquals("General Conversation", migrated.topicTitle)
assertEquals("", migrated.title)
assertEquals("I like dragons.", repository.observe("conversation-6-8").first { it.turns.isNotEmpty() }.turns.single().content)
```

- [ ] **Step 7: Run the migration test and verify RED**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.conversation.ConversationMigrationTest
```

Expected: FAIL because database version 3 and `MIGRATION_2_3` do not exist.

- [ ] **Step 8: Add Room version 3 and the additive migration**

Set `ProgressDatabase.version = 3`. Add:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE conversation_sessions ADD COLUMN topicId TEXT NOT NULL DEFAULT 'general'")
        db.execSQL("ALTER TABLE conversation_sessions ADD COLUMN topicTitle TEXT NOT NULL DEFAULT 'General Conversation'")
        db.execSQL("ALTER TABLE conversation_sessions ADD COLUMN title TEXT NOT NULL DEFAULT ''")
    }
}
```

Register both `MIGRATION_1_2` and `MIGRATION_2_3` in `AppContainer`.

- [ ] **Step 9: Run both database test classes**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.kidsenglish.companion.conversation
```

Expected: PASS.

- [ ] **Step 10: Commit Task 2**

```bash
git add app/src/main/java/com/kidsenglish/companion/conversation/ConversationDatabase.kt app/src/main/java/com/kidsenglish/companion/progress/ProgressDatabase.kt app/src/main/java/com/kidsenglish/companion/app/AppContainer.kt app/src/androidTest/java/com/kidsenglish/companion/conversation/ConversationDatabaseTest.kt app/src/androidTest/java/com/kidsenglish/companion/conversation/ConversationMigrationTest.kt
git commit -m "feat: persist independent conversation sessions"
```

---

### Task 3: Active Session Persistence and ViewModel Coordination

**Files:**
- Modify: `app/src/main/java/com/kidsenglish/companion/settings/AppSettingsRepository.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/LearningNavigator.kt`
- Modify: `app/src/test/java/com/kidsenglish/companion/ui/LearningNavigatorTest.kt`
- Create: `app/src/androidTest/java/com/kidsenglish/companion/settings/ActiveConversationSettingsTest.kt`

**Interfaces:**
- Consumes: Task 2 repository API.
- Produces settings methods:
  - `fun activeSessionId(ageBand: AgeBand): Flow<String?>`
  - `suspend fun setActiveSessionId(ageBand: AgeBand, sessionId: String)`
- Produces ViewModel methods:
  - `fun showTopicPicker()`
  - `fun createConversation(topic: ConversationTopic)`
  - `fun showConversationHistory()`
  - `fun openConversation(sessionId: String)`
- Adds `AppScreen.TOPICS` and `AppScreen.HISTORY`.

- [ ] **Step 1: Write failing active-session persistence tests**

Use the instrumentation context, set different IDs for `AGE_6_8` and `AGE_11_12`, recreate `AppSettingsRepository`, then assert both values survive and do not overwrite one another:

```kotlin
repository.setActiveSessionId(AgeBand.AGE_6_8, "young-session")
repository.setActiveSessionId(AgeBand.AGE_11_12, "older-session")
assertEquals("young-session", repository.activeSessionId(AgeBand.AGE_6_8).first())
assertEquals("older-session", repository.activeSessionId(AgeBand.AGE_11_12).first())
```

- [ ] **Step 2: Run the settings test and verify RED**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.settings.ActiveConversationSettingsTest
```

Expected: compilation fails because the active-session methods do not exist.

- [ ] **Step 3: Add per-age-band DataStore keys**

Map each age band to an explicit preference key:

```kotlin
private fun activeSessionKey(ageBand: AgeBand) = when (ageBand) {
    AgeBand.AGE_6_8 -> ACTIVE_SESSION_6_8
    AgeBand.AGE_9_10 -> ACTIVE_SESSION_9_10
    AgeBand.AGE_11_12 -> ACTIVE_SESSION_11_12
}
```

Expose the two interfaces above and keep active-session IDs outside `AppSettings`, preventing unrelated setting changes from recreating the conversation flow.

- [ ] **Step 4: Run the settings test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Write failing navigator tests for topics and history**

Add assertions that `showTopicPicker()` selects `TOPICS`, `showHistory()` selects `HISTORY`, and `back()` from either returns to `CONVERSATION`.

- [ ] **Step 6: Run navigator tests and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.kidsenglish.companion.ui.LearningNavigatorTest
```

Expected: compilation fails because the screens and methods do not exist.

- [ ] **Step 7: Extend the navigator and `AppUiState`**

Add:

```kotlin
val activeSessionId: String? = null,
val activeTopic: ConversationTopic = ConversationTopicCatalog.general,
val conversationHistory: List<ConversationSessionSummary> = emptyList(),
```

Extend the navigator with `TOPICS` and `HISTORY`, both returning to `CONVERSATION` on back.

- [ ] **Step 8: Coordinate active-session flows in `AppViewModel`**

For the selected age band:

1. read the persisted active session ID;
2. keep it only if `repository.sessionExists(id)`;
3. otherwise use `repository.latestSessionId(ageBand)`;
4. otherwise create a general session;
5. persist the resolved ID;
6. `flatMapLatest` into `repository.observe(sessionId)`;
7. separately collect `repository.observeHistory(ageBand)`.

When the active-session snapshot is collected, copy its `sessionId`, `topic`, turns, and memory into `AppUiState`. `sendConversationMessage` captures the current `activeSessionId` before launching, appends and applies outcomes only to that ID, and includes that snapshot-derived `activeTopic` in `ConversationRequest`.

`createConversation(topic)` creates and activates a session, returns to `CONVERSATION`, then calls the conversation client with `opening = true`. A failed opening request leaves the empty session available for retry and displays the existing offline status.

- [ ] **Step 9: Compile and run unit tests**

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 10: Commit Task 3**

```bash
git add app/src/main/java/com/kidsenglish/companion/settings/AppSettingsRepository.kt app/src/main/java/com/kidsenglish/companion/ui/AppViewModel.kt app/src/main/java/com/kidsenglish/companion/ui/LearningNavigator.kt app/src/test/java/com/kidsenglish/companion/ui/LearningNavigatorTest.kt app/src/androidTest/java/com/kidsenglish/companion/settings/ActiveConversationSettingsTest.kt
git commit -m "feat: coordinate active conversation sessions"
```

---

### Task 4: Topic Picker, History Screen, and Tap-to-Replay

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationReplayController.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/KidsEnglishApp.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/app/AppContainer.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/com/kidsenglish/companion/conversation/ConversationReplayControllerTest.kt`
- Modify: `app/src/androidTest/java/com/kidsenglish/companion/ui/LearningFlowTest.kt`

**Interfaces:**
- Consumes: topic catalog, session history, and ViewModel actions from Tasks 1–3.
- Produces: `TopicPickerScreen`, `ConversationHistoryScreen`, and `AppViewModel.replayConversationMessage(text: String)`.

- [ ] **Step 1: Write failing Compose tests for topic creation and history reopening**

Reset the test database and settings in `@Before`. Add one test that:

1. chooses age 6–8;
2. taps `New Conversation`;
3. sees all six predefined topics and `Custom Topic`;
4. taps `Space`;
5. verifies the conversation header displays `Space`.

Add another test that creates two sessions directly through the repository, opens `History`, verifies newest-first titles, taps the older title, and verifies only the older session's message is shown.

- [ ] **Step 2: Run the two new Compose tests and verify RED**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.ui.LearningFlowTest
```

Expected: tests fail because the new actions and screens are absent.

- [ ] **Step 3: Implement the topic picker**

Use a `LazyColumn` of large buttons suitable for children. Predefined topics call `viewModel.createConversation(topic)`. `Custom Topic` reveals one `OutlinedTextField`, limits input to 60 characters, and disables creation until `ConversationTopicCatalog.custom(input)` succeeds.

Add localized Simplified Chinese labels:

```xml
<string name="new_conversation">新建对话</string>
<string name="conversation_history">历史对话</string>
<string name="choose_topic">选择今天想聊的话题</string>
<string name="custom_topic">自定义话题</string>
<string name="start_topic">开始聊这个话题</string>
```

- [ ] **Step 4: Implement the history screen**

Render `conversationHistory` newest first. Each `Card` shows `title`, `topic.title`, a formatted local date/time, and `preview`. Clicking calls `openConversation(sessionId)`. Show a localized empty state when no sessions exist.

- [ ] **Step 5: Write a failing replay-controller unit test**

Use a fake `SpeechController` and assert that blank text causes no calls, while nonblank text is trimmed, stops current speech first, and then speaks with the requested rate and `SpeechStyle.CHILD_CONVERSATION`.

```kotlin
@Test fun replayStopsCurrentSpeechAndUsesChildConversationStyle() {
    controller.replay("  Hello, explorer!  ", rate = 0.82f)

    assertEquals(listOf("stop", "speak"), speech.calls)
    assertEquals("Hello, explorer!", speech.lastText)
    assertEquals(0.82f, speech.lastRate)
    assertEquals(SpeechStyle.CHILD_CONVERSATION, speech.lastStyle)
}
```

- [ ] **Step 6: Run the replay test and verify RED**

Run:

```bash
./gradlew testDebugUnitTest --tests com.kidsenglish.companion.conversation.ConversationReplayControllerTest
```

Expected: compilation fails because `ConversationReplayController` does not exist.

- [ ] **Step 7: Implement the replay controller, ViewModel action, and clickable cards**

Add the controller and expose one instance from `AppContainer`:

```kotlin
class ConversationReplayController(
    private val speechController: SpeechController,
) {
    fun replay(text: String, rate: Float, onError: (String) -> Unit = {}) {
        val normalized = text.trim()
        if (normalized.isBlank()) return
        speechController.stopSpeaking()
        speechController.speak(
            text = normalized,
            rate = rate,
            style = SpeechStyle.CHILD_CONVERSATION,
            onError = onError,
        )
    }
}
```

`AppViewModel.replayConversationMessage(text)` delegates to this controller with the current speech rate and maps `onError` to `conversationStatus`.

Make each message `Card` use `Modifier.clickable { viewModel.replayConversationMessage(turn.content) }`, add an accessibility role/button semantic through the clickable modifier, and add the visible hint `点击英文消息可重新朗读` above the list.

- [ ] **Step 8: Run the replay test and verify GREEN**

Run the Step 6 command. Expected: PASS.

- [ ] **Step 9: Add a replay interaction test**

Insert a known assistant message, open the conversation, click its text, and assert the UI remains on the conversation screen with the message still present and no voice-session controls activated. This Compose test validates that replay is a bounded TTS action rather than continuous microphone mode. The speech-style unit test continues to verify child pitch/rate behavior.

- [ ] **Step 10: Run `LearningFlowTest` and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 11: Commit Task 4**

```bash
git add app/src/main/java/com/kidsenglish/companion/conversation/ConversationReplayController.kt app/src/main/java/com/kidsenglish/companion/ui/KidsEnglishApp.kt app/src/main/java/com/kidsenglish/companion/ui/AppViewModel.kt app/src/main/java/com/kidsenglish/companion/app/AppContainer.kt app/src/main/res/values/strings.xml app/src/test/java/com/kidsenglish/companion/conversation/ConversationReplayControllerTest.kt app/src/androidTest/java/com/kidsenglish/companion/ui/LearningFlowTest.kt
git commit -m "feat: add topic and conversation history screens"
```

---

### Task 5: Portrait Lock and Full Emulator Verification

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/androidTest/java/com/kidsenglish/companion/ui/LearningFlowTest.kt`
- Modify: `README.md`

**Interfaces:**
- Consumes: completed topic/session UI.
- Produces: portrait-only manifest behavior and updated user/developer documentation.

- [ ] **Step 1: Write a failing portrait-orientation instrumentation test**

Add:

```kotlin
@Test fun mainActivityIsLockedToPortrait() {
    val info = compose.activity.packageManager.getActivityInfo(compose.activity.componentName, 0)
    assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, info.screenOrientation)
}
```

- [ ] **Step 2: Run the portrait test and verify RED**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.ui.LearningFlowTest#mainActivityIsLockedToPortrait
```

Expected: FAIL because `MainActivity` has no portrait declaration.

- [ ] **Step 3: Lock `MainActivity` to portrait**

Add to the existing activity declaration:

```xml
android:screenOrientation="portrait"
```

- [ ] **Step 4: Run the portrait test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Update README behavior and boundaries**

Document the topic catalog, the fact that branded titles are discussion topics without bundled book text, new/history conversations, tap-to-replay, portrait-only layout, local-only history, and emulator verification command.

- [ ] **Step 6: Run the complete static and JVM verification**

Run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
git diff --check
```

Expected: `BUILD SUCCESSFUL`; no diff-check output.

- [ ] **Step 7: Run the complete emulator suite**

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

Expected: all tests pass with zero failures and zero skips.

- [ ] **Step 8: Install and smoke-test the emulator APK**

Run:

```bash
adb -s emulator-5554 install -r -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 reverse tcp:11434 tcp:11434
adb -s emulator-5554 shell am force-stop com.kidsenglish.companion
adb -s emulator-5554 shell monkey -p com.kidsenglish.companion -c android.intent.category.LAUNCHER 1
```

Exercise these paths with Compose tests or ADB UI automation: choose an age, create a Space topic session, send one message, open History, reopen the session, tap the assistant message, rotate the emulator, and confirm the activity remains portrait.

- [ ] **Step 9: Capture screenshot and inspect crash logs**

Run:

```bash
adb -s emulator-5554 exec-out screencap -p > /private/tmp/kids-topic-history.png
adb -s emulator-5554 logcat -d -t 500 | rg 'FATAL EXCEPTION|AndroidRuntime|com.kidsenglish.companion'
```

Expected: screenshot shows the completed topic/history flow; logs contain no `FATAL EXCEPTION` for the app.

- [ ] **Step 10: Commit Task 5**

```bash
git add app/src/main/AndroidManifest.xml app/src/androidTest/java/com/kidsenglish/companion/ui/LearningFlowTest.kt README.md
git commit -m "feat: finish topic conversation experience"
```
