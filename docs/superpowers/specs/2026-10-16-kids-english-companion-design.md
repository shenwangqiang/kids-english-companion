# Kids English Companion — Android MVP Design

## Goal

Build a standalone Android application for children aged 6–12 that combines graded English reading, sentence playback, speaking practice, story-grounded AI conversation, comprehension questions, and local learning progress.

The first release must be usable without paid model APIs. During development, the Android app connects to Qwen running through Ollama on a local computer. The same client can later connect to a protected remote Ollama-compatible endpoint by changing settings.

## Success Criteria

1. A child can select one of three age bands: 6–8, 9–10, or 11–12.
2. The library contains 18 original graded English stories, six per age band.
3. A child can read a story, play an entire story or one sentence through Android text-to-speech, and view a short Chinese explanation and vocabulary notes.
4. A child can record a sentence, receive the recognized text, and see a transparent word-match score against the target sentence.
5. The child can ask questions or answer comprehension questions about the current story.
6. AI answers use retrieved excerpts from the active story, remain age-appropriate, and decline unrelated or unsafe conversations.
7. Reading, playback, speaking practice, and progress tracking continue to work when the model service is unavailable.
8. The app can switch between a local-network Ollama endpoint and a protected remote endpoint without rebuilding.

## Scope

### Included

- Native Android application written in Kotlin with Jetpack Compose.
- Three age bands with separate language constraints.
- Offline story catalog and story content packaged with the application.
- Story reading, sentence selection, Android text-to-speech, and Android speech recognition.
- Simple, explainable speaking score based on normalized recognized words versus the target sentence.
- Story-grounded tutor chat and generated comprehension questions.
- Local progress history using Room.
- Configurable OpenAI-compatible model endpoint, model name, and optional bearer token.
- Clear degraded behavior when speech or AI services are unavailable.
- Unit tests for content selection, grounding, prompt rules, scoring, and progress updates.
- UI smoke tests for the primary learning flow.

### Excluded from MVP

- Accounts, payments, subscriptions, social features, and cloud synchronization.
- Copyrighted commercial textbooks or copied Oxford/Cambridge course material.
- Professional phoneme-level pronunciation assessment.
- Teacher administration portal.
- Live classes, video, image generation, and open-ended general assistant behavior.
- Shipping an LLM inside the APK.

## Content and Licensing

The MVP ships with 18 original stories created specifically for this application. Each story records:

- Stable identifier, title, age band, and estimated CEFR level.
- Story paragraphs split into speakable sentences.
- Target vocabulary with English definition and concise Chinese explanation.
- Three fixed comprehension questions and expected-answer concepts.
- Attribution metadata, even though first-party original content needs no external attribution.

Openly licensed third-party stories may be added later only when their license and attribution are stored with the content. Commercial children's books are not ingested without written permission.

The model is not fine-tuned on the books. The app retrieves relevant story passages and sends them with a constrained tutoring prompt. This makes the source material inspectable and replaceable and prevents training data from becoming a hidden product dependency.

## Age Bands

| Band | Reading behavior | Tutor behavior |
|---|---|---|
| 6–8 | Short sentences, common concrete vocabulary, frequent repetition | One idea per reply, encouraging tone, English first with brief Chinese help |
| 9–10 | Short paragraphs, broader everyday vocabulary, simple inference | Two to four sentences, guided questions, limited grammar explanation |
| 11–12 | Longer paragraphs, richer vocabulary, cause/effect and opinion prompts | Concise discussion, paraphrasing, vocabulary and grammar coaching |

The chosen band controls visible content, prompt rules, reply length, vocabulary difficulty, and speech rate. It does not claim to diagnose or measure the child's ability.

## Architecture

```text
Compose UI
  |-- Library / Reader / Practice / Progress / Settings
  |
ViewModels
  |-- library, reading session, practice session, settings
  |
Domain services
  |-- StoryRepository
  |-- PassageRetriever
  |-- TutorPolicy + PromptBuilder
  |-- SpeakingScore
  |-- SpeechController
  |-- TutorClient
  |
Data adapters
  |-- Packaged JSON stories
  |-- Room progress database
  |-- Android SpeechRecognizer and TextToSpeech
  `-- OpenAI-compatible HTTP client
          |
          `-- Ollama + Qwen3 on local or remote machine
```

Dependencies point inward: UI depends on domain interfaces, while storage, speech, and network implementations sit behind those interfaces. The story and scoring paths therefore remain testable without a device microphone or live model.

## Model Service

The default development setup is Ollama on the developer's Mac with Qwen3-4B. Qwen3-1.7B is the fallback for memory-constrained machines. Both are open-weight models under Apache 2.0; running them locally incurs no per-request API charge.

The Android settings screen contains:

- Base URL.
- Model name.
- Optional bearer token.
- Connection test action.
- A local-network HTTP toggle available only in debug builds.

Release builds require HTTPS. A remote deployment must sit behind TLS and authentication; the Ollama port must not be exposed directly because the local API has no authentication by default.

The API client uses the OpenAI-compatible chat completion interface so that local Ollama, remote Ollama, or another compatible service can be selected without changing application code.

## Retrieval and Tutor Guardrails

Retrieval is deliberately simple for the MVP. Every tutor request is scoped to one active story. The retriever ranks story paragraphs using normalized token overlap with the user's question and selects the best passages, while always including the current sentence or paragraph.

