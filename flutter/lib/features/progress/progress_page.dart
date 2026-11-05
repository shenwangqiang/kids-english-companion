import 'package:flutter/material.dart';

import '../../data/progress_repository.dart';
import '../../data/story_repository.dart';
import '../../domain/age_band.dart';

class ProgressPage extends StatelessWidget {
  const ProgressPage({
    super.key,
    required this.ageBand,
    required this.progress,
    required this.stories,
  });
  final AgeBand ageBand;
  final ProgressRepository progress;
  final StoryRepository stories;

  @override
  Widget build(BuildContext context) => FutureBuilder<List<dynamic>>(
    future: Future.wait([
      progress.completedStories(ageBand),
      progress.bestPracticeScore(ageBand),
      stories.forAge(ageBand),
    ]),
    builder: (context, snapshot) {
      if (!snapshot.hasData)
        return const Center(child: CircularProgressIndicator());
      final completed = snapshot.data![0] as Set<String>;
      final best = snapshot.data![1] as int?;
      final allStories = snapshot.data![2] as List;
      return ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(ageBand.label, style: Theme.of(context).textTheme.headlineSmall),
          const SizedBox(height: 12),
          Card(
            child: ListTile(
              leading: const Icon(Icons.menu_book_outlined),
              title: const Text('已完成读物'),
              trailing: Text('${completed.length}/${allStories.length}'),
            ),
          ),
          Card(
            child: ListTile(
              leading: const Icon(Icons.record_voice_over_outlined),
              title: const Text('最佳跟读匹配度'),
              trailing: Text(best == null ? '—' : '$best%'),
            ),
          ),
        ],
      );
    },
  );
}
