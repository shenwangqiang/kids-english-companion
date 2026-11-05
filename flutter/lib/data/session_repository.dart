import 'dart:convert';
import 'dart:math';

import 'package:sqflite/sqflite.dart';

import '../domain/age_band.dart';
import '../domain/conversation_models.dart';
import 'local_database.dart';

class SessionRepository {
  SessionRepository(this._localDatabase);

  final LocalDatabase _localDatabase;

  Future<ConversationSession> create(
    AgeBand ageBand,
    ConversationTopic topic,
  ) async {
    final db = await _localDatabase.database;
    final now = DateTime.now();
    final session = ConversationSession(
      id: _newId(),
      ageBand: ageBand,
      topic: topic,
      title: topic.title,
      memory: const ConversationMemory(),
      createdAt: now,
      updatedAt: now,
    );
    await db.insert('sessions', _sessionValues(session));
    return session;
  }

  Future<ConversationSession?> get(String id) async {
    final db = await _localDatabase.database;
    final rows = await db.query(
      'sessions',
      where: 'id = ?',
      whereArgs: [id],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return _fromRow(rows.first, await _messages(db, id));
  }

  Future<List<ConversationSession>> listForAge(AgeBand ageBand) async {
    final db = await _localDatabase.database;
    final rows = await db.query(
      'sessions',
      where: 'age_band = ?',
      whereArgs: [ageBand.wireValue],
      orderBy: 'updated_at DESC',
    );
    final sessions = <ConversationSession>[];
    for (final row in rows) {
      sessions.add(_fromRow(row, await _messages(db, row['id']! as String)));
    }
    return sessions;
  }

  Future<void> appendSuccessfulTurn(
    String sessionId,
    String userText,
    ConversationOutcome outcome,
  ) async {
    final db = await _localDatabase.database;
    final now = DateTime.now();
    await db.transaction((txn) async {
      final current = await txn.query(
        'sessions',
        where: 'id = ?',
        whereArgs: [sessionId],
        limit: 1,
      );
      if (current.isEmpty)
        throw StateError('Conversation session no longer exists.');
      await txn.insert('messages', {
        'id': _newId(),
        'session_id': sessionId,
        'role': ConversationRole.user.wireValue,
        'content': userText,
        'created_at': now.millisecondsSinceEpoch,
      });
      await txn.insert('messages', {
        'id': _newId(),
        'session_id': sessionId,
        'role': ConversationRole.assistant.wireValue,
        'content': outcome.reply,
        'created_at': now.millisecondsSinceEpoch + 1,
      });
      await txn.update(
        'sessions',
        {
          'summary': outcome.conversationSummary,
          'next_direction': outcome.nextDirection,
          'remembered_facts_json': jsonEncode(outcome.rememberedFacts),
          'updated_at': now.millisecondsSinceEpoch + 1,
        },
        where: 'id = ?',
        whereArgs: [sessionId],
      );
    });
  }

  Future<void> delete(String id) async {
    final db = await _localDatabase.database;
    await db.delete('sessions', where: 'id = ?', whereArgs: [id]);
  }

  Future<List<ChatMessage>> _messages(
    DatabaseExecutor db,
    String sessionId,
  ) async {
    final rows = await db.query(
      'messages',
      where: 'session_id = ?',
      whereArgs: [sessionId],
      orderBy: 'created_at ASC',
    );
    return rows
        .map(
          (row) => ChatMessage(
            id: row['id']! as String,
            role: ConversationRole.fromWireValue(row['role']! as String),
            content: row['content']! as String,
            createdAt: DateTime.fromMillisecondsSinceEpoch(
              row['created_at']! as int,
            ),
          ),
        )
        .toList(growable: false);
  }

  Map<String, Object?> _sessionValues(ConversationSession session) => {
    'id': session.id,
    'age_band': session.ageBand.wireValue,
    'topic_json': jsonEncode(session.topic.toJson()),
    'title': session.title,
    'summary': session.memory.summary,
    'next_direction': session.memory.nextDirection,
    'remembered_facts_json': jsonEncode(session.memory.rememberedFacts),
    'created_at': session.createdAt.millisecondsSinceEpoch,
    'updated_at': session.updatedAt.millisecondsSinceEpoch,
  };

  ConversationSession _fromRow(
    Map<String, Object?> row,
    List<ChatMessage> messages,
  ) {
    final topicJson =
        jsonDecode(row['topic_json']! as String) as Map<String, Object?>;
    final facts =
        jsonDecode(row['remembered_facts_json']! as String) as List<dynamic>;
    return ConversationSession(
      id: row['id']! as String,
      ageBand: AgeBand.fromWireValue(row['age_band']! as String),
      topic: ConversationTopic.fromJson(topicJson),
      title: row['title']! as String,
      memory: ConversationMemory(
        summary: row['summary']! as String,
        nextDirection: row['next_direction']! as String,
        rememberedFacts: facts.cast<String>(),
      ),
      createdAt: DateTime.fromMillisecondsSinceEpoch(row['created_at']! as int),
      updatedAt: DateTime.fromMillisecondsSinceEpoch(row['updated_at']! as int),
      messages: messages,
    );
  }

  String _newId() =>
      '${DateTime.now().microsecondsSinceEpoch}-${Random.secure().nextInt(1 << 32).toRadixString(16)}';
}
