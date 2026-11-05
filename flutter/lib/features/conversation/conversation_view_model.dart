import 'package:flutter/foundation.dart';

import '../../data/ollama_client.dart';
import '../../data/session_repository.dart';
import '../../data/settings_repository.dart';
import '../../domain/age_band.dart';
import '../../domain/child_input_policy.dart';
import '../../domain/conversation_models.dart';
import '../../domain/story_models.dart';

enum AppRoute {
  welcome,
  conversation,
  topics,
  history,
  library,
  reader,
  practice,
  progress,
  settings,
}

class ConversationViewModel extends ChangeNotifier {
  ConversationViewModel({
    required SessionRepository sessions,
    required SettingsRepository settings,
    required OllamaClient ollama,
  }) : _sessions = sessions,
       _settings = settings,
       _ollama = ollama;

  final SessionRepository _sessions;
  final SettingsRepository _settings;
  final OllamaClient _ollama;
  final Set<String> _activeRequestIds = {};

  AppRoute route = AppRoute.welcome;
  AgeBand ageBand = AgeBand.sixToEight;
  ConversationSession? activeSession;
  List<ConversationSession> history = const [];
  Story? selectedStory;
  bool initialized = false;
  bool loading = false;
  String draft = '';
  String? errorMessage;

  Future<void> initialize() async {
    try {
      final prefs = await _settings.load();
      ageBand = prefs.ageBand;
      if (prefs.activeSessionId != null)
        activeSession = await _sessions.get(prefs.activeSessionId!);
      final all = await _sessions.listForAge(ageBand);
      history = all;
      activeSession ??= all.isNotEmpty ? all.first : null;
      if (activeSession == null && prefs.activeSessionId != null) {
        activeSession = await _sessions.create(
          ageBand,
          ConversationTopicCatalog.general,
        );
        await _settings.setActiveSession(ageBand, activeSession!.id);
      }
      route = prefs.activeSessionId == null
          ? AppRoute.welcome
          : AppRoute.conversation;
    } catch (_) {
      errorMessage = '本地数据暂时无法读取，请重新打开应用。';
    } finally {
      initialized = true;
      notifyListeners();
    }
  }

  Future<void> chooseAgeBand(AgeBand value) async {
    ageBand = value;
    final prefs = await _settings.load();
    await _settings.save(prefs.copyWith(ageBand: value));
    final sessions = await _sessions.listForAge(value);
    history = sessions;
    activeSession = sessions.isNotEmpty
        ? sessions.first
        : await _sessions.create(value, ConversationTopicCatalog.general);
    await _settings.setActiveSession(value, activeSession!.id);
    route = AppRoute.conversation;
    notifyListeners();
  }

  Future<void> createConversation(ConversationTopic topic) async {
    activeSession = await _sessions.create(ageBand, topic);
    await _settings.setActiveSession(ageBand, activeSession!.id);
    draft = '';
    errorMessage = null;
    await refreshHistory();
    route = AppRoute.conversation;
    notifyListeners();
  }

  Future<void> openConversation(String id) async {
    activeSession = await _sessions.get(id);
    if (activeSession == null) return;
    ageBand = activeSession!.ageBand;
    await _settings.setActiveSession(ageBand, id);
    errorMessage = null;
    route = AppRoute.conversation;
    notifyListeners();
  }

  Future<void> deleteConversation(String id) async {
    await _sessions.delete(id);
    if (activeSession?.id == id) activeSession = null;
    await refreshHistory();
    if (activeSession == null && history.isNotEmpty)
      activeSession = await _sessions.get(history.first.id);
    if (activeSession == null) {
      activeSession = await _sessions.create(
        ageBand,
        ConversationTopicCatalog.general,
      );
    }
    await _settings.setActiveSession(ageBand, activeSession!.id);
    notifyListeners();
  }

  Future<void> refreshHistory() async {
    history = await _sessions.listForAge(ageBand);
    notifyListeners();
  }

  void setDraft(String value) {
    draft = value;
    notifyListeners();
  }

  void navigate(AppRoute value) {
    route = value;
    errorMessage = null;
    notifyListeners();
  }

  void openStory(Story story) {
    selectedStory = story;
    navigate(AppRoute.reader);
  }

  void openPractice() {
    if (selectedStory != null) navigate(AppRoute.practice);
  }

  Future<void> send([String? overrideText]) async {
    final session = activeSession;
    final text = (overrideText ?? draft).trim();
    if (session == null ||
        text.isEmpty ||
        _activeRequestIds.contains(session.id))
      return;
    if (ChildInputPolicy.isBlocked(text)) {
      errorMessage = ChildInputPolicy.redirect;
      notifyListeners();
      return;
    }
    _activeRequestIds.add(session.id);
    loading = true;
    errorMessage = null;
    notifyListeners();
    try {
      final outcome = await _ollama.reply(session: session, childMessage: text);
      await _sessions.appendSuccessfulTurn(session.id, text, outcome);
      if (activeSession?.id == session.id) {
        activeSession = await _sessions.get(session.id);
        if (draft.trim() == text) draft = '';
      }
      await refreshHistory();
    } on Object catch (error) {
      errorMessage = error is OllamaException
          ? error.message
          : '发送失败，请检查连接后重试。';
    } finally {
      _activeRequestIds.remove(session.id);
      loading = _activeRequestIds.isNotEmpty;
      notifyListeners();
    }
  }
}
