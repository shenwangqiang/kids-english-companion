# Flutter Client Implementation Plan

> **For agentic workers:** Follow this plan task by task and keep each task reviewable. The user approved inline continuation by asking to continue development.

**Goal:** Build a functional Flutter client in `flutter/` that follows the Android app's feature flow and visual tokens on Android and iOS.

**Architecture:** Use feature-owned Flutter screens and `ChangeNotifier` view models, with domain models and repository interfaces separated from SQLite, Ollama, secure storage, and speech plugins. Use the Android app as the source for behavior, prompt rules, story data, and Material 3 tokens. Keep Android lock-screen continuation as a later native bridge.

**Tech Stack:** Flutter 3.47.5 / Dart 3.13.4, Material 3, `provider`, `http`, `sqflite`, `path`, `shared_preferences`, `flutter_secure_storage`, `speech_to_text`, and `flutter_tts`.

**Spec:** `docs/superpowers/specs/2026-11-05-flutter-client-design.md`

## Global Constraints

- Work only under `flutter/`, plus the root README and Flutter implementation docs.
- Target Android API 26+ and iOS 17+.
- Match Android tokens: primary `#006C4C`, secondary `#7A5900`, canvas/surface `#FFF8EE`, main text `#1E1B16`.
- Do not modify the native Android or native iOS applications.
- Do not migrate or share native app history; Flutter gets its own database.
- Bundle only the existing 18 original stories; do not add commercial book text.
- Keep model tokens in secure storage and never print tokens or child utterances to logs.
- Speech input uses short, separate utterances. Stop and restart recognition around each turn; never claim an always-open microphone.
- Android lock-screen continuation is deferred. Do not show a working lock-screen control in this phase.
- Use `/Users/lisi/flutter/bin/flutter`; set `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` for iOS commands.
- Do not add or run automated tests in this implementation. Validate with `flutter analyze` and platform builds; Android builds may require the user to accept SDK licenses first.

---

## File Structure

```text
flutter/
  lib/
    main.dart
    app/                   app shell, navigation, theme
    domain/                age, topic, conversation, story, practice, progress models
    data/                  database, preferences, secure token, Ollama client
    features/
      onboarding/ conversation/ history/ stories/ practice/ progress/ settings/
    platform/              speech and TTS service adapters
  assets/stories/          three bundled age-band JSON files
```

Keep each file responsible for one feature or adapter. Do not add generated state-management code.

### Task 1: Generate the Flutter Android and iOS scaffold

**Files:**
- Create: `flutter/pubspec.yaml`, `flutter/lib/main.dart`, `flutter/android/`, `flutter/ios/`, `flutter/.metadata`, and standard Flutter project files
- Preserve and update: `flutter/README.md`
- Modify: `.gitignore`

- [ ] Confirm the Flutter tool path and run the scaffold command without overwriting the existing README:

```bash
/Users/lisi/flutter/bin/flutter create \
  --project-name kids_english_companion \
  --org com.shenwangqiang \
  --platforms android,ios \
  --no-pub \
  --no-overwrite \
  flutter
```

- [ ] Inspect the generated Android application ID and iOS bundle ID; keep them under `com.shenwangqiang` and set the app display name to `英语伙伴`.
- [ ] Set Android `minSdk` to 26 and iOS deployment target to 17.0 in the generated platform projects.
- [ ] Expand `.gitignore` narrowly for Flutter build outputs, plugin metadata, Pods, and generated Xcode files; preserve existing Android and iOS ignore rules.
- [ ] Update `flutter/README.md` with the Flutter SDK version, run commands, and the Android and iOS endpoint setup notes.

**Checkpoint:** `flutter/pubspec.yaml`, platform folders, and the existing README are all present; project identity and deployment floors are correct.

### Task 2: Add dependencies, theme, domain models, and story assets

