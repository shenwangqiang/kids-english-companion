import 'package:flutter/material.dart';

import '../../data/progress_repository.dart';
import '../../domain/age_band.dart';
import '../../domain/practice_models.dart';
import '../../domain/story_models.dart';
import '../../platform/speech_service.dart';

class PracticePage extends StatefulWidget {
  const PracticePage({
    super.key,
    required this.story,
    required this.ageBand,
    required this.progress,
    required this.speech,
  });
  final Story story;
  final AgeBand ageBand;
  final ProgressRepository progress;
  final SpeechService speech;

  @override
  State<PracticePage> createState() => _PracticePageState();
}

class _PracticePageState extends State<PracticePage> {
  final _heardController = TextEditingController();
  SpeakingScore? _score;
  bool _listening = false;
  String? _speechError;

  @override
  void dispose() {
    _heardController.dispose();
    super.dispose();
  }

  String get _target => widget.story.paragraphs
      .expand((paragraph) => paragraph.sentences)
      .map((sentence) => sentence.text)
      .join(' ');

  Future<void> _scoreAttempt() async {
    final score = SpeakingScore.calculate(_target, _heardController.text);
    setState(() => _score = score);
    await widget.progress.savePractice(
      storyId: widget.story.id,
      ageBand: widget.ageBand,
      target: _target,
      heard: _heardController.text,
      score: score,
    );
  }

  Future<void> _recognize() async {
    setState(() {
      _listening = true;
      _speechError = null;
    });
    try {
      final words = await widget.speech.listenForOneEnglishUtterance();
      if (mounted) _heardController.text = words;
    } on Object catch (error) {
      if (mounted) setState(() => _speechError = error.toString());
    } finally {
      if (mounted) setState(() => _listening = false);
    }
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      Text('跟读练习', style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 8),
      Text(widget.story.title, style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 12),
      Card(
        child: Padding(padding: const EdgeInsets.all(16), child: Text(_target)),
      ),
      const SizedBox(height: 16),
      Text('朗读后查看识别结果，应用会帮你对照单词。'),
      const SizedBox(height: 8),
      OutlinedButton.icon(
        onPressed: _listening ? null : _recognize,
        icon: const Icon(Icons.mic),
        label: Text(_listening ? '正在聆听…' : '朗读并识别'),
      ),
      if (_speechError != null)
        Text(
          _speechError!,
          style: TextStyle(color: Theme.of(context).colorScheme.error),
        ),
      const SizedBox(height: 8),
      TextField(
        controller: _heardController,
        minLines: 2,
        maxLines: 5,
        decoration: const InputDecoration(
          labelText: '识别到的朗读内容',
          border: OutlineInputBorder(),
        ),
      ),
      const SizedBox(height: 8),
      FilledButton.icon(
        onPressed: _scoreAttempt,
        icon: const Icon(Icons.check),
        label: const Text('查看练习结果'),
      ),
      if (_score case final score?) ...[
        const SizedBox(height: 16),
        Text(
          '匹配度 ${score.percent}%',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        Text('正确：${score.matched.join(' ')}'),
        Text('再试试：${score.missing.join(' ')}'),
        if (score.extra.isNotEmpty) Text('多识别：${score.extra.join(' ')}'),
      ],
    ],
  );
}
