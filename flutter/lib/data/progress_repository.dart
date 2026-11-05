import 'package:sqflite/sqflite.dart';

import '../domain/age_band.dart';
import '../domain/practice_models.dart';
import 'local_database.dart';

class ProgressRepository {
  ProgressRepository(this._localDatabase);

  final LocalDatabase _localDatabase;

  Future<void> completeStory(String storyId, AgeBand ageBand) async {
    final db = await _localDatabase.database;
    await db.insert('story_completion', {
      'story_id': storyId,
      'age_band': ageBand.wireValue,
      'completed_at': DateTime.now().millisecondsSinceEpoch,
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }

  Future<Set<String>> completedStories(AgeBand ageBand) async {
    final db = await _localDatabase.database;
    final rows = await db.query(
      'story_completion',
      columns: ['story_id'],
      where: 'age_band = ?',
      whereArgs: [ageBand.wireValue],
    );
    return rows.map((row) => row['story_id']! as String).toSet();
  }

  Future<void> savePractice({
    required String storyId,
    required AgeBand ageBand,
    required String target,
    required String heard,
    required SpeakingScore score,
  }) async {
    final db = await _localDatabase.database;
    await db.insert('practice_attempts', {
      'id': '${DateTime.now().microsecondsSinceEpoch}',
      'story_id': storyId,
      'age_band': ageBand.wireValue,
      'target_text': target,
      'recognized_text': heard,
      'score': score.percent,
      'created_at': DateTime.now().millisecondsSinceEpoch,
    });
  }

  Future<int?> bestPracticeScore(AgeBand ageBand) async {
    final db = await _localDatabase.database;
    final rows = await db.rawQuery(
      'SELECT MAX(score) AS best FROM practice_attempts WHERE age_band = ?',
      [ageBand.wireValue],
    );
    return rows.single['best'] as int?;
  }
}
