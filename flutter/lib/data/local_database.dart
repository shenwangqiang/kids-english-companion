import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

class LocalDatabase {
  Database? _database;

  Future<Database> get database async => _database ??= await _open();

  Future<Database> _open() async {
    final root = await getDatabasesPath();
    return openDatabase(
      p.join(root, 'kids_english_companion_flutter.db'),
      version: 1,
      onConfigure: (db) => db.execute('PRAGMA foreign_keys = ON'),
      onCreate: (db, version) async {
        await db.execute('''CREATE TABLE sessions (
          id TEXT PRIMARY KEY,
          age_band TEXT NOT NULL,
          topic_json TEXT NOT NULL,
          title TEXT NOT NULL,
          summary TEXT NOT NULL,
          next_direction TEXT NOT NULL,
          remembered_facts_json TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )''');
        await db.execute('''CREATE TABLE messages (
          id TEXT PRIMARY KEY,
          session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
          role TEXT NOT NULL,
          content TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )''');
        await db.execute(
          'CREATE INDEX messages_session_time ON messages(session_id, created_at)',
        );
        await db.execute('''CREATE TABLE story_completion (
          story_id TEXT NOT NULL,
          age_band TEXT NOT NULL,
          completed_at INTEGER NOT NULL,
          PRIMARY KEY(story_id, age_band)
        )''');
        await db.execute('''CREATE TABLE practice_attempts (
          id TEXT PRIMARY KEY,
          story_id TEXT NOT NULL,
          age_band TEXT NOT NULL,
          target_text TEXT NOT NULL,
          recognized_text TEXT NOT NULL,
          score INTEGER NOT NULL,
          created_at INTEGER NOT NULL
        )''');
      },
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 1) {
          throw StateError(
            'Unsupported database upgrade path: $oldVersion → $newVersion',
          );
        }
      },
    );
  }
}