The model receives:

1. The child's selected age band.
2. A fixed tutor policy.
3. The story title and retrieved passages.
4. The recent story conversation, capped to a small window.
5. The child's question or answer.

The tutor must:

- Use only facts supported by supplied story passages.
- Say it cannot find the answer in the story when evidence is absent.
- Keep output within the age band's length and vocabulary rules.
- Avoid asking for names, addresses, school information, contact information, photos, or secrets.
- Reject sexual, violent, self-harm, illegal, manipulative, or unrelated general-assistant requests with a short child-safe redirect to the story.
- Never claim that its pronunciation score is a professional assessment.

Deterministic application checks reject empty input, oversized input, obvious personal-information patterns, and unsupported conversation modes before calling the model. Model output is length-capped and displayed as untrusted generated content.

## Speech and Speaking Score

Android TextToSpeech reads a selected sentence or the full story. The age band selects a conservative default speech rate, and the child can replay any sentence.

Android SpeechRecognizer handles push-to-talk recognition. The MVP score is calculated by normalizing case and punctuation, tokenizing the target and recognized strings, and comparing word sequence coverage. The result shows:

- Overall match percentage.
- Correct, missed, and extra words.
- Recognized sentence for transparency.

Recognition confidence is shown when the platform supplies it but is not presented as phoneme accuracy. If recognition is unavailable, the app explains how to install or enable an English recognition service and leaves reading features operational.

## Screens and Main Flow

1. **Welcome:** choose an age band; no child name or account is requested.
2. **Library:** show only stories for that band, reading progress, and completion state.
3. **Reader:** show story paragraphs, vocabulary, sentence playback, and entry points to speaking practice or tutor chat.
4. **Speaking practice:** record one selected sentence, show recognition and word-match feedback, then allow retry.
5. **Story tutor:** answer story-grounded questions and run comprehension questions.
6. **Progress:** completed stories, practice attempts, and best sentence-match scores stored locally.
7. **Settings:** age band, speech rate, model endpoint, model name, optional token, and connection test.

## Data and Privacy

- No user account, analytics SDK, advertising SDK, or cloud progress storage is included.
- Story and progress data stay on the device.
- Audio is sent to the active Android speech-recognition provider; whether that provider is offline depends on the device and installed language pack. The UI discloses this limitation.
- Tutor requests send the current question, recent story conversation, age band, and retrieved story excerpts to the configured model endpoint.
- The application does not request or store a child's real name, address, school, contacts, photos, or precise location.
- The optional model access token is stored using Android encrypted preferences or Keystore-backed storage and is never logged.

## Failure Handling

- **Model unavailable:** show a concise retry action; preserve the draft; keep reading, TTS, fixed comprehension questions, and progress available.
- **Speech recognizer unavailable:** keep text interaction and playback available; show device setup guidance.
- **TTS unavailable:** keep text reading and AI chat available; show language-engine setup guidance.
- **Malformed model output:** discard it and present a safe generic redirect instead of rendering unchecked structured data.
- **Remote timeout:** one bounded retry for idempotent tutor requests, followed by an offline-state message.
- **Database failure:** do not block reading; omit progress updates and report the local storage error.

## Verification

### Automated

- Story schema validation confirms 18 stories, six per age band, unique identifiers, vocabulary, and comprehension questions.
- Unit tests cover passage ranking, prompt age constraints, safety rules, scoring normalization, and progress updates.
- HTTP client tests cover success, timeout, authentication failure, invalid response, and unavailable service.
- Compose UI tests cover age selection, opening a story, sentence selection, offline tutor state, and stored progress.
- Debug APK compilation and Android lint must pass.

### Manual Acceptance

- Install on an emulator and at least one physical Android device.
- Connect once to Ollama on the same LAN and complete a grounded story question.
- Change the endpoint to an unreachable address and confirm non-AI reading features remain usable.
- Read a sentence aloud and confirm recognized text and word-level differences are visible.
- Verify the model refuses unrelated personal-data requests and redirects to the story.
- Verify no API token, child speech, or personal information appears in application logs.

## Delivery and Rollback

The first delivery is a standalone Git repository at `/Users/lisi/AndroidStudioProjects/kids-english-companion`. It includes application source, original story content, tests, and local/remote Ollama setup documentation.

The model integration is isolated behind `TutorClient`. It can be disabled by clearing the endpoint, replaced with another OpenAI-compatible implementation, or later replaced with an on-device LiteRT-LM adapter without migrating story or progress data.

Removing the application removes all locally stored progress. No server-side child data migration or rollback is required in the MVP.

## Key Risks

1. Android system speech recognition quality varies across devices, especially for children's voices. The MVP exposes recognized text and avoids claiming professional pronunciation scoring.
2. Qwen3-1.7B may not follow grounding and safety instructions reliably enough. Qwen3-4B is the default, with deterministic input checks and output limits around it.
3. A remote Ollama instance is unsafe if exposed without authentication. Release builds require HTTPS and deployment documentation mandates an authenticated reverse proxy.
4. Model availability is not guaranteed. Core reading and fixed practice flows remain local and functional when AI is unavailable.

The most fragile assumption is that a system prompt plus retrieved story context provides acceptable child-safe behavior. If testing disproves this, AI chat will remain disabled by default for release builds until a stricter server-side moderation layer is added; the reading and speaking-practice product remains usable.
