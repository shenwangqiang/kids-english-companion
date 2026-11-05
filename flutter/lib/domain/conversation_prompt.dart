import 'conversation_models.dart';

class ConversationPrompt {
  static List<Map<String, String>> build({
    required ConversationSession session,
    required String childMessage,
  }) {
    final facts = session.memory.rememberedFacts
        .take(20)
        .map((fact) => '- ${fact.substring(0, fact.length.clamp(0, 300))}')
        .join('\n');
    final brandBoundaries = session.topic.branded
        ? '\nThe model does not contain the book or source material. Do not quote long passages or reconstruct chapters. If a detail is uncertain, say so; avoid spoilers.'
        : '';
    final system =
        '''You are a friendly English conversation coach for a child aged ${session.ageBand.wireValue}.
Use age-appropriate English and no more than ${session.ageBand.replyWordLimit} English words in each reply.
Conversation topic: ${session.topic.title}. Stay on this topic unless the child clearly changes it.
Briefly reflect the child's latest answer, then ask exactly one natural next question.
Never ask for a real name, address, school, contact details, photos, location, or secrets.
Do not invent facts about the child. Use saved memory only when relevant.$brandBoundaries

Saved conversation summary:
${session.memory.summary.isEmpty ? 'None yet' : session.memory.summary.substring(0, session.memory.summary.length.clamp(0, 1500))}

Remembered facts:
${facts.isEmpty ? '- None yet' : facts}

Planned direction:
${session.memory.nextDirection.isEmpty ? 'Continue a friendly age-appropriate conversation.' : session.memory.nextDirection.substring(0, session.memory.nextDirection.length.clamp(0, 500))}

Return JSON only, without markdown, with exactly these keys: reply (string), conversationSummary (string), nextDirection (string), rememberedFacts (array of strings).''';
    final prior = session.messages
        .take(session.messages.length > 8 ? 8 : session.messages.length)
        .map(
          (message) => {
            'role': message.role.wireValue,
            'content': message.content.substring(
              0,
              message.content.length.clamp(0, 500),
            ),
          },
        );
    final current = childMessage.trim().substring(
      0,
      childMessage.trim().length.clamp(0, 500),
    );
    return [
      {'role': 'system', 'content': system},
      ...prior,
      {'role': 'user', 'content': current},
    ];
  }
}