**Files:**
- Create: `flutter/lib/app/app_theme.dart`
- Create: `flutter/lib/domain/age_band.dart`
- Create: `flutter/lib/domain/conversation_models.dart`
- Create: `flutter/lib/domain/story_models.dart`
- Create: `flutter/lib/domain/practice_models.dart`
- Create: `flutter/assets/stories/age_6_8.json`
- Create: `flutter/assets/stories/age_9_10.json`
- Create: `flutter/assets/stories/age_11_12.json`
- Modify: `flutter/pubspec.yaml`

- [ ] Add the selected packages:

```bash
cd flutter
/Users/lisi/flutter/bin/flutter pub add provider http sqflite path shared_preferences flutter_secure_storage speech_to_text flutter_tts
```

- [ ] Declare story JSON as bundled assets in `pubspec.yaml`.
- [ ] Copy each JSON file from `android/app/src/main/assets/stories/` and compare each source/destination pair with `cmp`.
- [ ] Define `AgeBand` values `6-8`, `9-10`, `11-12`, the existing topic catalog, chat message/session/memory/outcome models, and story/paragraph/sentence/vocabulary/question models.
- [ ] Make JSON decoding validate required fields and preserve story IDs, age bands, and English text without hardcoded content duplication.
- [ ] Define the `ThemeData` color scheme from the exact Android values, with light Material 3 components and Android-like app-bar/page navigation.

The domain model wire values should be explicit and independent from localized labels:

```dart
enum AgeBand {
  sixToEight('6-8', 30, 180),
  nineToTen('9-10', 50, 240),
  elevenToTwelve('11-12', 75, 300);

  const AgeBand(this.wireValue, this.replyWordLimit, this.maxOutputTokens);
  final String wireValue;
  final int replyWordLimit;
  final int maxOutputTokens;
}

class ConversationOutcome {
  const ConversationOutcome({
    required this.reply,
    required this.conversationSummary,
    required this.nextDirection,
    required this.rememberedFacts,
  });

  final String reply;
  final String conversationSummary;
  final String nextDirection;
  final List<String> rememberedFacts;
}
```

**Checkpoint:** Assets are bundled, all 18 stories are represented, and Flutter domain models use the same wire values as Android.

### Task 3: Build local persistence and settings storage

**Files:**
- Create: `flutter/lib/data/local_database.dart`
- Create: `flutter/lib/data/session_repository.dart`
- Create: `flutter/lib/data/progress_repository.dart`
- Create: `flutter/lib/data/settings_repository.dart`
- Create: `flutter/lib/data/secure_token_store.dart`

- [ ] Create SQLite tables for conversation sessions, ordered messages, story completion, and speaking-practice attempts. Store timestamps as integer milliseconds and stable IDs as text.
- [ ] Add a database version and explicit `onCreate`/`onUpgrade` handling. Update a session's last turn and memory in one `sqflite` transaction.
- [ ] Implement repository methods for age-scoped session lookup, create/select/delete, chronological messages, story completion, and best speaking score.
- [ ] Persist non-secret endpoint, selected model, age band, speech rate, and TTS preference with `shared_preferences`.
- [ ] Store and remove Bearer tokens only through `flutter_secure_storage`; never read them into diagnostics or UI status text.
- [ ] Define default model `qwen3.5:4b-mlx`, endpoint `http://127.0.0.1:11434`, and TTS preference `auto` to match the native project.

Use these table contracts, adding indexes for chronological message lookup:

```sql
CREATE TABLE sessions (
  id TEXT PRIMARY KEY,
  age_band TEXT NOT NULL,
  topic_json TEXT NOT NULL,
  title TEXT NOT NULL,
  summary TEXT NOT NULL,
  next_direction TEXT NOT NULL,
  remembered_facts_json TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE messages (
  id TEXT PRIMARY KEY,
  session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX messages_session_time ON messages(session_id, created_at);
```

**Checkpoint:** App state survives a process restart, age groups remain isolated, and secret storage is separate from preferences.

### Task 4: Implement Ollama service and child-input policy

**Files:**
- Create: `flutter/lib/data/ollama_client.dart`
- Create: `flutter/lib/domain/child_input_policy.dart`
- Create: `flutter/lib/domain/conversation_prompt.dart`

