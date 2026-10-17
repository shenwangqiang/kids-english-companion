# Voice and Lock-Screen Conversation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit hands-free speech loop that remains usable when the device locks during a user-started session.

**Architecture:** Extend the speech boundary with TTS completion callbacks, then drive a small deterministic voice-session state machine from `AppViewModel`. A microphone foreground service owns the ongoing-notification lifecycle while `MainActivity` controls lock-screen visibility and forwards the notification Stop action.

**Tech Stack:** Android SpeechRecognizer, TextToSpeech, foreground service type microphone, notifications, Kotlin coroutines/StateFlow, Jetpack Compose.

**Spec:** `docs/superpowers/specs/2026-10-17-conversation-companion-v2-design.md`

## Global Constraints

- Voice capture starts only from a visible activity after explicit user action.
- No wake word, boot receiver, or silent background microphone start.
- Stop always cancels recognition and TTS, stops the foreground service, and removes lock-screen visibility.
- Text conversation remains usable without speech services.

---

### Task 1: Deterministic voice-session state machine

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/VoiceSession.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/conversation/VoiceSessionTest.kt`

**Interfaces:**
- Produces: `VoicePhase`, `VoiceEvent`, and `VoiceSessionReducer.reduce(state, event)`.

- [x] Write tests for idle-to-listening, recognition-to-thinking, reply-to-speaking, TTS-complete-to-listening, recoverable errors, and Stop from every active phase.
- [x] Run the focused test and confirm RED.
- [x] Implement the pure reducer and re-run focused/full unit tests.
- [ ] Commit with `feat: add voice conversation state machine`.

### Task 2: Speech completion and ViewModel loop

**Files:**
- Modify: `app/src/main/java/com/kidsenglish/companion/speech/SpeechController.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/AppViewModel.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/ui/KidsEnglishApp.kt`
- Test: `app/src/test/java/com/kidsenglish/companion/conversation/VoiceSessionTest.kt`

**Interfaces:**
- Extends: `SpeechController.speak(text, rate, onDone, onError)`.
- Produces: `startVoiceConversation`, `stopVoiceConversation`, and automatic listening after successful TTS completion.

- [x] Add failing reducer/controller-facing tests for callback-driven loop behavior.
- [x] Implement `UtteranceProgressListener`, guarded recognition callbacks, ViewModel orchestration, and Start/Stop/Retry UI.
- [x] Run focused and full unit tests.
- [ ] Commit with `feat: add hands-free conversation loop`.

### Task 3: Foreground microphone service and lock-screen activity

**Files:**
- Create: `app/src/main/java/com/kidsenglish/companion/conversation/ConversationForegroundService.kt`
- Modify: `app/src/main/java/com/kidsenglish/companion/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/java/com/kidsenglish/companion/ui/LearningFlowTest.kt`

**Interfaces:**
- Produces: service start/stop actions, microphone notification channel, MainActivity stop receiver, and dynamic `setShowWhenLocked` behavior.

- [x] Add an instrumentation test requiring Start conversation to show active voice controls and Stop to restore idle state.
- [x] Declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, and a non-exported microphone foreground service.
- [x] Implement notification Open/Stop actions and activity lock-screen visibility synchronization.
- [x] Run instrumentation tests.
- [ ] Commit with `feat: support lock-screen voice sessions`.

### Task 4: Device acceptance

**Files:**
- Modify: `README.md`

**Interfaces:**
- Verifies the complete voice and lock-screen flow.

- [x] Run `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug`.
- [ ] Start a voice session on a device with English speech recognition, answer one question, confirm automatic TTS and listening restart, lock the device, and confirm the notification and conversation activity remain available.
- [ ] Press Stop from the notification and confirm microphone capture, TTS, service, and lock-screen visibility all end.
- [x] Document speech-provider and lock-screen limitations.
- [ ] Commit with `docs: document voice conversation behavior`.
