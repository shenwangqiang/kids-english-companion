# Kids English Companion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android MVP for children aged 6–12 with offline graded stories, read-aloud, speaking practice, story-grounded Qwen tutoring, comprehension questions, and local progress.

**Architecture:** A single-activity Jetpack Compose application keeps story content and progress local. Focused domain services handle passage retrieval, prompt policy, safety checks, and explainable speaking scores; Android speech adapters and an OpenAI-compatible HTTP client sit behind interfaces so reading remains available when AI is offline.

**Tech Stack:** Kotlin 2.1.0, Java 17, Gradle 8.10.2, Android Gradle Plugin 8.8.0, compile/target SDK 36, min SDK 26, Jetpack Compose BOM 2025.01.01, Room 2.7.0, DataStore 1.1.2, OkHttp 4.12.0, kotlinx.serialization 1.8.1, coroutines 1.10.2, JUnit 4, MockWebServer 4.12.0.

**Spec:** `docs/superpowers/specs/2026-10-16-kids-english-companion-design.md`

## Global Constraints

- Package and application ID: `com.kidsenglish.companion`.
- Project path: `/Users/lisi/AndroidStudioProjects/kids-english-companion`.
- The app must not require a paid model API; development defaults to configurable Ollama/Qwen3.
- Release builds accept only HTTPS model endpoints; debug builds may explicitly allow local-network HTTP.
- Ship exactly 18 original stories: six each for ages 6–8, 9–10, and 11–12.
- Do not ship copyrighted commercial textbook content.
- Do not request or store a child's name, school, address, contacts, photos, or location.
- Reading, TTS, fixed questions, speaking score, and progress must remain usable when the model endpoint is unavailable.
- Speaking feedback is a word-match score, never described as professional phoneme assessment.
- No account, analytics SDK, advertising SDK, payment, cloud sync, or teacher portal.
- Use TDD for domain, persistence, and network behavior; run fresh build, lint, unit, instrumentation, and smoke checks before completion claims.

## File Map

```text
settings.gradle.kts                         plugin/dependency repositories and app module
build.gradle.kts                            pinned Android/Kotlin plugins
gradle.properties                           AndroidX, JVM, and Compose build settings
gradle/wrapper/*                            Gradle 8.10.2 wrapper
app/build.gradle.kts                        Android config and dependencies
app/src/main/AndroidManifest.xml            permissions, app, cleartext policy
app/src/debug/res/xml/network_security_config.xml
app/src/main/java/com/kidsenglish/companion/
  MainActivity.kt                           Compose host and permission bridge
  KidsEnglishApplication.kt                 process-level dependency container
  app/AppContainer.kt                       manual dependency assembly
  model/StoryModels.kt                      immutable story and age-band types
  content/StoryRepository.kt                catalog interface and asset implementation
  domain/PassageRetriever.kt                active-story lexical retrieval
  domain/SpeakingScore.kt                   transparent target/recognized comparison
  tutor/TutorPolicy.kt                      child-safety and age rules
  tutor/PromptBuilder.kt                    grounded message construction
  tutor/TutorClient.kt                      model interface and result types
  tutor/OpenAiTutorClient.kt                compatible chat-completions transport
  speech/SpeechController.kt                TTS/ASR interface and Android adapter
  progress/ProgressDatabase.kt              Room entity, DAO, database, repository
  settings/AppSettingsRepository.kt         endpoint/model/age/speech-rate preferences
  settings/SecureTokenStore.kt              Android Keystore-backed bearer token
  ui/AppViewModel.kt                        application state and use-case orchestration
  ui/KidsEnglishApp.kt                      navigation and top-level UI state
  ui/screens/*.kt                           Welcome, Library, Reader, Practice, Tutor, Progress, Settings
  ui/theme/*.kt                             accessible child-friendly theme
app/src/main/assets/stories/*.json          18 original graded stories
app/src/test/...                            JVM unit tests
app/src/androidTest/...                     Room and Compose integration tests
README.md                                   build, Ollama, local LAN, remote TLS setup
```

