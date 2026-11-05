import 'package:flutter/material.dart';

import '../../data/progress_repository.dart';
import '../../data/story_repository.dart';
import '../../domain/age_band.dart';
import '../../domain/story_models.dart';

class StoryLibraryPage extends StatelessWidget {
  const StoryLibraryPage({
    super.key,
    required this.ageBand,
    required this.stories,
    required this.progress,
    required this.onSelect,
  });

  final AgeBand ageBand;
  final StoryRepository stories;
  final ProgressRepository progress;
  final ValueChanged<Story> onSelect;

  @override
  Widget build(BuildContext context) => FutureBuilder<List<dynamic>>(
    future: Future.wait([
      stories.forAge(ageBand),
      progress.completedStories(ageBand),
    ]),
    builder: (context, snapshot) {
      if (!snapshot.hasData)
        return const Center(child: CircularProgressIndicator());
      final items = snapshot.data!;
      final stories = items[0] as List<Story>;
      final completed = items[1] as Set<String>;
      return ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: stories.length,
        separatorBuilder: (_, __) => const SizedBox(height: 10),
        itemBuilder: (context, index) {
          final story = stories[index];
          return Card(
            child: ListTile(
              contentPadding: const EdgeInsets.all(16),
              title: Text(
                story.title,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              subtitle: Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text('${story.cefr} · ${story.summary}'),
              ),
              trailing: Icon(
                completed.contains(story.id)
                    ? Icons.check_circle
                    : Icons.chevron_right,
              ),
              onTap: () => onSelect(story),
            ),
          );
        },
      );
    },
  );
}
