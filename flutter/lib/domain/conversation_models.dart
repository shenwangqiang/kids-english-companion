import 'dart:convert';

import 'age_band.dart';

enum ConversationRole {
  user('user'),
  assistant('assistant');

  const ConversationRole(this.wireValue);
  final String wireValue;

  static ConversationRole fromWireValue(String value) =>
      ConversationRole.values.firstWhere(
        (role) => role.wireValue == value,
        orElse: () =>
            throw FormatException('Unknown conversation role: $value'),
      );
}

class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.role,
    required this.content,
    required this.createdAt,
  });

  final String id;
  final ConversationRole role;
  final String content;
  final DateTime createdAt;
}

class ConversationMemory {
  const ConversationMemory({
    this.summary = '',
    this.rememberedFacts = const [],
    this.nextDirection = '',
  });

  final String summary;
  final List<String> rememberedFacts;
  final String nextDirection;
}

class ConversationSession {
  const ConversationSession({
    required this.id,
    required this.ageBand,
    required this.topic,
    required this.title,
    required this.memory,
    required this.createdAt,
    required this.updatedAt,
    this.messages = const [],
  });

  final String id;
  final AgeBand ageBand;
  final ConversationTopic topic;
  final String title;
  final ConversationMemory memory;
  final DateTime createdAt;
  final DateTime updatedAt;
  final List<ChatMessage> messages;
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

  factory ConversationOutcome.parse(String content) {
    final decoded = jsonDecode(content);
    if (decoded is! Map<String, dynamic>)
      throw const FormatException('Expected a JSON object.');
    String field(String key, int limit) {
      final value = decoded[key];
      if (value is! String) throw FormatException('Missing string field: $key');
      return String.fromCharCodes(value.trim().runes.take(limit));
    }

    final reply = field('reply', 2000);
    if (reply.isEmpty) throw const FormatException('Reply cannot be empty.');
    final factsValue = decoded['rememberedFacts'];
    if (factsValue is! List || factsValue.any((item) => item is! String)) {
      throw const FormatException('rememberedFacts must be a string array.');
    }
    final facts = factsValue
        .cast<String>()
        .map((fact) => fact.trim())
        .where((fact) => fact.isNotEmpty)
        .toSet()
        .take(20)
        .toList();
    return ConversationOutcome(
      reply: reply,
      conversationSummary: field('conversationSummary', 1500),
      nextDirection: field('nextDirection', 500),
      rememberedFacts: facts,
    );
  }
}