---

### Task 1: Bootstrap a Buildable Compose Application

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/com/kidsenglish/companion/MainActivity.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/AppConfigurationTest.kt`

**Interfaces:**
- Produces: a buildable `:app` module and `MainActivity` Compose entry point used by all later tasks.

- [ ] **Step 1: Write the failing configuration test**

```kotlin
package com.kidsenglish.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigurationTest {
    @Test fun supportedAgeBandsAreStable() {
        assertEquals(listOf("6-8", "9-10", "11-12"), BuildContract.AGE_BANDS)
    }
}
```

- [ ] **Step 2: Create the pinned Gradle project and verify the test initially fails**

Run: `./gradlew testDebugUnitTest --tests '*AppConfigurationTest'`

Expected: compilation fails because `BuildContract` does not exist.

- [ ] **Step 3: Add the minimal app shell**

Create `BuildContract.kt`:

```kotlin
package com.kidsenglish.companion

object BuildContract {
    const val DATABASE_NAME = "kids-english.db"
    val AGE_BANDS = listOf("6-8", "9-10", "11-12")
}
```

Configure Compose, Kotlin serialization, kapt, Room, DataStore, OkHttp, navigation, lifecycle, coroutines, JUnit, MockWebServer, AndroidX test, and Compose UI test dependencies using the versions in the plan header. Set Java/Kotlin target 17, SDK values from Global Constraints, and test instrumentation runner `androidx.test.runner.AndroidJUnitRunner`.

`MainActivity` must render a themed `Kids English Companion` title and a `Loading library…` body so the application is launchable before later screens exist.

- [ ] **Step 4: Verify the shell**

Run: `./gradlew testDebugUnitTest assembleDebug lintDebug`

Expected: all commands exit 0 and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle app
git commit -m "build: bootstrap kids English Android app"
```

---

### Task 2: Add Typed Story Content and the 18-Story Catalog

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/model/StoryModels.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/content/StoryRepository.kt`
- Create: `app/src/main/assets/stories/age_6_8.json`
- Create: `app/src/main/assets/stories/age_9_10.json`
- Create: `app/src/main/assets/stories/age_11_12.json`
- Test: `app/src/test/java/com/kidsenglish/companion/content/StoryCatalogTest.kt`

**Interfaces:**
- Produces: `AgeBand`, `Story`, `StoryParagraph`, `VocabularyItem`, `ComprehensionQuestion`, and `StoryRepository`.
- Produces: `StoryRepository.stories(ageBand: AgeBand): List<Story>` and `story(id: String): Story?`.

- [ ] **Step 1: Write failing catalog and schema tests**

```kotlin
@Test fun catalogContainsSixValidStoriesPerBand() {
    AgeBand.entries.forEach { band ->
        val stories = repository.stories(band)
        assertEquals(6, stories.size)
        assertTrue(stories.all { it.paragraphs.isNotEmpty() })
        assertTrue(stories.all { it.vocabulary.size >= 5 })
        assertTrue(stories.all { it.questions.size == 3 })
    }
}

@Test fun storyIdsAreUniqueAndSentencesAreSpeakable() {
    val stories = AgeBand.entries.flatMap(repository::stories)
    assertEquals(18, stories.map(Story::id).distinct().size)
    assertTrue(stories.flatMap { it.paragraphs }.flatMap { it.sentences }
        .all { it.text.isNotBlank() && it.text.length <= 220 })
}
```

- [ ] **Step 2: Run tests and observe missing model/repository failures**

Run: `./gradlew testDebugUnitTest --tests '*StoryCatalogTest'`

Expected: compilation fails because story types and repository do not exist.

- [ ] **Step 3: Implement serializable story types and asset repository**

Use these exact core types:

```kotlin
enum class AgeBand(val wireValue: String) { AGE_6_8("6-8"), AGE_9_10("9-10"), AGE_11_12("11-12") }

