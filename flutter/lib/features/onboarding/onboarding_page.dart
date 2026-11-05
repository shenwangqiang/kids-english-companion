import 'package:flutter/material.dart';

import '../../domain/age_band.dart';

class OnboardingPage extends StatelessWidget {
  const OnboardingPage({super.key, required this.onChoose});

  final ValueChanged<AgeBand> onChoose;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      const SizedBox(height: 48),
      Text(
        '欢迎来到英语伙伴',
        style: Theme.of(context).textTheme.headlineMedium
            ?.copyWith(fontWeight: FontWeight.bold),
      ),
      const SizedBox(height: 12),
      Text(
        '选择年龄段，我们会用适合你的方式一起练习英语。',
        style: Theme.of(context).textTheme.bodyLarge,
      ),
      const SizedBox(height: 24),
      for (final age in AgeBand.values)
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: FilledButton.tonal(
            onPressed: () => onChoose(age),
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(64),
            ),
            child: Text(
              age.label,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
        ),
    ],
  );
}
