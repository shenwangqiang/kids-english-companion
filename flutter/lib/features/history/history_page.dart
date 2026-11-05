import 'package:flutter/material.dart';

import '../../domain/conversation_models.dart';

class HistoryPage extends StatelessWidget {
  const HistoryPage({
    super.key,
    required this.sessions,
    required this.onSelect,
    required this.onDelete,
  });
  final List<ConversationSession> sessions;
  final ValueChanged<String> onSelect;
  final ValueChanged<String> onDelete;

  @override
  Widget build(BuildContext context) {
    if (sessions.isEmpty) return const Center(child: Text('还没有聊天记录'));
    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: sessions.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (context, index) {
        final session = sessions[index];
        return Card(
          child: ListTile(
            title: Text(
              session.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              '${session.ageBand.label} · ${session.updatedAt.toLocal()}',
            ),
            onTap: () => onSelect(session.id),
            trailing: IconButton(
              tooltip: '删除聊天记录',
              icon: const Icon(Icons.delete_outline),
              onPressed: () => onDelete(session.id),
            ),
          ),
        );
      },
    );
  }
}