@Serializable data class StorySentence(val id: String, val text: String, val chineseHint: String)
@Serializable data class StoryParagraph(val id: String, val sentences: List<StorySentence>)
@Serializable data class VocabularyItem(val word: String, val definition: String, val chinese: String, val example: String)
@Serializable data class ComprehensionQuestion(val id: String, val prompt: String, val answerConcepts: List<String>)
@Serializable data class Story(
    val id: String,
    val title: String,
    val ageBand: String,
    val cefr: String,
    val summary: String,
    val paragraphs: List<StoryParagraph>,
    val vocabulary: List<VocabularyItem>,
    val questions: List<ComprehensionQuestion>,
    val attribution: String = "Original content for Kids English Companion"
)
```

The repository receives an `InputStream` loader function so JVM tests can load fixture assets without Android framework dependencies. The Android implementation supplies `context.assets.open(path)`.

- [ ] **Step 4: Author the complete original catalog**

Create complete prose, Chinese hints, vocabulary entries, and three answerable questions for these fixed titles and learning objectives:

| Band | Story title | Objective |
|---|---|---|
| 6–8 | Mia and the Red Kite | colors, weather, simple actions |
| 6–8 | Ben's Tiny Garden | plants, growth, sequencing |
| 6–8 | The Sleepy Little Train | transport, time, repetition |
| 6–8 | Coco Finds a Friend | introductions and feelings |
| 6–8 | A Picnic for Five | food and counting |
| 6–8 | The Moon in My Window | night vocabulary and imagination |
| 9–10 | The Library Mystery | clues, locations, inference |
| 9–10 | Nora Builds a Boat | materials and problem solving |
| 9–10 | The Day the Bees Rested | nature and cause/effect |
| 9–10 | Leo's First Market | money, choices, conversation |
| 9–10 | A Message from the Mountain | directions and cooperation |
| 9–10 | The Robot Who Asked Why | questions and responsible technology |
| 11–12 | The Last Light in Harbor Town | community and responsibility |
| 11–12 | Maya's Science Fair Promise | evidence and honesty |
| 11–12 | The Map Under the Floor | historical clues and inference |
| 11–12 | One Week Without Plastic | environment and reflection |
| 11–12 | The Debate Club Surprise | opinions and respectful disagreement |
| 11–12 | Signals from the Night Sky | astronomy and uncertainty |

Constraints: 6–8 stories use 120–180 words and CEFR Pre-A1/A1; 9–10 stories use 220–320 words and CEFR A1/A2; 11–12 stories use 350–500 words and CEFR A2/B1. Each question's concepts must be explicitly supported by story text.

- [ ] **Step 5: Verify all content**

Run: `./gradlew testDebugUnitTest --tests '*StoryCatalogTest'`

Expected: 18 stories validate, with six in every band and no duplicate IDs.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kidsenglish/companion/model app/src/main/java/com/kidsenglish/companion/content app/src/main/assets app/src/test
git commit -m "feat: add original graded story catalog"
```

---

### Task 3: Implement Retrieval, Safety Policy, Prompts, and Speaking Score

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/domain/PassageRetriever.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/domain/SpeakingScore.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/tutor/TutorPolicy.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/tutor/PromptBuilder.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/tutor/TutorClient.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/domain/PassageRetrieverTest.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/domain/SpeakingScoreTest.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/tutor/TutorPolicyTest.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/tutor/PromptBuilderTest.kt`

**Interfaces:**
- Produces: `PassageRetriever.retrieve(story: Story, query: String, currentParagraphId: String?, limit: Int = 3): List<StoryParagraph>`.
- Produces: `SpeakingScore.compare(target: String, recognized: String): SpeakingFeedback`.
- Produces: `TutorPolicy.validateInput(input: String): PolicyDecision`.
- Produces: `PromptBuilder.messages(request: TutorRequest): List<ChatMessage>`.
- Produces: `TutorClient.chat(request: TutorRequest): TutorResult` and `connectionTest(): TutorResult`.

- [ ] **Step 1: Write focused failing tests**

```kotlin
@Test fun currentParagraphIsAlwaysIncluded() {
    val result = PassageRetriever().retrieve(story, "Where is the kite?", "p2", 2)
    assertEquals("p2", result.first().id)
}

