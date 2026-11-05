import 'dart:async';

import 'package:speech_to_text/speech_to_text.dart';

class SpeechService {
  final SpeechToText _speech = SpeechToText();
  bool _initialized = false;
  bool _cancelled = false;
  Completer<String>? _activeResult;

  Future<String> listenForOneEnglishUtterance() async {
    _cancelled = false;
    final result = Completer<String>();
    _activeResult = result;
    try {
      final ready =
          _initialized ||
          await _speech.initialize(
            debugLogging: false,
            onError: (error) {
              final active = _activeResult;
              if (active != null && !active.isCompleted) {
                active.completeError(SpeechServiceException(error.errorMsg));
              }
            },
          );
      if (!ready) {
        throw const SpeechServiceException('此设备暂时无法使用语音识别，请检查系统语音服务和麦克风权限。');
      }
      _initialized = true;
      var lastWords = '';
      await _speech.listen(
        onResult: (value) {
          lastWords = value.recognizedWords.trim();
          if (value.finalResult && !result.isCompleted)
            result.complete(lastWords);
        },
        listenOptions: SpeechListenOptions(
          localeId: 'en_US',
          listenFor: const Duration(seconds: 10),
          pauseFor: const Duration(seconds: 2),
          partialResults: true,
          listenMode: ListenMode.dictation,
        ),
      );
      return await result.future
          .timeout(
            const Duration(seconds: 13),
            onTimeout: () {
              if (_cancelled) return '';
              return lastWords;
            },
          )
          .whenComplete(() async {
            if (_speech.isListening) await _speech.stop();
          });
    } finally {
      if (identical(_activeResult, result)) _activeResult = null;
    }
  }

  Future<void> stop() async {
    _cancelled = true;
    if (_speech.isListening) await _speech.cancel();
  }
}

class SpeechServiceException implements Exception {
  const SpeechServiceException(this.message);
  final String message;
  @override
  String toString() => message;
}