- [ ] Add `availableModels()` using `GET /api/tags` and `sendChat()` using `POST /api/chat`.
- [ ] Send `stream: false`, `think: false`, configured model, bounded `num_predict`, `keep_alive: 30m`, and the optional Bearer header.
- [ ] Implement endpoint validation matching Android: HTTPS for remote services, HTTP only for localhost/private-network development endpoints, no URL user/password or fragment.
- [ ] Build prompts from age band, selected topic, persisted summary/facts/direction, recent bounded turns, and the current child message.
- [ ] Apply deterministic empty/length/personal-information checks before network calls. Cap output fields and parse the required structured response.
- [ ] Retry malformed structured output once with a JSON-repair prompt. Return a recoverable typed error without saving a failed assistant turn.
- [ ] Keep the composer draft when network, timeout, or authentication failures occur; do not log request bodies, tokens, or speech transcripts.

The chat request body must keep Ollama's native fields explicit:

```dart
final body = <String, Object?>{
  'model': connection.model,
  'messages': messages,
  'stream': false,
  'think': false,
  'keep_alive': '30m',
  'options': {'temperature': 0.2, 'num_predict': ageBand.maxOutputTokens},
};
```

**Checkpoint:** The service contract and prompt behavior match the native Android `/api/chat` implementation, with useful offline and authentication errors.

### Task 5: Implement app state, onboarding, topic selection, chat, and history

**Files:**
- Create: `flutter/lib/app/app.dart`
- Create: `flutter/lib/features/onboarding/onboarding_page.dart`
- Create: `flutter/lib/features/conversation/conversation_view_model.dart`
- Create: `flutter/lib/features/conversation/conversation_page.dart`
- Create: `flutter/lib/features/conversation/topic_picker_page.dart`
- Create: `flutter/lib/features/conversation/message_bubble.dart`
- Create: `flutter/lib/features/history/history_page.dart`
- Modify: `flutter/lib/main.dart`

- [ ] Provide repositories and feature view models through `MultiProvider`; restore the selected age and session before showing the conversation.
- [ ] Build the three-age welcome screen, then enter chat after selection.
- [ ] Add general, predefined, and 60-character custom topics. Starting a topic creates an independent age-scoped session.
- [ ] Render scrollable user and assistant message bubbles with tap-to-replay controls, a fixed composer at the bottom, send/loading/error states, and scroll-to-latest behavior.
- [ ] Preserve the draft on send failure. Serialize one active model request per session, and append a successful assistant reply and memory only to the originating session.
- [ ] Add the recent-first conversation history list, session selection, and delete action.
- [ ] Reuse Android labels and hierarchy; do not copy iOS tabs or introduce custom branding not present in the Android client.

The view model tracks active requests by session ID so a topic switch cannot attach a late response to the wrong session. Persist a user turn and its structured assistant result together only after a valid model response; this leaves the composer draft intact on request failure:

```dart
Future<void> send(String text) async {
  final session = state.activeSession;
  if (session == null || activeRequestIds.contains(session.id)) return;
  if (childInputPolicy.isBlocked(text)) {
    state = state.copyWith(errorMessage: childInputPolicy.redirect);
    notifyListeners();
    return;
  }
  activeRequestIds.add(session.id);
  state = state.copyWith(errorMessage: null);
  notifyListeners();
  try {
    final outcome = await ollama.reply(session: session, childMessage: text);
    await sessions.appendSuccessfulTurn(session.id, text, outcome);
    if (state.draft.trim() == text.trim()) state = state.copyWith(draft: '');
    if (state.activeSession?.id == session.id) {
      state = state.copyWith(activeSession: await sessions.get(session.id));
    }
  } catch (error) {
    state = state.copyWith(errorMessage: friendlyMessage(error));
  } finally {
    activeRequestIds.remove(session.id);
    notifyListeners();
  }
}
```

**Checkpoint:** A user can choose an age and topic, chat through a configured local/remote Ollama server, restart the app, and resume the same history.

### Task 6: Implement stories, reader, story tutor, practice, and progress