@Test fun speakingScoreShowsMissedAndExtraWords() {
    val result = SpeakingScore.compare("The red kite flies high", "the kite fly high today")
    assertTrue("red" in result.missedWords)
    assertTrue("today" in result.extraWords)
    assertTrue(result.percent in 0..100)
}

@Test fun policyBlocksPersonalInformationRequests() {
    assertTrue(TutorPolicy.validateInput("What is your home address?") is PolicyDecision.Blocked)
}

@Test fun promptContainsEvidenceAndAgeLimit() {
    val messages = PromptBuilder().messages(request)
    assertTrue(messages.first().content.contains("Use only the supplied story evidence"))
    assertTrue(messages.first().content.contains("6-8"))
    assertTrue(messages.last().content.contains("Mia and the Red Kite"))
}
```

- [ ] **Step 2: Run tests and confirm missing implementations**

Run: `./gradlew testDebugUnitTest --tests '*PassageRetrieverTest' --tests '*SpeakingScoreTest' --tests '*TutorPolicyTest' --tests '*PromptBuilderTest'`

Expected: compilation fails for undefined types.

- [ ] **Step 3: Implement deterministic domain behavior**

Token normalization must lowercase with `Locale.US`, replace punctuation with spaces, collapse whitespace, and preserve apostrophes inside words. Passage ranking counts distinct query-token overlap, then keeps original paragraph order as the stable tie-breaker. Always prepend the current paragraph when supplied.

Speaking feedback uses longest-common-subsequence alignment and exposes:

```kotlin
data class SpeakingFeedback(
    val percent: Int,
    val recognizedText: String,
    val correctWords: List<String>,
    val missedWords: List<String>,
    val extraWords: List<String>
)
```

Score is `round(100 * correct target tokens / target token count)`, clamped to 0–100. Empty recognition scores 0; empty target is rejected with `IllegalArgumentException`.

The policy blocks empty input, input over 500 characters, common address/phone/school/contact/photo/secret patterns, and unsafe sexual, graphic violence, self-harm, criminal-instruction, or manipulation patterns. A blocked result contains a child-safe redirect: `Let's stay with our story and learning activity.`

The prompt orders messages as system policy, grounded evidence, capped recent history (last six messages), then current child message. It sets reply limits of 45, 80, and 120 words for the three age bands.

- [ ] **Step 4: Run domain tests**

Run: `./gradlew testDebugUnitTest --tests '*PassageRetrieverTest' --tests '*SpeakingScoreTest' --tests '*TutorPolicyTest' --tests '*PromptBuilderTest'`

Expected: all domain tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kidsenglish/companion/domain app/src/main/java/com/kidsenglish/companion/tutor app/src/test
git commit -m "feat: add grounded tutor domain rules"
```

---

### Task 4: Add Local Settings, Secure Token Storage, and Progress Database

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/settings/AppSettingsRepository.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/settings/SecureTokenStore.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/progress/ProgressDatabase.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/settings/EndpointPolicyTest.kt`
- Test: `app/src/androidTest/java/com/kidsenglish/companion/progress/ProgressDatabaseTest.kt`

**Interfaces:**
- Produces: `AppSettings(ageBand, baseUrl, modelName, speechRate, allowDebugHttp)` as a `Flow<AppSettings>`.
- Produces: suspend setters for each non-secret setting.
- Produces: `SecureTokenStore.save/read/clear` with Android Keystore-backed AES/GCM encryption.
- Produces: `ProgressRepository.markStoryOpened`, `markStoryCompleted`, `recordSpeakingAttempt`, and `observeSummary`.

- [ ] **Step 1: Write failing policy and Room tests**

