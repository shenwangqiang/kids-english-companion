import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/settings_repository.dart';
import '../../platform/tts_service.dart';
import '../../platform/voice_session_controller.dart';
import 'conversation_view_model.dart';
import 'message_bubble.dart';

class ConversationPage extends StatefulWidget {
  const ConversationPage({super.key});

  @override
  State<ConversationPage> createState() => _ConversationPageState();
}

class _ConversationPageState extends State<ConversationPage> {
  final _controller = TextEditingController();
  final _scrollController = ScrollController();
  String _lastDraft = '';

  @override
  void dispose() {
    _controller.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _send(ConversationViewModel model) async {
    final submitted = _controller.text;
    await model.send(submitted);
    if (!mounted) return;
    if (model.draft.isEmpty && submitted.trim().isNotEmpty) _controller.clear();
  }

  @override
  Widget build(BuildContext context) => Consumer<ConversationViewModel>(
    builder: (context, model, _) {
      if (model.draft != _lastDraft &&
          model.draft.isEmpty &&
          _controller.text.isNotEmpty)
        _controller.clear();
      _lastDraft = model.draft;
      final messages = model.activeSession?.messages ?? const [];
      final voice = context.watch<VoiceSessionController>();
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.hasClients)
          _scrollController.animateTo(
            _scrollController.position.maxScrollExtent,
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOut,
          );
      });
      return Column(
        children: [
          if (model.activeSession != null)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      model.activeSession!.topic.title,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                  ),
                  Text(
                    model.ageBand.label,
                    style: Theme.of(context).textTheme.labelLarge,
                  ),
                ],
              ),
            ),
          Expanded(
            child: messages.isEmpty
                ? Center(
                    child: Padding(
                      padding: const EdgeInsets.all(32),
                      child: Text(
                        '今天想聊些什么？可以选择一个主题，也可以直接开始。',
                        style: Theme.of(context).textTheme.titleMedium,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  )
                : ListView.separated(
                    controller: _scrollController,
                    padding: const EdgeInsets.all(16),
                    itemCount: messages.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 4),
                    itemBuilder: (context, index) => MessageBubble(
                      message: messages[index],
                      onReplay: messages[index].role.name == 'assistant'
                          ? () async {
                              final settings = await context
                                  .read<SettingsRepository>()
                                  .load();
                              await context.read<TtsService>().speak(
                                messages[index].content,
                                preference: settings.ttsPreference,
                                rate: settings.speechRate,
                              );
                            }
                          : null,
                    ),
                  ),
          ),
          if (voice.phase != VoicePhase.idle || voice.errorMessage != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Row(
                children: [
                  Icon(
                    voice.phase == VoicePhase.listening
                        ? Icons.mic
                        : Icons.graphic_eq,
                    size: 18,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      voice.errorMessage ??
                          switch (voice.phase) {
                            VoicePhase.listening => '正在听你说英语…',
                            VoicePhase.thinking => '正在思考…',
                            VoicePhase.speaking => '正在回答…',
                            VoicePhase.paused => '语音对话已暂停',
                            VoicePhase.idle => '',
                          },
                    ),
                  ),
                  if (voice.phase != VoicePhase.idle)
                    TextButton(onPressed: voice.stop, child: const Text('停止')),
                ],
              ),
            ),
          if (model.errorMessage case final error?)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              child: Text(
                error,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      minLines: 1,
                      maxLines: 4,
                      maxLength: 500,
                      readOnly: voice.phase != VoicePhase.idle,
                      onChanged: model.setDraft,
                      onSubmitted:
                          model.loading || voice.phase != VoicePhase.idle
                          ? null
                          : (_) => _send(model),
                      decoration: const InputDecoration(
                        hintText: '用英语或中文输入…',
                        counterText: '',
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    tooltip: '发送',
                    onPressed: model.loading || voice.phase != VoicePhase.idle
                        ? null
                        : () => _send(model),
                    icon: model.loading
                        ? const SizedBox.square(
                            dimension: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.send),
                  ),
                  const SizedBox(width: 4),
                  IconButton(
                    tooltip:
                        voice.phase == VoicePhase.idle ||
                            voice.phase == VoicePhase.paused
                        ? '开始语音对话'
                        : '停止语音对话',
                    onPressed: model.loading
                        ? null
                        : () =>
                              voice.phase == VoicePhase.idle ||
                                  voice.phase == VoicePhase.paused
                              ? voice.start()
                              : voice.stop(),
                    icon: Icon(
                      voice.phase == VoicePhase.idle ||
                              voice.phase == VoicePhase.paused
                          ? Icons.mic_none
                          : Icons.stop_circle_outlined,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      );
    },
  );
}
