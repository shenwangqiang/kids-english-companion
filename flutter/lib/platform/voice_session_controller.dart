import 'package:flutter/widgets.dart';

import '../data/ollama_client.dart';
import '../data/settings_repository.dart';
import '../features/conversation/conversation_view_model.dart';
import 'speech_service.dart';
import 'tts_service.dart';

enum VoicePhase { idle, listening, thinking, speaking, paused }

class VoiceSessionController extends ChangeNotifier
    with WidgetsBindingObserver {
  VoiceSessionController({
    required ConversationViewModel conversation,
    required SpeechService speech,
    required TtsService tts,
    required SettingsRepository settings,
  }) : _conversation = conversation,
       _speech = speech,
       _tts = tts,
       _settings = settings;

  final ConversationViewModel _conversation;
  final SpeechService _speech;
  final TtsService _tts;
  final SettingsRepository _settings;
  bool _active = false;
  int _generation = 0;
  VoicePhase phase = VoicePhase.idle;
  String? errorMessage;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed && _active) stop();
  }

  Future<void> start() async {
    if (_active) return;
    _active = true;
    final generation = ++_generation;
    WidgetsBinding.instance.addObserver(this);
    errorMessage = null;
    notifyListeners();
    await _runTurns(generation);
  }

  Future<void> stop() async {
    _active = false;
    _generation++;
    WidgetsBinding.instance.removeObserver(this);
    phase = VoicePhase.idle;
    await _speech.stop();
    await _tts.stop();
    notifyListeners();
  }

  Future<void> _runTurns(int generation) async {
    while (_active && generation == _generation) {
      try {
        phase = VoicePhase.listening;
        notifyListeners();
        final words = await _speech.listenForOneEnglishUtterance();
        if (!_active || generation != _generation) break;
        if (words.isEmpty) {
          phase = VoicePhase.paused;
          errorMessage = '没有听清楚，请点麦克风重新开始。';
          _active = false;
          notifyListeners();
          break;
        }
        _conversation.setDraft(words);
        phase = VoicePhase.thinking;
        errorMessage = null;
        notifyListeners();
        await _conversation.send(words);
        if (!_active || generation != _generation) break;
        if (_conversation.errorMessage case final error?) {
          errorMessage = error;
          phase = VoicePhase.paused;
          _active = false;
          notifyListeners();
          break;
        }
        final messages = _conversation.activeSession?.messages ?? const [];
        if (messages.isEmpty) break;
        final reply = messages.last.content;
        phase = VoicePhase.speaking;
        notifyListeners();
        final settings = await _settings.load();
        await _tts.speak(
          reply,
          preference: settings.ttsPreference,
          rate: settings.speechRate,
        );
      } on Object catch (error) {
        errorMessage =
            error is SpeechServiceException || error is OllamaException
            ? error.toString()
            : '语音对话暂时不可用，请重试。';
        phase = VoicePhase.paused;
        _active = false;
        notifyListeners();
      }
    }
    if (generation == _generation) {
      if (!_active && phase != VoicePhase.paused) phase = VoicePhase.idle;
      WidgetsBinding.instance.removeObserver(this);
    }
    notifyListeners();
  }
}