```kotlin
@Test fun releaseEndpointRequiresHttps() {
    assertFalse(EndpointPolicy.isAllowed("http://example.com/v1", isDebug = false, allowDebugHttp = false))
    assertTrue(EndpointPolicy.isAllowed("https://example.com/v1", isDebug = false, allowDebugHttp = false))
}

@Test fun debugEndpointAllowsPrivateLanOnlyWhenEnabled() {
    assertTrue(EndpointPolicy.isAllowed("http://192.168.1.20:11434/v1", true, true))
    assertFalse(EndpointPolicy.isAllowed("http://example.com/v1", true, true))
}
```

The instrumented Room test inserts an opened story, marks it complete, records two scores for one sentence, then asserts completion is true, attempt count is two, and best score equals the larger value.

- [ ] **Step 2: Run tests and confirm missing storage components**

Run JVM test: `./gradlew testDebugUnitTest --tests '*EndpointPolicyTest'`

Run instrumentation test when an emulator is available: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.progress.ProgressDatabaseTest`

Expected: tests fail to compile because policy and storage types are missing.

- [ ] **Step 3: Implement preferences and endpoint validation**

Use DataStore Preferences keys `age_band`, `base_url`, `model_name`, `speech_rate`, and `allow_debug_http`. Defaults are `AGE_6_8`, empty base URL, `qwen3:4b`, `0.9f`, and false.

`EndpointPolicy` permits HTTPS universally. In debug builds only, when the explicit toggle is true, it additionally permits HTTP hosts `10.0.2.2`, `localhost`, `127.0.0.1`, RFC1918 IPv4 ranges, and `.local` hostnames. It rejects embedded credentials, fragments, and non-HTTP schemes.

- [ ] **Step 4: Implement Keystore token storage and Room**

Generate an AES-256 key named `kids_english_model_token` in `AndroidKeyStore`, require GCM with no padding, store only Base64 IV and ciphertext in private SharedPreferences, and return an empty token when none is saved. Never log token content or include it in thrown error messages.

Use a single Room `LearningProgressEntity` keyed by `storyId` with `openedAt`, nullable `completedAt`, `speakingAttempts`, and `bestSpeakingScore`. DAO updates must preserve the maximum score.

- [ ] **Step 5: Run storage tests**

Run: `./gradlew testDebugUnitTest --tests '*EndpointPolicyTest'`

Run with emulator: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.progress.ProgressDatabaseTest`

Expected: policy and Room tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/kidsenglish/companion/settings app/src/main/java/com/kidsenglish/companion/progress app/src/test app/src/androidTest
git commit -m "feat: persist secure settings and learning progress"
```

---

### Task 5: Implement the Ollama/OpenAI-Compatible Tutor Client

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/tutor/OpenAiTutorClient.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/tutor/OpenAiTutorClientTest.kt`

**Interfaces:**
- Consumes: `TutorClient`, `TutorRequest`, `TutorResult`, `PromptBuilder`, `AppSettingsRepository`, and `SecureTokenStore`.
- Produces: a live `TutorClient` that calls `{baseUrl}/chat/completions` and a connection test that sends a deterministic one-token prompt.

- [ ] **Step 1: Write failing MockWebServer tests**

```kotlin
@Test fun sendsModelMessagesAndBearerToken() = runTest {
    server.enqueue(MockResponse().setBody(successBody("The kite is in the tree.")))
    val result = client.chat(request)
    val recorded = server.takeRequest()
    assertEquals("Bearer secret", recorded.getHeader("Authorization"))
    assertTrue(recorded.body.readUtf8().contains("qwen3:4b"))
    assertEquals("The kite is in the tree.", (result as TutorResult.Success).text)
}

@Test fun mapsTimeoutAndMalformedResponses() = runTest {
    server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
    assertTrue(client.chat(request) is TutorResult.Unavailable)
}
```

Also test 401 maps to `AuthenticationRequired`, 429/5xx maps to `Unavailable`, blank assistant content maps to `InvalidResponse`, and the token header is omitted when the token is empty.

- [ ] **Step 2: Run the test and confirm the client is missing**

Run: `./gradlew testDebugUnitTest --tests '*OpenAiTutorClientTest'`

Expected: compilation fails for `OpenAiTutorClient`.

