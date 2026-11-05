import 'package:flutter/material.dart';

import '../../domain/conversation_models.dart';

class MessageBubble extends StatelessWidget {
  const MessageBubble({super.key, required this.message, this.onReplay});
  final ChatMessage message;
  final VoidCallback? onReplay;

  @override
  Widget build(BuildContext context) {
    final isUser = message.role == ConversationRole.user;
    final colors = Theme.of(context).colorScheme;
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxWidth: MediaQuery.sizeOf(context).width * 0.82,
        ),
        child: Card(
          color: isUser ? colors.primaryContainer : Colors.white,
          child: InkWell(
            borderRadius: BorderRadius.circular(12),
            onTap: onReplay,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Flexible(
                    child: SelectableText(
                      message.content,
                      style: Theme.of(context).textTheme.bodyLarge,
                    ),
                  ),
                  if (!isUser && onReplay != null) ...[
                    const SizedBox(width: 8),
                    const Icon(Icons.volume_up_outlined, size: 18),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
