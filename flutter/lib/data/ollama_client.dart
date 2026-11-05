import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

import '../domain/age_band.dart';
import '../domain/child_input_policy.dart';
import '../domain/conversation_models.dart';
import '../domain/conversation_prompt.dart';
import '../domain/story_models.dart';
import 'secure_token_store.dart';
import 'settings_repository.dart';

class OllamaException implements Exception {
  const OllamaException(this.message);
  final String message;
  @override
  String toString() => message;
}

class OllamaAuthenticationException extends OllamaException {
  const OllamaAuthenticationException()
    : super('服务拒绝了身份验证，请检查模型设置中的 Bearer Token。');
}

class OllamaUnavailableException extends OllamaException {
  const OllamaUnavailableException() : super('对话服务当前不可用，请检查网络地址和 Ollama 服务。');
}

class OllamaInvalidResponseException extends OllamaException {
  const OllamaInvalidResponseException() : super('模型返回内容无法识别，请重试。');
}

class OllamaClient {
  OllamaClient({
    required SettingsRepository settings,
    required SecureTokenStore tokens,
    http.Client? client,
  }) : _settings = settings,
       _tokens = tokens,
       _client = client ?? http.Client();

  final SettingsRepository _settings;
  final SecureTokenStore _tokens;
  final http.Client _client;

  Future<List<String>> availableModels() async {
    final settings = await _settings.load();
    final uri = _apiUri(settings.endpoint, '/api/tags');
    final token = await _tokens.read();
    try {
      final response = await _client
          .get(uri, headers: _headers(token))
          .timeout(const Duration(seconds: 12));
      if (response.statusCode == 401 || response.statusCode == 403)
        throw const OllamaAuthenticationException();
      if (response.statusCode < 200 || response.statusCode >= 300)
        throw const OllamaUnavailableException();
      final decoded = jsonDecode(response.body);
      final models = decoded is Map<String, dynamic> ? decoded['models'] : null;
      if (models is! List) throw const OllamaInvalidResponseException();
      return models
          .whereType<Map<String, dynamic>>()
          .map((item) => item['name'])
          .whereType<String>()
          .where((name) => name.isNotEmpty)
          .toList();
    } on OllamaException {
      rethrow;
    } on Object {
      throw const OllamaUnavailableException();
    }
  }

  Future<ConversationOutcome> reply({
    required ConversationSession session,
    required String childMessage,
  }) async {
    if (ChildInputPolicy.isBlocked(childMessage))
      throw const OllamaException(ChildInputPolicy.redirect);
    final messages = ConversationPrompt.build(
      session: session,
      childMessage: childMessage,
    );
    final content = await _send(messages, session.ageBand);
    try {
      return ConversationOutcome.parse(content);
    } on FormatException {
      final repair = [
        ...messages,
        {
          'role': 'assistant',
          'content': content.substring(0, content.length.clamp(0, 2000)),
        },
        {
          'role': 'user',
          'content': 'Return valid JSON only with reply, conversationSummary, nextDirection, and rememberedFacts. Do not use markdown.',
        },
      ];
      try {
        return ConversationOutcome.parse(await _send(repair, session.ageBand));
      } on FormatException {
        throw const OllamaInvalidResponseException();
      }
    }
  }

  Future<void> testConnection() async {
    final settings = await _settings.load();
    final response = await _sendRaw(settings, [
      {'role': 'user', 'content': 'Reply only with OK.'},
    ], 32);
    if (response.trim().isEmpty) throw const OllamaInvalidResponseException();
  }

  Future<String> askStory({
    required Story story,
    required String question,
  }) async {
    if (ChildInputPolicy.isBlocked(question))
      throw const OllamaException(ChildInputPolicy.redirect);
    final queryWords = _storyWords(question);
    final evidence = <({String text, double score})>[];
    for (final paragraph in story.paragraphs) {
      for (final sentence in paragraph.sentences) {
        final sentenceWords = _storyWords(sentence.text);
        final overlap = queryWords.intersection(sentenceWords).length;
        if (overlap > 0)
          evidence.add((
            text: sentence.text,
            score: overlap / queryWords.length.clamp(1, 100),
          ));
      }
    }
    evidence.sort((a, b) => b.score.compareTo(a.score));
    if (evidence.isEmpty || evidence.first.score < 0.15) {
      return "I can't find that detail in this story. Let's look for the answer together.";
    }
    final context = evidence.take(4).map((item) => '- ${item.text}').join('\n');
    final response = await _sendRaw(await _settings.load(), [
      {
        'role': 'system',
        'content':
            'You are an English reading helper for children. Answer using only the story evidence below. If it does not support an answer, say you cannot find it. Use simple, short English and do not invent story details.\nStory: ${story.title}\nEvidence:\n$context',
      },
      {
        'role': 'user',
        'content': question.trim().substring(
          0,
          question.trim().length.clamp(0, 500),
        ),
      },
    ], 120);
    try {
      final decoded = jsonDecode(response) as Map<String, dynamic>;
      final message = decoded['message'] as Map<String, dynamic>;
      final text = (message['content'] as String).trim();
      if (text.isEmpty) throw const OllamaInvalidResponseException();
      return text;
    } on Object {
      throw const OllamaInvalidResponseException();
    }
  }