- [ ] **Step 3: Implement bounded HTTP behavior**

Use OkHttp with 10-second connect, 30-second read, and 30-second call timeouts. Send JSON with `model`, `messages`, `stream=false`, `temperature=0.3`, and `max_tokens` derived from age-band word limit. Retry once only for connection failures and HTTP 502/503/504. Parse `choices[0].message.content`, trim it, enforce 2,000 output characters, and map every failure to a typed `TutorResult` without leaking URLs containing credentials or authorization headers.

Normalize the configured base URL once: strip the trailing slash and append `/chat/completions`. Reject it before transport when `EndpointPolicy` disallows it.

- [ ] **Step 4: Run network tests**

Run: `./gradlew testDebugUnitTest --tests '*OpenAiTutorClientTest'`

Expected: success, authentication, timeout, retry, invalid-response, and no-token tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/kidsenglish/companion/tutor/OpenAiTutorClient.kt app/src/test/java/com/kidsenglish/companion/tutor/OpenAiTutorClientTest.kt
git commit -m "feat: connect grounded tutor to Qwen service"
```

---

### Task 6: Add Android Speech and the Complete Compose Learning Flow

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/KidsEnglishApplication.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/app/AppContainer.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/speech/SpeechController.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/AppViewModel.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/KidsEnglishApp.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/screens/WelcomeScreen.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/screens/LibraryScreen.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/screens/ReaderScreen.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/screens/PracticeScreen.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/screens/TutorScreen.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/screens/ProgressScreen.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/debug/res/xml/network_security_config.xml`
- Test: `app/src/androidTest/java/com/kidsenglish/companion/ui/LearningFlowTest.kt`

**Interfaces:**
- Consumes: all repositories and domain services from Tasks 2–5.
- Produces: the user-visible MVP flow and Android speech adapter.
- Produces: `SpeechController.speak(text, rate)`, `startListening(onResult, onError)`, `stopListening()`, and `shutdown()`.

- [ ] **Step 1: Write failing Compose flow tests**

```kotlin
@Test fun chooseBandOpenStoryAndEnterPractice() {
    compose.onNodeWithText("Ages 6–8").performClick()
    compose.onNodeWithText("Mia and the Red Kite").performClick()
    compose.onNodeWithText("Practice this sentence").performClick()
    compose.onNodeWithText("Hold to speak").assertIsDisplayed()
}

@Test fun unavailableTutorDoesNotBlockReading() {
    compose.onNodeWithText("Ask about this story").performClick()
    compose.onNodeWithText("AI tutor is offline").assertIsDisplayed()
    compose.onNodeWithContentDescription("Back").performClick()
    compose.onNodeWithText("Read sentence").assertIsDisplayed()
}
```

Use fake story, progress, speech, settings, and tutor implementations; the UI test must not depend on network or device speech services.

