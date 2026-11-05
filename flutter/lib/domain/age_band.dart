enum AgeBand {
  sixToEight('6-8', '6–8 岁', 30, 180),
  nineToTen('9-10', '9–10 岁', 50, 240),
  elevenToTwelve('11-12', '11–12 岁', 75, 300);

  const AgeBand(
    this.wireValue,
    this.label,
    this.replyWordLimit,
    this.maxOutputTokens,
  );

  final String wireValue;
  final String label;
  final int replyWordLimit;
  final int maxOutputTokens;

  static AgeBand fromWireValue(String value) => AgeBand.values.firstWhere(
    (band) => band.wireValue == value,
    orElse: () => throw FormatException('Unknown age band: $value'),
  );
}

class ConversationTopic {
  const ConversationTopic({
    required this.id,
    required this.title,
    this.branded = false,
  });

  final String id;
  final String title;
  final bool branded;

  Map<String, Object?> toJson() => {
    'id': id,
    'title': title,
    'branded': branded,
  };

  factory ConversationTopic.fromJson(Map<String, Object?> json) {
    final id = json['id'];
    final title = json['title'];
    if (id is! String || id.isEmpty || title is! String || title.isEmpty) {
      throw const FormatException('Topic requires a non-empty id and title.');
    }
    return ConversationTopic(
      id: id,
      title: title,
      branded: json['branded'] == true,
    );
  }
}

abstract final class ConversationTopicCatalog {
  static const general = ConversationTopic(
    id: 'general',
    title: 'General Conversation',
  );

  static const predefined = <ConversationTopic>[
    ConversationTopic(
      id: 'how-to-train-your-dragon',
      title: 'How to Train Your Dragon',
      branded: true,
    ),
    ConversationTopic(id: 'harry-potter', title: 'Harry Potter', branded: true),
    ConversationTopic(id: 'space', title: 'Space'),
    ConversationTopic(id: 'dinosaurs', title: 'Dinosaurs'),
    ConversationTopic(id: 'animals', title: 'Animals'),
    ConversationTopic(id: 'school-life', title: 'School Life'),
  ];

  static ConversationTopic? custom(String raw) {
    final title = raw.trim().replaceAll(RegExp(r'\s+'), ' ');
    if (title.isEmpty || title.length > 60) return null;
    final slug = title
        .toLowerCase()
        .replaceAll(RegExp(r'[^a-z0-9]+'), '-')
        .replaceAll(RegExp(r'^-+|-+$'), '');
    return ConversationTopic(id: 'custom-$slug', title: title);
  }

  static ConversationTopic resolve(String id, String title) =>
      [...predefined, general].firstWhere(
        (topic) => topic.id == id,
        orElse: () => ConversationTopic(id: id, title: title),
      );
}
