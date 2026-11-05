import 'dart:io';

import 'package:flutter_tts/flutter_tts.dart';

class TtsService {
  final FlutterTts _tts = FlutterTts();
  bool _configured = false;
  String _platformNote = '';

  String get platformNote => _platformNote;

  Future<void> configure({
    required String preference,
    required double rate,
  }) async {
    await _tts.awaitSpeakCompletion(true);
    await _tts.setSpeechRate(rate.clamp(0.6, 1.2).toDouble());
    _platformNote = Platform.isIOS
        ? 'iPhone 使用系统英语语音；Google 和讯飞引擎选择仅适用于 Android。'
        : '';
    if (!Platform.isAndroid) {
      await _tts.setLanguage('en-US');
      _configured = true;
      return;
    }
    final engines = ((await _tts.getEngines) as List<dynamic>? ?? const [])
        .whereType<String>()
        .toList();
    final google = _matching(engines, (name) => name.contains('google'));
    final iflytek = _matching(
      engines,
      (name) => name.contains('iflytek') || name.contains('讯飞'),
    );
    final candidates = switch (preference) {
      'google' => [google, null],
      'iflytek' => [iflytek, null],
      _ => [google, iflytek, null],
    };
    for (final engine in candidates) {
      try {
        if (engine != null) await _tts.setEngine(engine);
        final available = await _tts.isLanguageAvailable('en-US');
        if (available == true || available == 1) {
          await _tts.setLanguage('en-US');
          _configured = true;
          return;
        }
      } on Object {
        continue;
      }
    }
    _platformNote = '没有找到可用的英语语音，将尝试使用系统默认语音。';
    try {
      await _tts.setLanguage('en-US');
      _configured = true;
    } on Object {
      _platformNote = '设备未安装英语语音数据；文字阅读仍可使用。';
    }
  }

  Future<void> speak(
    String text, {
    String preference = 'auto',
    double rate = 0.9,
  }) async {
    if (!_configured) await configure(preference: preference, rate: rate);
    await _tts.stop();
    await _tts.speak(text);
  }

  Future<void> stop() => _tts.stop();

  String? _matching(List<String> engines, bool Function(String) match) {
    for (final engine in engines) {
      if (match(engine.toLowerCase())) return engine;
    }
    return null;
  }
}
