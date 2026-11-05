import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../data/ollama_client.dart';
import '../../data/secure_token_store.dart';
import '../../data/settings_repository.dart';
import '../../domain/age_band.dart';
import '../../platform/tts_service.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    required this.settings,
    required this.tokens,
    required this.ollama,
    required this.tts,
    required this.onAgeBandChanged,
  });
  final SettingsRepository settings;
  final SecureTokenStore tokens;
  final OllamaClient ollama;
  final TtsService tts;
  final ValueChanged<AgeBand> onAgeBandChanged;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final _endpoint = TextEditingController();
  final _model = TextEditingController();
  final _token = TextEditingController();
  List<String> _models = const [];
  CompanionSettings _settings = const CompanionSettings();
  AgeBand _loadedAgeBand = AgeBand.sixToEight;
  bool _loading = true;
  bool _working = false;
  String? _status;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _endpoint.dispose();
    _model.dispose();
    _token.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final settings = await widget.settings.load();
      final token = await widget.tokens.read();
      if (!mounted) return;
      setState(() {
        _settings = settings;
        _loadedAgeBand = settings.ageBand;
        _endpoint.text = settings.endpoint;
        _model.text = settings.model;
        _token.text = token;
        _loading = false;
      });
    } on Object {
      if (mounted)
        setState(() {
          _loading = false;
          _status = '设置暂时无法读取。';
        });
    }
  }

  Future<void> _save() async {
    setState(() {
      _working = true;
      _status = null;
    });
    try {
      final value = _settings.copyWith(
        endpoint: _endpoint.text.trim(),
        model: _model.text.trim(),
      );
      await widget.settings.save(value);
      await widget.tokens.save(_token.text);
      await widget.tts.configure(
        preference: value.ttsPreference,
        rate: value.speechRate,
      );
      if (value.ageBand != _loadedAgeBand) {
        _loadedAgeBand = value.ageBand;
        widget.onAgeBandChanged(value.ageBand);
      }
      if (mounted) setState(() => _status = '设置已保存');
    } on Object {
      if (mounted) setState(() => _status = '设置未能保存，请重试。');
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  Future<void> _refreshModels() async {
    setState(() {
      _working = true;
      _status = null;
    });
    try {
      await widget.settings.save(
        _settings.copyWith(
          endpoint: _endpoint.text.trim(),
          model: _model.text.trim(),
        ),
      );
      await widget.tokens.save(_token.text);
      final models = await widget.ollama.availableModels();
      if (!mounted) return;
      setState(() {
        _models = models;
        if (_model.text.isEmpty && models.isNotEmpty)
          _model.text = models.first;
        _status = models.isEmpty
            ? '服务已连接，但没有可用模型。'
            : '已读取 ${models.length} 个模型';
      });
    } on Object catch (error) {
      if (mounted)
        setState(
          () => _status = error is OllamaException
              ? error.message
              : '连接失败；仍可手动输入模型名称。',
        );
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  Future<void> _test() async {
    setState(() {
      _working = true;
      _status = null;
    });
    try {
      await widget.settings.save(
        _settings.copyWith(
          endpoint: _endpoint.text.trim(),
          model: _model.text.trim(),
        ),
      );
      await widget.tokens.save(_token.text);
      await widget.ollama.testConnection();
      if (mounted) setState(() => _status = '连接成功');
    } on Object catch (error) {
      if (mounted)
        setState(
          () => _status = error is OllamaException
              ? error.message
              : '连接失败，请检查地址和服务状态。',
        );
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        Text('模型连接', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 10),
        TextField(
          controller: _endpoint,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(
            labelText: 'Ollama 服务地址',
            hintText: 'http://192.168.1.10:11434',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        DropdownButtonFormField<String>(
          initialValue: _models.contains(_model.text) ? _model.text : null,
          decoration: const InputDecoration(
            labelText: '已安装模型',
            border: OutlineInputBorder(),
          ),
          items: _models
              .map((name) => DropdownMenuItem(value: name, child: Text(name)))
              .toList(),
          onChanged: (value) {
            if (value != null) setState(() => _model.text = value);
          },
          hint: const Text('刷新后选择，或手动输入'),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _model,
          decoration: const InputDecoration(
            labelText: '模型名称',
            hintText: 'qwen3.5:4b-mlx',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _token,
          obscureText: true,
          autocorrect: false,
          decoration: const InputDecoration(
            labelText: 'Bearer Token（可选）',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          children: [
            OutlinedButton(
              onPressed: _working ? null : _refreshModels,
              child: const Text('刷新模型'),
            ),
            OutlinedButton(
              onPressed: _working ? null : _test,
              child: const Text('测试连接'),
            ),
          ],
        ),
        const Divider(height: 32),
        Text('学习设置', style: Theme.of(context).textTheme.titleLarge),
        DropdownButtonFormField<AgeBand>(
          initialValue: _settings.ageBand,
          decoration: const InputDecoration(labelText: '年龄段'),
          items: AgeBand.values
              .map(
                (age) => DropdownMenuItem(value: age, child: Text(age.label)),
              )
              .toList(),
          onChanged: (value) {
            if (value != null)
              setState(() => _settings = _settings.copyWith(ageBand: value));
          },
        ),
        const SizedBox(height: 12),
        Text('语速：${_settings.speechRate.toStringAsFixed(1)}'),
        Slider(
          value: _settings.speechRate,
          min: 0.6,
          max: 1.2,
          divisions: 6,
          label: _settings.speechRate.toStringAsFixed(1),
          onChanged: (value) =>
              setState(() => _settings = _settings.copyWith(speechRate: value)),
        ),
        DropdownButtonFormField<String>(
          initialValue: _settings.ttsPreference,
          decoration: const InputDecoration(labelText: '英语语音'),
          items: const [
            DropdownMenuItem(value: 'auto', child: Text('自动')),
            DropdownMenuItem(value: 'google', child: Text('Google')),
            DropdownMenuItem(value: 'iflytek', child: Text('讯飞')),
          ],
          onChanged: (value) {
            if (value != null)
              setState(
                () => _settings = _settings.copyWith(ttsPreference: value),
              );
          },
        ),
        if (defaultTargetPlatform == TargetPlatform.iOS)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(
              widget.tts.platformNote.isEmpty
                  ? 'iPhone 使用系统英语语音，不支持选择 Google 或讯飞引擎。'
                  : widget.tts.platformNote,
            ),
          ),
        if (_status case final status?)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text(status),
          ),
        FilledButton(
          onPressed: _working ? null : _save,
          child: _working
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('保存设置'),
        ),
      ],
    );
  }
}
