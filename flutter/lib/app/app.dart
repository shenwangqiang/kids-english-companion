import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../data/local_database.dart';
import '../data/ollama_client.dart';
import '../data/progress_repository.dart';
import '../data/secure_token_store.dart';
import '../data/session_repository.dart';
import '../data/settings_repository.dart';
import '../data/story_repository.dart';
import '../features/conversation/conversation_page.dart';
import '../features/conversation/conversation_view_model.dart';
import '../features/conversation/topic_picker_page.dart';
import '../features/history/history_page.dart';
import '../features/onboarding/onboarding_page.dart';
import '../features/practice/practice_page.dart';
import '../features/progress/progress_page.dart';
import '../features/settings/settings_page.dart';
import '../features/stories/reader_page.dart';
import '../features/stories/story_library_page.dart';
import '../platform/speech_service.dart';
import '../platform/tts_service.dart';
import '../platform/voice_session_controller.dart';
import 'app_theme.dart';

class KidsEnglishCompanionApp extends StatelessWidget {
  const KidsEnglishCompanionApp({super.key});

  @override
  Widget build(BuildContext context) {
    final database = LocalDatabase();
    final settings = SettingsRepository();
    final tokens = SecureTokenStore();
    final sessions = SessionRepository(database);
    final progress = ProgressRepository(database);
    final stories = StoryRepository();
    final ollama = OllamaClient(settings: settings, tokens: tokens);
    final speech = SpeechService();
    final tts = TtsService();
    return MultiProvider(
      providers: [
        Provider.value(value: database),
        Provider.value(value: settings),
        Provider.value(value: tokens),
        Provider.value(value: sessions),
        Provider.value(value: progress),
        Provider.value(value: stories),
        Provider.value(value: ollama),
        Provider.value(value: speech),
        Provider.value(value: tts),
        ChangeNotifierProvider(
          create: (_) => ConversationViewModel(
            sessions: sessions,
            settings: settings,
            ollama: ollama,
          )..initialize(),
        ),
        ChangeNotifierProxyProvider<
          ConversationViewModel,
          VoiceSessionController
        >(
          create: (context) => VoiceSessionController(
            conversation: context.read<ConversationViewModel>(),
            speech: speech,
            tts: tts,
            settings: settings,
          ),
          update: (_, conversation, voice) => voice!,
        ),
      ],
      child: MaterialApp(
        title: '英语伙伴',
        debugShowCheckedModeBanner: false,
        theme: buildAppTheme(),
        home: const _AppShell(),
      ),
    );
  }
}

class _AppShell extends StatelessWidget {
  const _AppShell();

  @override
  Widget build(BuildContext context) => Consumer<ConversationViewModel>(
    builder: (context, model, _) {
      final stories = context.read<StoryRepository>();
      final progress = context.read<ProgressRepository>();
      final ollama = context.read<OllamaClient>();
      final settings = context.read<SettingsRepository>();
      final tokens = context.read<SecureTokenStore>();
      final tts = context.read<TtsService>();
      if (!model.initialized)
        return const Material(
          child: Center(child: CircularProgressIndicator()),
        );
      final titles = {
        AppRoute.conversation: '英语伙伴',
        AppRoute.topics: '选择主题',
        AppRoute.history: '历史对话',
        AppRoute.library: '英语读物',
        AppRoute.reader: '阅读',
        AppRoute.practice: '口语练习',
        AppRoute.progress: '学习进度',
        AppRoute.settings: '模型设置',
        AppRoute.welcome: '英语伙伴',
      };
      return Scaffold(
        appBar: model.route == AppRoute.welcome
            ? null
            : AppBar(
                title: Text(titles[model.route] ?? '英语伙伴'),
                leading: model.route == AppRoute.conversation
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.arrow_back),
                        onPressed: () {
                          context.read<VoiceSessionController>().stop();
                          model.navigate(AppRoute.conversation);
                        },
                      ),
                actions: model.route == AppRoute.conversation
                    ? [
                        PopupMenuButton<AppRoute>(
                          tooltip: '更多功能',
                          onSelected: (route) {
                            context.read<VoiceSessionController>().stop();
                            model.navigate(route);
                          },
                          itemBuilder: (_) => const [
                            PopupMenuItem(
                              value: AppRoute.topics,
                              child: Text('新建主题'),
                            ),
                            PopupMenuItem(
                              value: AppRoute.history,
                              child: Text('历史对话'),
                            ),
                            PopupMenuItem(
                              value: AppRoute.library,
                              child: Text('英语读物'),
                            ),
                            PopupMenuItem(
                              value: AppRoute.practice,
                              child: Text('口语练习'),
                            ),
                            PopupMenuItem(
                              value: AppRoute.progress,
                              child: Text('学习进度'),
                            ),
                            PopupMenuItem(
                              value: AppRoute.settings,
                              child: Text('模型设置'),
                            ),
                          ],
                        ),
                      ]
                    : null,
              ),
        body: switch (model.route) {
          AppRoute.welcome => OnboardingPage(onChoose: model.chooseAgeBand),
          AppRoute.conversation => const ConversationPage(),
          AppRoute.topics => TopicPickerPage(
            onSelect: model.createConversation,
          ),
          AppRoute.history => HistoryPage(
            sessions: model.history,
            onSelect: model.openConversation,
            onDelete: model.deleteConversation,
          ),
          AppRoute.library => StoryLibraryPage(
            ageBand: model.ageBand,
            stories: stories,
            progress: progress,
            onSelect: model.openStory,
          ),
          AppRoute.reader =>
            model.selectedStory == null
                ? const Center(child: Text('请先选择一本读物'))
                : ReaderPage(
                    story: model.selectedStory!,
                    ageBand: model.ageBand,
                    progress: progress,
                    ollama: ollama,
                    tts: tts,
                    settings: settings,
                    onPractice: model.openPractice,
                  ),
          AppRoute.practice =>
            model.selectedStory == null
                ? const Center(child: Text('请先选择一本读物'))
                : PracticePage(
                    story: model.selectedStory!,
                    ageBand: model.ageBand,
                    progress: progress,
                    speech: context.read<SpeechService>(),
                  ),
          AppRoute.progress => ProgressPage(
            ageBand: model.ageBand,
            progress: progress,
            stories: stories,
          ),
          AppRoute.settings => SettingsPage(
            settings: settings,
            tokens: tokens,
            ollama: ollama,
            tts: tts,
            onAgeBandChanged: model.chooseAgeBand,
          ),
        },
        floatingActionButton: model.route == AppRoute.conversation
            ? FloatingActionButton.small(
                tooltip: '切换年龄段',
                onPressed: () => model.navigate(AppRoute.welcome),
                child: const Icon(Icons.face_outlined),
              )
            : null,
      );
    },
  );
}
