# Kids English Conversation Companion V2 Design

## Goal

Rebuild the application around an ongoing English conversation for children aged 6–12. The assistant must remember recent context, maintain a compact long-term summary, reflect the child's latest answer, and deliberately choose the next useful conversation direction. Reading stories remain available as an optional secondary activity.

## Verified Model Baseline

- Default local model: `qwen3.5:9b` through Ollama.
- The model is open-weight under Apache 2.0 and has no per-request fee when self-hosted.
- The Android client must use Ollama's native `POST /api/chat` contract with `stream: false` and `think: false`.
- The OpenAI-compatible `/v1/chat/completions` route is not accepted for the default integration because local verification showed that thinking tokens can exhaust the output budget while `message.content` remains empty.
- A connection test succeeds only when a real chat request returns a non-empty assistant message. Listing models or receiving HTTP 200 is insufficient.

## Product Flow

1. The child chooses an age band: 6–8, 9–10, or 11–12.
2. The app opens the conversation screen, not the story library.
3. The child can type or start a voice conversation.
4. Each successful turn shows and speaks a short assistant reply.
5. The assistant briefly acknowledges the child's answer and asks one natural next question.
6. The app stores the turn, rolling summary, remembered facts, and planned next direction locally.
7. A compact "What I remember" panel lets the child or supervising adult inspect the summary.
8. Stories, reading practice, progress, and settings remain reachable from the conversation screen.

## Conversation Contract

Every model call receives:

- Age-band-specific language and reply limits.
- A child-safety policy that prohibits collecting personal contact, location, school, photo, and secret information.
- The durable conversation summary.
- Approved remembered facts.
- The previous planned direction.
- At most the 12 most recent turns.
- The child's new utterance.

The model is instructed to return JSON only:

```json
{
  "reply": "That sounds exciting! What happened next?",
  "conversationSummary": "The child enjoys ocean animals and building sandcastles with a sibling.",
  "nextDirection": "Invite the child to describe a beach memory using past-tense verbs.",
  "rememberedFacts": [
    "The child likes dolphins because they are smart.",
    "The child builds sandcastles with a sibling."
  ]
}
```

The app validates this structure, requires a non-empty `reply`, limits all fields, and retries once with a repair instruction if parsing fails. A second malformed response becomes a visible error and is not persisted. The app never displays hidden reasoning.

## Context and Memory

Room stores one active conversation per age band and its ordered turns.

`ConversationSessionEntity` contains:

- Stable session ID and age band.
- Compact summary, next direction, and JSON-encoded remembered facts.
- Creation and update timestamps.

`ConversationTurnEntity` contains:

- Auto-generated ID, session ID, role, content, and timestamp.

The prompt uses the persisted summary plus the latest 12 turns. The complete local turn history can remain in Room, while prompt size remains bounded. Facts are limited to 20 entries, summary to 1,500 characters, next direction to 500 characters, and individual messages to 500 characters.

## Speech Loop

Voice conversation is user-initiated:

```text
Start conversation
  -> listen for one child utterance
  -> send recognized text
  -> receive and persist structured result
  -> speak reply with Android TTS
  -> after TTS completion, listen again
  -> repeat until Stop
```

Text input remains available when speech recognition is missing or inaccurate. Recognition errors pause the loop and show a retry action; they do not discard prior context.

## Lock-Screen Behavior

- Starting voice conversation while the activity is visible starts a microphone foreground service.
- The foreground notification clearly says that the microphone conversation is active and offers Open and Stop actions.
- While a voice session is active, the conversation activity uses `setShowWhenLocked(true)` so it remains usable over the lock screen.
- Stop ends recognition, speech, and the foreground service and removes lock-screen visibility.
- The app never starts microphone capture silently from the background or after reboot.

This design supports an ongoing session across screen lock. It does not promise an always-listening wake word or unrestricted background microphone access.

## UI State

The conversation screen exposes explicit states:

- `IDLE`: ready for text or Start conversation.
- `LISTENING`: microphone is collecting one utterance.
- `THINKING`: model request in progress.
- `SPEAKING`: TTS is reading the reply.
- `PAUSED`: recoverable recognition or model failure.

Only one send operation may run at a time. Stop is available in every active voice state.

## Privacy and Safety

- Conversations, summaries, and remembered facts stay in the local Room database.
- Requests are sent only to the configured model endpoint.
- Child speech may be processed by the device's configured Android recognition provider; the UI keeps the existing disclosure that this provider may use the network.
- The assistant must not ask for a real name, address, school, contact details, photos, or secrets.
- Deterministic input policy checks remain in front of the model.
- Settings allow clearing the saved conversation for the selected age band.

## Error Handling

- Endpoint unavailable: retain the child's draft and previous history; show Retry.
- Authentication failure: show a settings action.
- Empty or malformed response: repair once, then fail visibly without persisting the failed assistant turn.
- TTS unavailable: display the reply and continue in manual push-to-talk mode.
- Speech recognition unavailable: preserve full text conversation functionality.
- Process recreation: reload the session summary and turns from Room; do not automatically restart the microphone.

## Acceptance Criteria

1. A real connection test rejects an HTTP 200 response with empty `message.content`.
2. `qwen3.5:9b` requests use `/api/chat`, `think: false`, and `stream: false`.
3. A second question can refer to the first answer through persisted history.
4. A successful response updates reply, summary, facts, and next direction atomically from the UI's perspective.
5. Restarting the app restores the conversation and "What I remember" summary.
6. Voice mode automatically resumes listening after TTS completes until Stop is pressed.
7. Locking the device during an active voice session keeps the visible conversation and foreground notification available.
8. Stories and existing reading/practice capabilities still work.
9. Unit, Room instrumentation, Compose UI, lint, and debug build verification pass.