**Files:**
- Create: `flutter/lib/data/story_repository.dart`
- Create: `flutter/lib/features/stories/story_library_page.dart`
- Create: `flutter/lib/features/stories/reader_page.dart`
- Create: `flutter/lib/features/stories/story_tutor_panel.dart`
- Create: `flutter/lib/features/practice/practice_page.dart`
- Create: `flutter/lib/features/progress/progress_page.dart`

- [ ] Load and age-filter bundled story assets without a network request.
- [ ] Build the library and reader with paragraph/sentence layout, Chinese hints, vocabulary, three fixed comprehension questions, and story completion controls.
- [ ] Implement story retrieval using normalized token overlap and include the active story evidence in the tutor prompt. Refuse answers unsupported by that story.
- [ ] Implement speaking score normalization for case and punctuation and calculate matching target words, missing words, extra words, and percentage.
- [ ] Store practice attempts and completion status locally; show the best score and completed stories on the progress page.
- [ ] Keep the complete reading and fixed comprehension flow usable when Ollama is unreachable.

Normalize practice words, align them in order with longest common subsequence, and expose matched, missed, and extra tokens like Android:

```dart
import 'dart:math' show max;

List<String> tokenize(String value) {
  final normalized = value
      .toLowerCase()
      .replaceAll(RegExp(r"[^a-z0-9' ]"), ' ')
      .trim()
      .replaceAll(RegExp(r'\s+'), ' ');
  return normalized.isEmpty ? const [] : normalized.split(' ');
}

List<(int, int)> alignLcs(List<String> target, List<String> heard) {
  final dp = List.generate(target.length + 1, (_) => List.filled(heard.length + 1, 0));
  for (var i = target.length - 1; i >= 0; i--) {
    for (var j = heard.length - 1; j >= 0; j--) {
      dp[i][j] = target[i] == heard[j]
          ? 1 + dp[i + 1][j + 1]
          : max(dp[i + 1][j], dp[i][j + 1]);
    }
  }
  final aligned = <(int, int)>[];
  var i = 0;
  var j = 0;
  while (i < target.length && j < heard.length) {
    if (target[i] == heard[j]) {
      aligned.add((i++, j++));
    } else if (dp[i + 1][j] >= dp[i][j + 1]) {
      i++;
    } else {
      j++;
    }
  }
  return aligned;
}

final targetWords = tokenize(target);
final heardWords = tokenize(recognized);
final aligned = alignLcs(targetWords, heardWords);
final score = targetWords.isEmpty ? 0 : (100 * aligned.length / targetWords.length).round();
```

**Checkpoint:** All age bands display six offline stories, and story reading, replay, tutor questions, practice scoring, and progress use the same local data.

### Task 7: Add short-turn speech, English TTS, and model/TTS settings

**Files:**
- Create: `flutter/lib/platform/speech_service.dart`
- Create: `flutter/lib/platform/tts_service.dart`
- Create: `flutter/lib/features/settings/settings_page.dart`
- Modify: `flutter/lib/features/conversation/conversation_page.dart`
- Modify: `flutter/lib/features/stories/reader_page.dart`
- Modify: `flutter/lib/features/practice/practice_page.dart`

- [ ] Wrap `speech_to_text` in a service that starts recognition only after an explicit user action and returns one English utterance at a time.
- [ ] For voice conversation, transition through listening, thinking, and speaking; after each TTS completion, begin a new short listen only while the user-started session is active. Stop, permission denial, or recognition errors must stop or pause the loop.
- [ ] Wrap `flutter_tts` for English sentence/reply playback, speech rate, utterance completion, and cancellation. On Android, enumerate installed engines and select Google English, iFlytek English, or the system default according to the preference. On iOS, use the system voice and explain provider limitations.
- [ ] Add settings for endpoint, secure Bearer token, model name, installed-model dropdown, refresh, connection test, age group, speech rate, and TTS preference.
- [ ] If `/api/tags` fails, keep the saved/manual model editable and show the connection error without clearing settings.
- [ ] Keep Android foreground lock-screen behavior out of this phase; provide no nonfunctional lock-screen switch or promise.

