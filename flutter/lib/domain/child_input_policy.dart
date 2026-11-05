class ChildInputPolicy {
  static const redirect = "Let's stay with our story and learning activity.";

  static final _blockedPatterns = <RegExp>[
    RegExp(r'\b(home )?address\b', caseSensitive: false),
    RegExp(r'\b(phone|mobile|contact) number\b', caseSensitive: false),
    RegExp(r'\b(my|your) school\b', caseSensitive: false),
    RegExp(
      r'\b(send|share|show).{0,20}(photo|picture|secret)\b',
      caseSensitive: false,
    ),
    RegExp(r'\b(self[- ]?harm|suicide|kill myself)\b', caseSensitive: false),
    RegExp(r'\b(make|build).{0,20}(bomb|weapon)\b', caseSensitive: false),
    RegExp(r'\b(nude|sexual|porn)\b', caseSensitive: false),
  ];

  static bool isBlocked(String value) {
    final input = value.trim();
    return input.isEmpty ||
        input.length > 500 ||
        _blockedPatterns.any((pattern) => pattern.hasMatch(input));
  }
}