  Set<String> _storyWords(String text) =>
      RegExp(r"[a-z0-9']+")
          .allMatches(text.toLowerCase())
          .map((match) => match.group(0)!)
          .where((word) => word.length > 2)
          .toSet();

  Future<String> _send(
    List<Map<String, String>> messages,
    AgeBand ageBand,
  ) async {
    final settings = await _settings.load();
    final response = await _sendRaw(
      settings,
      messages,
      ageBand.maxOutputTokens,
    );
    try {
      final decoded = jsonDecode(response);
      if (decoded is! Map<String, dynamic>)
        throw const OllamaInvalidResponseException();
      final message = decoded['message'];
      if (message is! Map<String, dynamic> || message['content'] is! String)
        throw const OllamaInvalidResponseException();
      return message['content'] as String;
    } on FormatException {
      throw const OllamaInvalidResponseException();
    }
  }

  Future<String> _sendRaw(
    CompanionSettings settings,
    List<Map<String, String>> messages,
    int maxTokens,
  ) async {
    final uri = _apiUri(settings.endpoint, '/api/chat');
    final token = await _tokens.read();
    final body = <String, Object?>{
      'model': settings.model,
      'messages': messages,
      'stream': false,
      'think': false,
      'keep_alive': '30m',
      'options': {'temperature': 0.2, 'num_predict': maxTokens},
    };
    try {
      final response = await _client
          .post(uri, headers: _headers(token), body: jsonEncode(body))
          .timeout(const Duration(seconds: 90));
      if (response.statusCode == 401 || response.statusCode == 403)
        throw const OllamaAuthenticationException();
      if (response.statusCode < 200 || response.statusCode >= 300)
        throw const OllamaUnavailableException();
      return response.body;
    } on OllamaException {
      rethrow;
    } on Object {
      throw const OllamaUnavailableException();
    }
  }

  Uri _apiUri(String endpoint, String path) {
    final value = endpoint.trim();
    final uri = Uri.tryParse(value);
    if (uri == null ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty ||
        uri.hasFragment)
      throw const OllamaException('模型服务地址无效。');
    final isLocal = _isLocalHost(uri.host);
    if (uri.scheme.toLowerCase() != 'https' &&
        !(uri.scheme.toLowerCase() == 'http' && isLocal && kDebugMode)) {
      throw const OllamaException('远程模型服务必须使用 HTTPS。');
    }
    var basePath = uri.path.replaceFirst(RegExp(r'/+$'), '');
    if (basePath.endsWith('/v1'))
      basePath = basePath.substring(0, basePath.length - 3);
    return uri.replace(path: '$basePath$path', query: '', fragment: '');
  }

  bool _isLocalHost(String host) {
    final normalized = host.toLowerCase();
    if (normalized == 'localhost' ||
        normalized == '127.0.0.1' ||
        normalized == '::1' ||
        normalized.endsWith('.local') ||
        normalized == '10.0.2.2')
      return true;
    final parts = normalized.split('.').map(int.tryParse).toList();
    if (parts.length != 4 ||
        parts.any((value) => value == null || value < 0 || value > 255))
      return false;
    return parts[0] == 10 ||
        (parts[0] == 172 && parts[1]! >= 16 && parts[1]! <= 31) ||
        (parts[0] == 192 && parts[1] == 168);
  }

  Map<String, String> _headers(String token) => {
    HttpHeaders.contentTypeHeader: 'application/json',
    if (token.isNotEmpty) HttpHeaders.authorizationHeader: 'Bearer $token',
  };
}