The short-turn loop must stop at a lifecycle or error boundary:

```dart
enum VoicePhase { idle, listening, thinking, speaking, paused }

Future<void> onAssistantSpeechFinished() async {
  if (!sessionActive || phase != VoicePhase.speaking) return;
  await listenForOneEnglishUtterance();
}
```

On Android, engine candidates follow the existing native selector: explicit preferences try only the requested engine before the system default; automatic preference tries Google, iFlytek, then default. `flutter_tts` exposes installed-engine listing and engine selection on Android. `speech_to_text` is intended for short phrases rather than continuous spoken conversion, so keep each recognition call bounded. See [speech_to_text documentation](https://pub.dev/packages/speech_to_text) and [flutter_tts documentation](https://pub.dev/packages/flutter_tts).

```dart
String? firstMatchingEngine(List<String> engines, bool Function(String) matches) {
  for (final engine in engines) {
    if (matches(engine.toLowerCase())) return engine;
  }
  return null;
}

Future<void> configureAndroidTts(TtsPreference preference) async {
  final installed = await tts.getEngines;
  final google = firstMatchingEngine(installed, (e) => e.contains('google'));
  final iflytek = firstMatchingEngine(
    installed,
    (e) => e.contains('iflytek') || e.contains('讯飞'),
  );
  final candidates = switch (preference) {
    TtsPreference.auto => [google, iflytek, null],
    TtsPreference.google => [google, null],
    TtsPreference.iflytek => [iflytek, null],
  };
  for (final engine in candidates) {
    try {
      if (engine != null) await tts.setEngine(engine);
      final available = await tts.isLanguageAvailable('en-US');
      if (available == true || available == 1) {
        await tts.setLanguage('en-US');
        return;
      }
    } catch (_) {
      continue;
    }
  }
}
```

**Checkpoint:** Reading and conversation text can be spoken, one-turn recognition works where the device service is available, and settings persist across restart.

### Task 8: Configure platform permissions and update repository documentation

**Files:**
- Modify: `flutter/android/app/src/main/AndroidManifest.xml`
- Modify: `flutter/ios/Runner/Info.plist`
- Modify: `README.md`
- Modify: `flutter/README.md`
- Modify: `docs/ollama-local.md` only if a Flutter-specific connection distinction is missing

- [ ] Add only the runtime permissions needed for Internet access, microphone, and speech recognition. Declare clear Chinese user explanations in iOS usage descriptions.
- [ ] Permit local-network Ollama development URLs only in debug builds where possible; retain HTTPS expectations for remote services.
- [ ] Update root README to describe Flutter as an active client, include commands, platform coverage, known iOS voice limits, and the Android lock-screen follow-up.
- [ ] Update Flutter README with the main flow, local database namespace, endpoint examples for simulator and physical phone, and TTS provider behavior.
- [ ] Confirm the project `LICENSE` still covers the Flutter source and bundled original story assets.

**Checkpoint:** Platform permission prompts are explicit, local development setup is documented, and repository directory descriptions match actual project state.

### Task 9: Run static analysis and platform builds

**Files:** No source changes unless a build diagnostic identifies a required fix.

- [ ] Run dependency resolution and static analysis:

```bash
cd flutter
/Users/lisi/flutter/bin/flutter pub get
/Users/lisi/flutter/bin/flutter analyze
```

- [ ] Build iOS Simulator with full Xcode selected:

```bash
cd flutter
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  /Users/lisi/flutter/bin/flutter build ios --simulator --debug
```

- [ ] Build Android debug APK:

```bash
cd flutter
/Users/lisi/flutter/bin/flutter build apk --debug
```

- [ ] If Android SDK license acceptance is the only blocker, stop at that command and report the exact `flutter doctor --android-licenses` step for the user to perform. Do not accept licenses silently.
- [ ] Report each build result independently. Do not claim emulator/device speech or Android lock-screen behavior without device-level confirmation.

**Checkpoint:** `flutter analyze` and each available platform build have explicit pass/fail output; unsupported Android license acceptance is clearly called out.
