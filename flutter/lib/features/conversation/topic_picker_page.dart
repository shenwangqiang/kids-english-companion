import 'package:flutter/material.dart';

import '../../domain/age_band.dart';

class TopicPickerPage extends StatefulWidget {
  const TopicPickerPage({super.key, required this.onSelect});
  final ValueChanged<ConversationTopic> onSelect;

  @override
  State<TopicPickerPage> createState() => _TopicPickerPageState();
}

class _TopicPickerPageState extends State<TopicPickerPage> {
  final _customController = TextEditingController();

  @override
  void dispose() {
    _customController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(20),
    children: [
      Text('选择聊天主题', style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: 16),
      for (final topic in [
        ConversationTopicCatalog.general,
        ...ConversationTopicCatalog.predefined,
      ])
        Padding(
          padding: const EdgeInsets.only(bottom: 10),
          child: Card(
            child: ListTile(
              minVerticalPadding: 14,
              title: Text(topic.title),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => widget.onSelect(topic),
            ),
          ),
        ),
      const SizedBox(height: 12),
      TextField(
        controller: _customController,
        maxLength: 60,
        decoration: const InputDecoration(
          labelText: '自定义主题',
          border: OutlineInputBorder(),
        ),
      ),
      FilledButton(
        onPressed: () {
          final topic = ConversationTopicCatalog.custom(_customController.text);
          if (topic != null) widget.onSelect(topic);
        },
        child: const Text('开始聊天'),
      ),
    ],
  );
}
