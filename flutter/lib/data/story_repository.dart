import 'dart:convert';

import 'package:flutter/services.dart';

import '../domain/age_band.dart';
import '../domain/story_models.dart';

class StoryRepository {
  StoryRepository({AssetBundle? bundle}) : _bundle = bundle ?? rootBundle;

  final AssetBundle _bundle;
  List<Story>? _cache;

  Future<List<Story>> all() async {
    final cached = _cache;
    if (cached != null) return cached;
    final stories = <Story>[];
    for (final asset in const [
      'assets/stories/age_6_8.json',
      'assets/stories/age_9_10.json',
      'assets/stories/age_11_12.json',
    ]) {
      final decoded = jsonDecode(await _bundle.loadString(asset));
      if (decoded is! List || decoded.any((item) => item is! Map)) {
        throw FormatException('Invalid story catalog asset: $asset');
      }
      stories.addAll(
        decoded.map(
          (item) => Story.fromJson(Map<String, Object?>.from(item as Map)),
        ),
      );
    }
    _cache = List.unmodifiable(stories);
    return _cache!;
  }

  Future<List<Story>> forAge(AgeBand ageBand) async => (await all())
      .where((story) => story.ageBand == ageBand.wireValue)
      .toList(growable: false);
}
