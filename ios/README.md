# Kids English Companion — iOS

Native SwiftUI client for iPhone and iPad. Open [`KidsEnglishCompanion.xcodeproj`](KidsEnglishCompanion.xcodeproj) in Xcode 16 or newer and run the `KidsEnglishCompanion` scheme on iOS 17 or newer.

## Included

- Age groups 6–8, 9–10, and 11–12 with separate local chat histories
- Ollama `/api/chat` conversations, `/api/tags` model discovery, summaries, remembered facts, and next-topic direction
- Keychain storage for an optional Bearer token; local JSON storage for chat sessions, excluded from device backups
- 18 original graded stories shared with Android, offline reading, sentence replay, story-grounded questions, vocabulary, and completion marks
- Explicitly started speech conversation using Apple Speech recognition and the built-in English system voice
- Child-input safety checks before sending or storing chat text

## Connect to Ollama

In Settings, configure the Ollama base address and model. The iOS Simulator can usually reach an Ollama server running on the Mac through `http://127.0.0.1:11434`. On a physical iPhone, use a reachable Mac hostname ending in `.local`, or an HTTPS endpoint. Set the optional Bearer token only if the server requires it.

The app restricts plain HTTP to localhost/private-network hosts and ATS permits local networking. HTTPS is required for remote endpoints. No API key is included in the source tree.

## Voice and privacy

Microphone and speech-recognition permissions are requested only when the user starts voice conversation. The app configures an audio session and declares the audio background mode so an active voice exchange can continue when iOS allows it; the system may still pause recognition after locking the device or reclaiming audio resources. There is no wake word or silent background microphone startup.

Conversation text is stored on device and sent to the configured Ollama server for replies. Speech is processed by Apple’s speech-recognition service; whether recognition stays entirely on device depends on the installed iOS language resources and system settings. iOS currently uses the system English voice; Google and iFlytek TTS providers are not integrated.
