import 'package:flutter/material.dart';

import '../../data/ollama_client.dart';
import '../../data/progress_repository.dart';
import '../../data/settings_repository.dart';
import '../../domain/age_band.dart';
import '../../domain/story_models.dart';
import '../../platform/tts_service.dart';
import 'story_tutor_panel.dart';

class ReaderPage extends StatefulWidget {
  const ReaderPage({
    super.key,
    required this.story,
    required this.ageBand,
    required this.progress,
    required this.ollama,
    required this.tts,
    required this.settings,
    required this.onPractice,
  });

  final Story story;
  final AgeBand ageBand;
  final ProgressRepository progress;
  final OllamaClient ollama;
  final TtsService tts;
  final SettingsRepository settings;
  final VoidCallback onPractice;

  @override
  State<ReaderPage> createState() => _ReaderPageState();
}

class _ReaderPageState extends State<ReaderPage> {
  final _answers = <String, String>{};
  bool _saved = false;

  bool _isCorrect(ComprehensionQuestion question) {
    final answer = (_answers[question.id] ?? '').toLowerCase();
    return question.answerConcepts.any(
      (concept) => answer.contains(concept.toLowerCase()),
    );
  }

  Future<void> _complete() async {
    await widget.progress.completeStory(widget.story.id, widget.ageBand);
    if (mounted) setState(() => _saved = true);
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(16),
    children: [
      Text(
        widget.story.title,
        style: Theme.of(context).textTheme.headlineSmall,
      ),
      const SizedBox(height: 4),
      Text('${widget.story.cefr} · ${widget.story.summary}'),
      const SizedBox(height: 20),
      for (final paragraph in widget.story.paragraphs)
        Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (final sentence in paragraph.sentences)
                Card(
                  child: ListTile(
                    title: Text(
                      sentence.text,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    subtitle: Text(sentence.chineseHint),
                    trailing: const Icon(Icons.volume_up_outlined),
                    onTap: () async {
                      final preferences = await widget.settings.load();
                      await widget.tts.speak(
                        sentence.text,
                        preference: preferences.ttsPreference,
                        rate: preferences.speechRate,
                      );
                    },
                  ),
                ),
            ],
          ),
        ),
      Text('生词', style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 8),
      for (final word in widget.story.vocabulary)
        Card(
          child: ListTile(
            title: Text('${word.word} · ${word.chinese}'),
            subtitle: Text('${word.definition}\n${word.example}'),
          ),
        ),
      const SizedBox(height: 20),
      Text('读后小问题', style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 8),
      for (final question in widget.story.questions)
        Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(question.prompt),
                const SizedBox(height: 8),
                TextField(
                  onChanged: (value) => _answers[question.id] = value,
                  decoration: InputDecoration(
                    hintText: '输入你的答案',
                    suffixIcon: _answers.containsKey(question.id)
                        ? Icon(
                            _isCorrect(question)
                                ? Icons.check_circle
                                : Icons.edit_outlined,
                          )
                        : null,
                  ),
                ),
              ],
            ),
          ),
        ),
      StoryTutorPanel(story: widget.story, ollama: widget.ollama),
      const SizedBox(height: 12),
      FilledButton(
        onPressed: _complete,
        child: Text(_saved ? '已完成阅读 ✓' : '完成阅读'),
      ),
      OutlinedButton(onPressed: widget.onPractice, child: const Text('练习朗读')),
    ],
  );
}
