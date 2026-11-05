# Flutter Client Design

## Goal

Add a Flutter client under `flutter/` for the Kids English Companion repository. It should provide the shared learning experience on Android and iOS and follow the existing Android application as the functional and visual reference.

## Product Context

The project already has a mature native Android client and a native iOS client. The Android application is the source reference for the Flutter client because its current product flow contains the complete conversation, story, practice, progress, settings, and voice experience. The iOS app remains a separate native application. The Flutter app does not replace or modify either native app in this project phase.

## Visual Direction

- **Visual thesis:** Use the Android application's warm, readable Material 3 language, with a warm-white canvas, deep ink text, and a restrained green accent.
- **Content plan:** Age selection leads into conversation; topic selection and history stay close to chat; stories, reading, speaking practice, progress, and settings remain reachable from the main flow.
- **Interaction thesis:** Preserve Android's page navigation and chat composer behavior, scroll the conversation to the newest turn, and keep touch targets comfortable for children. Use only short, functional Material press feedback.

### Android Design Tokens

| Role | Value | Source |
|---|---|---|
| Primary | `#006C4C` | `android/app/src/main/java/com/kidsenglish/companion/ui/theme/Theme.kt` |
| Secondary | `#7A5900` | Same |
| Canvas and surface | `#FFF8EE` | Same |
| Main text | `#1E1B16` | Same |

The Flutter app uses a light Material 3 theme and the Android page hierarchy. It does not copy the iOS tab layout or introduce a new visual system.

## Scope

### Included in the Flutter Core

1. Age selection for 6–8, 9–10, and 11–12, with separate conversation and learning records per age group.
2. General, predefined, and custom conversation topics; session history; persisted turns, summary, remembered facts, and next direction.
3. Ollama `/api/tags` model discovery, `/api/chat` conversation, connection test, refresh, manual model entry, optional Bearer token, connection errors, and offline states.
4. The 18 original graded stories, offline story reading, sentence playback, vocabulary, fixed comprehension questions, story-grounded tutor questions, and completion state.
5. Speaking practice with speech recognition, normalized word-match feedback, retry, and saved progress.
6. Voice conversation with explicit start and stop controls and visible listening/thinking/speaking states. Recognition runs as one short utterance per listen call; after the assistant speaks, the app starts a new listen call only while the user-started session remains active.
7. Settings for model endpoint, model, token, age band, speech rate, and TTS preference (`自动 / Google / 讯飞`).
8. Deterministic input safety checks, child privacy rules, and bounded model outputs equivalent to the Android behavior.

### Platform-Specific Follow-Up

Android lock-screen conversation requires the native foreground service and lock-screen activity behavior or a platform-channel adapter. It is a separate follow-up milestone. The Flutter core must not present lock-screen continuation as working until the native bridge is implemented and manually verified.

On Android, Google and iFlytek TTS engine selection is part of the Flutter core through the Android engine enumeration and selection APIs exposed by `flutter_tts`. Automatic mode tries Google English, then iFlytek English, then the system default. On iOS, use the system English voice and explain that provider choice is unavailable. The `speech_to_text` plugin is designed for short phrases, not a long-running microphone stream; the conversation loop therefore uses separate short recognition calls and remains user-stoppable. If that sequence is unreliable on a target device, provide a tap-to-talk fallback and do not claim continuous listening.

iOS voice recognition and background continuation remain subject to Apple's APIs and device policy. Flutter does not promise lock-screen recognition on iOS. Text chat and reading remain available if speech services are unavailable.

## Architecture

Use a small feature-oriented Flutter structure with dependency direction toward domain contracts:

```text
lib/
  app/                 Material theme, app shell, route setup
  domain/              age bands, topics, messages, sessions, stories, scores
  data/                Ollama client, local database, preferences, secure token store
  features/
    onboarding/
    conversation/
    history/
    stories/
    practice/
    progress/
    settings/
  platform/            speech/TTS adapters and later native-channel contracts
```

Each feature owns presentation and state transitions. Data access and platform audio are exposed through small interfaces so a screen can stay usable when a network or speech provider fails.

### State, Navigation, and Dependencies

- Use Flutter Material 3 widgets and Navigator-based page navigation matching the Android flow.
- Use `ChangeNotifier` view models exposed with `provider` for observable screen state, following the existing Android ViewModel boundary without introducing code generation.
- Use `package:http` for Ollama requests. Apply explicit timeouts, response validation, and bounded error messages.
- Use `sqflite` for local sessions, ordered turns, story completion, and practice results. Writes that update a conversation turn and its memory are transactional.
- Use `shared_preferences` for non-secret user preferences and `flutter_secure_storage` for the optional Bearer token.
- Use `speech_to_text` for user-started short-utterance recognition and `flutter_tts` for sentence/reply playback, speech-rate control, and Android engine selection. Isolate both behind platform-facing services. The speech plugin documents short phrases as its target use case, so the app must stop and restart recognition around each conversational turn rather than rely on one continuous stream.

