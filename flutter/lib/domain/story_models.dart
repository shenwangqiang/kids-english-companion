class StorySentence {
  const StorySentence({
    required this.id,
    required this.text,
    required this.chineseHint,
  });

  final String id;
  final String text;
  final String chineseHint;

  factory StorySentence.fromJson(Map<String, Object?> json) => StorySentence(
    id: _requiredString(json, 'id'),
    text: _requiredString(json, 'text'),
    chineseHint: _requiredString(json, 'chineseHint'),
  );
}

class StoryParagraph {
  const StoryParagraph({required this.id, required this.sentences});

  final String id;
  final List<StorySentence> sentences;

  factory StoryParagraph.fromJson(Map<String, Object?> json) => StoryParagraph(
    id: _requiredString(json, 'id'),
    sentences: _requiredObjects(json, 'sentences', StorySentence.fromJson),
  );
}

class VocabularyItem {
  const VocabularyItem({
    required this.word,
    required this.definition,
    required this.chinese,
    required this.example,
  });

  final String word;
  final String definition;
  final String chinese;
  final String example;

  factory VocabularyItem.fromJson(Map<String, Object?> json) => VocabularyItem(
    word: _requiredString(json, 'word'),
    definition: _requiredString(json, 'definition'),
    chinese: _requiredString(json, 'chinese'),
    example: _requiredString(json, 'example'),
  );
}

class ComprehensionQuestion {
  const ComprehensionQuestion({
    required this.id,
    required this.prompt,
    required this.answerConcepts,
  });

  final String id;
  final String prompt;
  final List<String> answerConcepts;

  factory ComprehensionQuestion.fromJson(Map<String, Object?> json) {
    final concepts = json['answerConcepts'];
    if (concepts is! List || concepts.any((item) => item is! String)) {
      throw const FormatException('answerConcepts must be a string array.');
    }
    return ComprehensionQuestion(
      id: _requiredString(json, 'id'),
      prompt: _requiredString(json, 'prompt'),
      answerConcepts: concepts.cast<String>(),
    );
  }
}

class Story {
  const Story({
    required this.id,
    required this.title,
    required this.ageBand,
    required this.cefr,
    required this.summary,
    required this.paragraphs,
    required this.vocabulary,
    required this.questions,
    this.attribution = 'Original content for Kids English Companion',
  });

  final String id;
  final String title;
  final String ageBand;
  final String cefr;
  final String summary;
  final List<StoryParagraph> paragraphs;
  final List<VocabularyItem> vocabulary;
  final List<ComprehensionQuestion> questions;
  final String attribution;

  factory Story.fromJson(Map<String, Object?> json) {
    final story = Story(
      id: _requiredString(json, 'id'),
      title: _requiredString(json, 'title'),
      ageBand: _requiredString(json, 'ageBand'),
      cefr: _requiredString(json, 'cefr'),
      summary: _requiredString(json, 'summary'),
      paragraphs: _requiredObjects(json, 'paragraphs', StoryParagraph.fromJson),
      vocabulary: _requiredObjects(json, 'vocabulary', VocabularyItem.fromJson),
      questions: _requiredObjects(
        json,
        'questions',
        ComprehensionQuestion.fromJson,
      ),
      attribution: json['attribution'] is String
          ? json['attribution']! as String
          : 'Original content for Kids English Companion',
    );
    if (!{'6-8', '9-10', '11-12'}.contains(story.ageBand))
      throw FormatException('Unknown story ageBand: ${story.ageBand}');
    return story;
  }
}

String _requiredString(Map<String, Object?> json, String key) {
  final value = json[key];
  if (value is! String || value.isEmpty)
    throw FormatException('Missing story string field: $key');
  return value;
}

List<T> _requiredObjects<T>(
  Map<String, Object?> json,
  String key,
  T Function(Map<String, Object?>) decode,
) {
  final value = json[key];
  if (value is! List || value.any((item) => item is! Map))
    throw FormatException('$key must be an object array.');
  return value
      .map((item) => decode(Map<String, Object?>.from(item as Map)))
      .toList(growable: false);
}
