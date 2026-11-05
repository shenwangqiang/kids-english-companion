# Flutter client

Kids English Companion (`英语伙伴`) Flutter client for Android and iOS. It uses Flutter 3.47.5 stable and Dart 3.13.4 from `/Users/lisi/flutter`. Its local SQLite database is separate from both native clients.

## Run

From the repository root:

```bash
/Users/lisi/flutter/bin/flutter pub get
/Users/lisi/flutter/bin/flutter run -d <device>
```

For iOS commands, use the installed Xcode toolchain:

```bash
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer /Users/lisi/flutter/bin/flutter run -d <ios-device>
```

## Endpoint setup

Configure the Ollama-compatible endpoint in **模型设置**. For a Mac Ollama service, use `http://10.0.2.2:11434` from an Android emulator, `http://127.0.0.1:11434` from the iOS simulator, or `http://<Mac-LAN-IP>:11434` from a physical phone on the same network. Remote endpoints must use HTTPS. Local HTTP is permitted only in Android debug builds; iOS local networking is enabled for development. Keep credentials out of source and use the optional Bearer token field only with a trusted endpoint; the token is stored in secure platform storage.

The app starts with age selection, then offers topic chat, recent history, offline graded stories, story-grounded questions, reading practice and progress. Stories and learning records stay on-device. Android TTS can select 自动 / Google / 讯飞 when the engine and English data are installed. iOS uses its system English voice. Speech recognition runs one short utterance per user-started turn; Android lock-screen continuation is not implemented in this Flutter milestone.

The Android application ID and iOS bundle ID are under `com.shenwangqiang`. Android targets minSdk 26; iOS targets 17.0.
