# Conversation Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a conversation-first Android flow backed by Ollama native chat, persistent context, validated summaries, remembered facts, and planned next directions.

**Architecture:** Add a focused conversation module containing transport, prompt, parsing, and persistence boundaries. `AppViewModel` coordinates the module and exposes conversation UI state; existing story tutoring remains isolated until it can be removed safely.

**Tech Stack:** Kotlin, coroutines/Flow, kotlinx.serialization, OkHttp MockWebServer, Room, Jetpack Compose, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-17-conversation-companion-v2-design.md`

## Global Constraints

- Android minSdk 26, targetSdk 36, Java/Kotlin target 17.
- Default model is `qwen3.5:9b` through Ollama native `POST /api/chat`.
- Every model request uses `stream: false` and `think: false`.
- Model messages are limited to the latest 12 turns plus the durable summary, facts, and next direction.
- No account, analytics, advertising, or cloud conversation storage.
- Existing story reading, TTS, practice, and progress flows remain operational.

---

### Task 1: Structured conversation prompt and response parser

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationModels.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationPrompt.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/conversation/ConversationPromptTest.kt`

**Interfaces:**
- Produces: `ConversationRequest`, `ConversationTurn`, `ConversationOutcome`, `ConversationPrompt.messages(request)`, and `ConversationOutcomeParser.parse(content)`.

- [x] Write tests proving age limits, durable memory, latest-12 history, valid JSON parsing, and rejection of empty/malformed replies.
- [x] Run `./gradlew testDebugUnitTest --tests '*ConversationPromptTest'` and confirm failures are caused by missing production types.
- [x] Implement the minimal serializable models, bounded prompt builder, and strict parser.
- [x] Re-run the focused tests and then all unit tests.
- [ ] Commit with `feat: add structured conversation contract`.

### Task 2: Ollama native conversation client

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationClient.kt`
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/OllamaConversationClient.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/conversation/OllamaConversationClientTest.kt`

**Interfaces:**
- Consumes: `ConversationPrompt.messages` and `ConversationOutcomeParser.parse`.
- Produces: `ConversationClient.chat(request)` and `ConversationClient.connectionTest()` returning `ConversationResult`.

- [x] Write MockWebServer tests that assert `/api/chat`, `think:false`, `stream:false`, optional bearer token, `/v1` base URL normalization, real non-empty connection test, authentication mapping, timeout mapping, and one malformed-response repair request.
- [x] Run the focused tests and confirm RED.
- [x] Implement the native Ollama request/response transport and one repair retry.
- [x] Run focused and full unit tests.
- [ ] Commit with `fix: use native ollama conversation api`.

### Task 3: Room conversation persistence

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationDatabase.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/progress/ProgressDatabase.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/app/AppContainer.kt`
- Test: `app/src/androidTest/java/com/kidsenglish/companion/conversation/ConversationDatabaseTest.kt`

**Interfaces:**
- Produces: `ConversationRepository.observe(ageBand)`, `appendUserTurn`, `applyOutcome`, and `clear(ageBand)`.

- [x] Write a Room instrumentation test proving ordered turn persistence, atomic outcome update, age-band isolation, and clear behavior.
- [x] Run the focused instrumentation test and confirm RED.
- [x] Add entities, DAO, repository, and database version 2 migration that creates new tables without deleting learning progress.
- [x] Wire the repository and Ollama client in `AppContainer`.
- [x] Run instrumentation tests and all unit tests.
- [ ] Commit with `feat: persist conversation memory`.

### Task 4: Conversation-first state and Compose screen

**Files:**
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/LearningNavigator.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/KidsEnglishApp.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/settings/AppSettingsRepository.kt`
- Modify: `app/src/androidTest/java/com/kidsenglish/companion/ui/LearningFlowTest.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/ui/LearningNavigatorTest.kt`

**Interfaces:**
- Consumes: `ConversationClient` and `ConversationRepository`.
- Produces: conversation-first navigation, `sendConversationMessage`, `clearConversation`, and visible summary/facts/direction state.

- [x] Update navigator and Compose tests first to require conversation immediately after age selection, text send controls, summary panel, and access to Stories/Progress/Settings.
- [x] Run focused tests and confirm RED.
- [x] Implement `CONVERSATION` navigation, state collection, guarded sending, persistence, failure recovery, and the new screen.
- [x] Change new-install defaults to `http://10.0.2.2:11434` and `qwen3.5:9b`, while normalizing persisted `/v1` endpoints in the client.
- [x] Run unit and UI instrumentation tests.
- [ ] Commit with `feat: make conversation the primary experience`.

### Task 5: Core end-to-end verification and documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/ollama-local.md`

**Interfaces:**
- Verifies all interfaces produced by Tasks 1–4.

- [x] Update setup docs for `ollama pull qwen3.5:9b`, root Ollama URL, and the real chat connection test.
- [x] Run `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug`.
- [x] Install the APK, configure the emulator for `qwen3.5:9b`, send two related turns, and verify the second reply plus summary are visible.
- [x] Capture a screenshot and inspect Logcat for crashes.
- [ ] Commit with `docs: document conversation model setup`.