The Flutter app has its own local database and secure-storage namespace. It does not migrate or share history with the native Android or iOS apps in this phase.

## Data and Service Contracts

### Stories

Copy the three existing JSON story files into `flutter/assets/stories/` and declare them as bundled Flutter assets. Preserve their schema and IDs, and compare the copied files directly with the Android source assets during setup.

### Ollama Conversation

- Read installed models from `GET /api/tags`.
- Send chat requests to `POST /api/chat` using `stream: false`, `think: false`, configured model, and age-specific output limits.
- Persist the complete local conversation, but send only the durable memory plus the most recent bounded turn window to the model.
- Parse the structured response (`reply`, `conversationSummary`, `nextDirection`, and `rememberedFacts`); require a non-empty reply and enforce field length limits.
- Retry malformed structured output once with a repair instruction. Do not store a failed assistant reply.
- Keep secrets out of source, logs, crash messages, and request diagnostics.

### Child Safety and Privacy

- Reject empty, oversized, or clearly unsafe/personal-information requests before they reach the model.
- Keep the assistant on the selected age-appropriate topic and avoid soliciting identity, address, school, contact details, photos, location, or secrets.
- Send only the current learning context and bounded chat context to the configured Ollama service.
- Store conversation, memory, and learning progress locally. Do not add accounts, analytics, advertising, or cloud sync.
- Keep stories, reading, and fixed practice usable while Ollama is unavailable.

## Failure Behavior

- **Ollama unavailable:** preserve the draft and history; offer retry; leave local reading and practice available.
- **Authentication rejected:** show a concise message pointing to model settings without logging the token.
- **Model list unavailable:** retain the saved/manual model name and allow manual editing.
- **Malformed model output:** repair once, then show a recoverable error without persisting the malformed turn.
- **Speech unavailable or denied:** explain the device permission/service issue and retain text chat and reading controls.
- **TTS unavailable:** retain readable story and response text and continue without speech.
- **Local storage failure:** report the failure and keep the current screen usable where possible; never silently claim data was saved.

## Verification and Acceptance

### Core Acceptance

1. `flutter pub get` and `flutter analyze` complete successfully.
2. Android and iOS builds succeed at their configured deployment targets.
3. The first launch offers the three age groups and then enters the conversation flow.
4. Selecting a topic creates an independent session; app restart restores turns, memory, and selected age group.
5. A model refresh reads `/api/tags`; a successful conversation reads `/api/chat` and updates the reply, summary, facts, and next direction together.
6. Endpoint, authentication, timeout, and malformed-response failures do not lose the draft or write a failed assistant turn.
7. The story library shows six bundled stories for the selected age group and remains usable offline.
8. Sentence playback, vocabulary, fixed comprehension questions, story-grounded questions, speaking recognition, and word-match progress work through the available device services.
9. Tapping stop ends the user-started voice loop; the app never starts microphone capture silently.
10. The Flutter UI uses the Android theme values and page flow rather than the existing iOS tab structure.
11. No API key or live credential appears in source or committed assets.

### Native Bridge Acceptance, Later Milestone

1. Android shows an ongoing foreground notification with a clear Stop action during a voice session.
2. An explicitly started voice session can continue across Android screen lock as the native app currently supports.
3. iOS limitations are stated in settings/help; no unsupported lock-screen or provider-selection promise is shown.

The Flutter SDK is installed at `/Users/lisi/flutter` (Flutter 3.47.5 stable, Dart 3.13.4). The current Codex command environment omits `/Users/lisi/flutter/bin` from `PATH`, so invoke the SDK by full path or set `PATH` explicitly. Full Xcode 27.1 is installed and recognized when `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` is set. `flutter doctor` reports Android SDK 36 and JDK 21, but some Android SDK licenses remain unaccepted; resolve that before Android builds if Gradle requires those components. An iPhone 18 Pro simulator is available. Do not claim successful Flutter builds until the actual commands have run.

## Non-Goals

- Replacing or restructuring the native Android or native iOS applications.
- Migrating conversation history between Android, Flutter, and native iOS.
- Bundling an LLM in the app.
- Bundling copyrighted commercial books.
- Introducing user accounts, cloud synchronization, ads, or analytics.
- Claiming professional speech or pronunciation assessment.
- Claiming guaranteed lock-screen speech recognition on iOS.

## Risks and Decisions

1. Flutter audio plugins differ by OS and installed service. Keep the feature behind interfaces and test on both simulator/emulator and real hardware.
2. Android lock-screen behavior has existing native code and must be bridged deliberately rather than approximated in Dart.
3. Duplicated story JSON can drift. Compare the Flutter copies directly with the Android source assets during setup.
4. Flutter is installed; command `PATH` must be set explicitly, and Android license acceptance may be needed for Android builds.
5. Native and Flutter apps keep separate local records. This is intentional for the first implementation and avoids an unplanned data migration.
