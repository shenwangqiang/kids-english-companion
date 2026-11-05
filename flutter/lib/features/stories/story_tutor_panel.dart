import 'package:flutter/material.dart';

import '../../data/ollama_client.dart';
import '../../domain/story_models.dart';

class StoryTutorPanel extends StatefulWidget {
  const StoryTutorPanel({super.key, required this.story, required this.ollama});

  final Story story;
  final OllamaClient ollama;

  @override
  State<StoryTutorPanel> createState() => _StoryTutorPanelState();
}

class _StoryTutorPanelState extends State<StoryTutorPanel> {
  final _controller = TextEditingController();
  bool _loading = false;
  String? _answer;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _ask() async {
    final question = _controller.text.trim();
    if (question.isEmpty || _loading) return;
    setState(() {
      _loading = true;
      _answer = null;
    });
    try {
      final answer = await widget.ollama.askStory(
        story: widget.story,
        question: question,
      );
      if (mounted) setState(() => _answer = answer);
    } on Object catch (error) {
      if (mounted)
        setState(
          () => _answer = error is OllamaException
              ? error.message
              : '暂时无法回答，请稍后再试。',
        );
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('问问故事老师', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          TextField(
            controller: _controller,
            maxLength: 500,
            decoration: const InputDecoration(
              hintText: '关于故事，你想知道什么？',
              border: OutlineInputBorder(),
              counterText: '',
            ),
          ),
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.tonal(
              onPressed: _loading ? null : _ask,
              child: _loading
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('提问'),
            ),
          ),
          if (_answer != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(_answer!),
            ),
        ],
      ),
    ),
  );
}
