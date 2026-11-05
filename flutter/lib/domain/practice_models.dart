class SpeakingScore {
  const SpeakingScore({
    required this.target,
    required this.heard,
    required this.matched,
    required this.missing,
    required this.extra,
    required this.percent,
  });

  final List<String> target;
  final List<String> heard;
  final List<String> matched;
  final List<String> missing;
  final List<String> extra;
  final int percent;

  factory SpeakingScore.calculate(String targetText, String heardText) {
    final target = _tokens(targetText);
    final heard = _tokens(heardText);
    final rows = List.generate(
      target.length + 1,
      (_) => List<int>.filled(heard.length + 1, 0),
    );
    for (var i = target.length - 1; i >= 0; i--) {
      for (var j = heard.length - 1; j >= 0; j--) {
        rows[i][j] = target[i] == heard[j]
            ? rows[i + 1][j + 1] + 1
            : (rows[i + 1][j] >= rows[i][j + 1]
                  ? rows[i + 1][j]
                  : rows[i][j + 1]);
      }
    }
    final pairs = <(int, int)>[];
    var i = 0;
    var j = 0;
    while (i < target.length && j < heard.length) {
      if (target[i] == heard[j]) {
        pairs.add((i++, j++));
      } else if (rows[i + 1][j] >= rows[i][j + 1]) {
        i++;
      } else {
        j++;
      }
    }
    final targetMatched = pairs.map((pair) => pair.$1).toSet();
    final heardMatched = pairs.map((pair) => pair.$2).toSet();
    return SpeakingScore(
      target: target,
      heard: heard,
      matched: pairs.map((pair) => target[pair.$1]).toList(),
      missing: [
        for (var index = 0; index < target.length; index++)
          if (!targetMatched.contains(index)) target[index],
      ],
      extra: [
        for (var index = 0; index < heard.length; index++)
          if (!heardMatched.contains(index)) heard[index],
      ],
      percent: target.isEmpty
          ? 0
          : (100 * pairs.length / target.length).round(),
    );
  }
}

List<String> _tokens(String value) {
  final normalized = value
      .toLowerCase()
      .replaceAll(RegExp(r"[^a-z0-9' ]"), ' ')
      .trim()
      .replaceAll(RegExp(r'\s+'), ' ');
  return normalized.isEmpty ? const [] : normalized.split(' ');
}
