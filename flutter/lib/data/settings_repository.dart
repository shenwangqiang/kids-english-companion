import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/age_band.dart';

class CompanionSettings {
  const CompanionSettings({
    this.ageBand = AgeBand.sixToEight,
    this.endpoint = 'http://127.0.0.1:11434',
    this.model = 'qwen3.5:4b-mlx',
    this.speechRate = 0.9,
    this.ttsPreference = 'auto',
    this.activeSessionId,
  });

  final AgeBand ageBand;
  final String endpoint;
  final String model;
  final double speechRate;
  final String ttsPreference;
  final String? activeSessionId;

  CompanionSettings copyWith({
    AgeBand? ageBand,
    String? endpoint,
    String? model,
    double? speechRate,
    String? ttsPreference,
    String? activeSessionId,
  }) => CompanionSettings(
    ageBand: ageBand ?? this.ageBand,
    endpoint: endpoint ?? this.endpoint,
    model: model ?? this.model,
    speechRate: speechRate ?? this.speechRate,
    ttsPreference: ttsPreference ?? this.ttsPreference,
    activeSessionId: activeSessionId ?? this.activeSessionId,
  );
}

class SettingsRepository {
  static const _ageBandKey = 'age_band';
  static const _endpointKey = 'ollama_endpoint';
  static const _modelKey = 'ollama_model';
  static const _speechRateKey = 'speech_rate';
  static const _ttsPreferenceKey = 'tts_preference';
  static const _activeSessionPrefix = 'active_session_';

  Future<CompanionSettings> load() async {
    final prefs = await SharedPreferences.getInstance();
    final age = AgeBand.values.firstWhere(
      (item) => item.wireValue == prefs.getString(_ageBandKey),
      orElse: () => AgeBand.sixToEight,
    );
    return CompanionSettings(
      ageBand: age,
      endpoint:
          prefs.getString(_endpointKey) ??
          (defaultTargetPlatform == TargetPlatform.android
              ? 'http://10.0.2.2:11434'
              : 'http://127.0.0.1:11434'),
      model: prefs.getString(_modelKey) ?? 'qwen3.5:4b-mlx',
      speechRate: (prefs.getDouble(_speechRateKey) ?? 0.9).clamp(0.6, 1.2),
      ttsPreference: prefs.getString(_ttsPreferenceKey) ?? 'auto',
      activeSessionId: prefs.getString('$_activeSessionPrefix${age.wireValue}'),
    );
  }

  Future<void> save(CompanionSettings value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_ageBandKey, value.ageBand.wireValue);
    await prefs.setString(_endpointKey, value.endpoint.trim());
    await prefs.setString(_modelKey, value.model.trim());
    await prefs.setDouble(_speechRateKey, value.speechRate.clamp(0.6, 1.2));
    await prefs.setString(_ttsPreferenceKey, value.ttsPreference);
    if (value.activeSessionId != null) {
      await prefs.setString(
        '$_activeSessionPrefix${value.ageBand.wireValue}',
        value.activeSessionId!,
      );
    }
  }

  Future<void> setActiveSession(AgeBand ageBand, String? sessionId) async {
    final prefs = await SharedPreferences.getInstance();
    final key = '$_activeSessionPrefix${ageBand.wireValue}';
    if (sessionId == null) {
      await prefs.remove(key);
    } else {
      await prefs.setString(key, sessionId);
    }
  }
}