- [ ] **Step 2: Run the UI test and confirm screens are missing**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kidsenglish.companion.ui.LearningFlowTest`

Expected: compilation fails because app UI and fakeable interfaces are missing.

- [ ] **Step 3: Implement Android speech adapter**

Declare `RECORD_AUDIO`. Request it only when the child presses the speaking control. Configure recognition with `LANGUAGE_MODEL_FREE_FORM`, locale `en-US`, partial results false, and one best result. Map permission denial, recognizer absence, no match, network error, and cancellation to distinct UI messages. Initialize TextToSpeech once, set English locale, clamp speech rate to 0.6–1.2, stop before starting a new utterance, and release both services from lifecycle shutdown.

- [ ] **Step 4: Implement app container and state orchestration**

`KidsEnglishApplication` creates `AppContainer` containing the asset repository, Room progress repository, settings repository, secure token store, passage retriever, prompt builder, tutor client factory, and speech controller factory. Do not introduce a dependency-injection framework.

`AppViewModel` exposes one immutable `AppUiState` containing selected band, catalog, current story/sentence, progress summary, speech state, tutor messages, tutor availability, endpoint test state, and user-visible errors. All long-running work runs in `viewModelScope`; duplicate tutor submissions and duplicate recognizer starts are ignored.

- [ ] **Step 5: Implement seven screens and navigation**

Use a high-contrast warm theme, minimum 48dp controls, scalable typography, and text labels alongside icons. The primary flow is Welcome → Library → Reader → Practice/Tutor. Progress and Settings are reachable from Library.

Required stable UI strings for tests and accessibility:

- `Ages 6–8`, `Ages 9–10`, `Ages 11–12`
- `Read sentence`, `Practice this sentence`, `Ask about this story`
- `Hold to speak`, `Try again`, `Mark story complete`
- `AI tutor is offline`, `Test connection`, `Connection successful`
- `This is a word-match practice score, not a professional pronunciation assessment.`

The Tutor screen displays only the current story conversation, caps child input at 500 characters, disables send during a request, shows a safe policy redirect without making a network request, and preserves draft text after transport failure.

- [ ] **Step 6: Run UI and full unit tests**

Run: `./gradlew testDebugUnitTest connectedDebugAndroidTest assembleDebug lintDebug`

Expected: all tests pass, lint exits 0, and debug APK is generated.

- [ ] **Step 7: Commit**

```bash
git add app
git commit -m "feat: deliver reading speaking and tutor flows"
```

---

### Task 7: Document Ollama Setup and Run End-to-End Verification

**Files:**
- Create: `README.md`
- Create: `docs/ollama-local.md`
- Create: `docs/ollama-remote.md`
- Modify: `.gitignore`

**Interfaces:**
- Produces: exact setup instructions for local Mac development and authenticated remote deployment.

- [ ] **Step 1: Write setup documentation**

README commands must include:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Local Ollama instructions must install/start Ollama, pull `qwen3:4b`, test `http://localhost:11434/v1/chat/completions`, bind only to the LAN interface when explicitly needed, identify the Mac's private IP, and configure the debug app with `http://<private-ip>:11434/v1`.

Remote instructions must require HTTPS and authentication through a reverse proxy, keep port 11434 private, show the app base URL as `https://ai.example.com/v1`, explain bearer-token storage, and include a health request that never prints the token.

- [ ] **Step 2: Verify documentation contains no secrets or unsafe public exposure**

Run: `rg -n '0\.0\.0\.0:11434|sk-[A-Za-z0-9]|Authorization: Bearer [^<$]' README.md docs || true`

Expected: no hard-coded secret and no instruction to expose unauthenticated Ollama publicly.

- [ ] **Step 3: Run the complete automated gate**

Run: `./gradlew clean testDebugUnitTest lintDebug assembleDebug`

Run with emulator: `./gradlew connectedDebugAndroidTest`

Expected: every command exits 0; unit and instrumentation reports contain zero failures; debug APK exists.

- [ ] **Step 4: Run manual smoke acceptance**

1. Install and launch the debug APK.
2. Select ages 6–8, open `Mia and the Red Kite`, and play one sentence.
3. Grant microphone permission, speak the sentence, and confirm recognized/correct/missed/extra words render.
4. Connect to local Ollama/Qwen3 and ask where the kite is; confirm the answer cites only story facts.
5. Ask for a home address and confirm the app redirects without network activity.
6. Change the endpoint to an unreachable private IP and confirm reading, TTS, fixed questions, and progress still work.
7. Confirm logcat contains no bearer token, child audio, or recognized transcript.

- [ ] **Step 5: Inspect final repository state and commit documentation**

Run: `git status --short`

Expected: only intended documentation changes are pending.

```bash
git add README.md docs .gitignore
git commit -m "docs: add Qwen deployment and verification guide"
```

## Final Completion Gate

- [ ] Re-read the design spec and map every success criterion to a passing automated or manual check.
- [ ] Confirm `git status --short` is clean.
- [ ] Record APK path, tested device/emulator API, unit-test count, instrumentation-test count, lint result, and any unavailable external-service check.
- [ ] Do not claim professional pronunciation scoring, fully offline ASR, or production child-safety certification.
