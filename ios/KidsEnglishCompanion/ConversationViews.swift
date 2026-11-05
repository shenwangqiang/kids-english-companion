import SwiftUI

struct ConversationView: View {
    @EnvironmentObject private var store: CompanionStore
    @EnvironmentObject private var speech: SpeechManager
    @State private var draft = ""
    @State private var activeRequestIDs: Set<UUID> = []
    @State private var showingTopics = false
    @State private var alertMessage: String?
    private let service = OllamaService()

    private var session: ChatSession? { store.currentSession }
    private var isWorking: Bool { session.map { activeRequestIDs.contains($0.id) } ?? false }

    var body: some View {
        NavigationStack {
            ZStack {
                AppPalette.canvas.ignoresSafeArea()
                VStack(spacing: 0) {
                    if speech.isVoiceConversationActive {
                        voiceStatus
                    }
                    conversationList
                    composer
                }
            }
            .navigationTitle(session?.topic.title ?? "英语伙伴")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Menu {
                        ForEach(AgeBand.allCases) { age in
                            Button(age.label) { store.chooseAgeBand(age) }
                        }
                    } label: {
                        Image(systemName: "person.crop.circle")
                            .foregroundStyle(AppPalette.blue)
                    }
                    .accessibilityLabel("选择年龄段")
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { showingTopics = true } label: { Image(systemName: "square.and.pencil") }
                        .accessibilityLabel("新建对话")
                    NavigationLink { HistoryView() } label: { Image(systemName: "clock.arrow.circlepath") }
                        .accessibilityLabel("历史对话")
                }
            }
            .sheet(isPresented: $showingTopics) {
                NewConversationSheet { topic in store.createSession(topic: topic) }
            }
            .alert("对话", isPresented: Binding(
                get: { alertMessage != nil },
                set: { if !$0 { alertMessage = nil } }
            )) {
                Button("确定", role: .cancel) { alertMessage = nil }
            } message: { Text(alertMessage ?? "") }
            .task(id: store.selectedSessionID) { await openIfNeeded() }
            .onChange(of: speech.isVoiceConversationActive) { _, active in
                if !active { speech.onFinalTranscript = nil }
            }
        }
    }

    private var voiceStatus: some View {
        HStack(spacing: 10) {
            Circle().fill(speech.isListening ? Color.green : AppPalette.blue).frame(width: 8, height: 8)
            Text(speech.isListening ? "正在听，请开始说英语。" : "伙伴正在思考或朗读…")
                .font(.system(size: 13, weight: .medium, design: .rounded))
            Spacer()
            Button("停止") { speech.stop() }
                .font(.system(size: 13, weight: .bold, design: .rounded))
        }
        .foregroundStyle(AppPalette.ink)
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .background(AppPalette.mint.opacity(0.65))
    }

    private var conversationList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    if let session {
                        if session.messages.isEmpty {
                            VStack(spacing: 12) {
                                Text("✨") .font(.system(size: 42))
                                Text("今天来聊聊：\(session.topic.title)")
                                    .font(.system(size: 18, weight: .bold, design: .rounded))
                                    .foregroundStyle(AppPalette.ink)
                                Text("英语伙伴正在准备…")
                                    .font(.system(size: 14, design: .rounded))
                                    .foregroundStyle(AppPalette.muted)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.top, 90)
                        }
                        ForEach(session.messages) { message in
                            MessageBubble(message: message) { speech.speak(message.content) }
                                .id(message.id)
                        }
                        if isWorking { typingIndicator }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 18)
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: session?.messages.count) { _, _ in
                guard let last = session?.messages.last else { return }
                withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(last.id, anchor: .bottom) }
            }
        }
    }

    private var typingIndicator: some View {
        HStack {
            ProgressView().tint(AppPalette.blue)
            Text("正在思考…")
                .font(.system(size: 13, design: .rounded))
                .foregroundStyle(AppPalette.muted)
            Spacer()
        }
        .padding(14)
        .background(.white, in: RoundedRectangle(cornerRadius: 18))
    }

    private var composer: some View {
        HStack(alignment: .bottom, spacing: 10) {
            Button(action: toggleVoice) {
                Image(systemName: speech.isVoiceConversationActive ? "stop.fill" : "mic.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(speech.isVoiceConversationActive ? .white : AppPalette.blue)
                    .frame(width: 44, height: 44)
                    .background(speech.isVoiceConversationActive ? AppPalette.blue : AppPalette.lavender.opacity(0.6), in: Circle())
            }
            .disabled(isWorking && !speech.isVoiceConversationActive)
            .accessibilityLabel(speech.isVoiceConversationActive ? "停止语音对话" : "开始语音对话")

            TextField("用英语说点什么…", text: $draft, axis: .vertical)
                .lineLimit(1...4)
                .font(.system(size: 16, design: .rounded))
                .padding(.horizontal, 15)
                .padding(.vertical, 12)
                .background(.white, in: RoundedRectangle(cornerRadius: 22))
                .disabled(isWorking)
                .onSubmit { sendDraft() }

            Button(action: sendDraft) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(AppPalette.blue, in: Circle())
            }
            .disabled(isWorking || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(isWorking || draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.45 : 1)
            .accessibilityLabel("发送消息")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(AppPalette.canvas)
    }

    private func openIfNeeded() async {
        guard let session, session.messages.isEmpty, !activeRequestIDs.contains(session.id) else { return }
        await requestReply(for: session, opening: true)
    }

    private func sendDraft() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !isWorking else { return }
        draft = ""
        send(text)
    }

    private func send(_ text: String) {
        guard let session, !isWorking else { return }
        guard !ChildInputPolicy.isBlocked(text) else {
            store.appendTutorMessage(ChildInputPolicy.redirect, to: session.id)
            if speech.isVoiceConversationActive { speech.speak(ChildInputPolicy.redirect) }
            return
        }
        store.appendUserMessage(text, to: session.id)
        guard let updatedSession = store.session(id: session.id) else { return }
        Task { await requestReply(for: updatedSession, opening: false) }
    }

    private func requestReply(for session: ChatSession, opening: Bool) async {
        guard !activeRequestIDs.contains(session.id) else { return }
        activeRequestIDs.insert(session.id)
        defer { activeRequestIDs.remove(session.id) }
        do {
            let connection = OllamaConnection(baseURL: store.baseURL, model: store.modelName, token: store.secureToken)
            let outcome = try await service.converse(session: session, opening: opening, connection: connection)
            store.appendAssistantMessage(outcome, to: session.id)
            if speech.isVoiceConversationActive, store.selectedSessionID == session.id { speech.speak(outcome.reply) }
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    private func toggleVoice() {
        if speech.isVoiceConversationActive {
            speech.stop()
            return
        }
        Task {
            do {
                try await speech.startVoiceConversation { transcript in
                    send(transcript)
                }
            } catch {
                alertMessage = error.localizedDescription
            }
        }
    }
}

struct MessageBubble: View {
    let message: ChatMessage
    let onReplay: () -> Void

    private var isUser: Bool { message.role == .user }

    var body: some View {
        HStack {
            if isUser { Spacer(minLength: 42) }
            Button(action: onReplay) {
                HStack(alignment: .top, spacing: 9) {
                    if !isUser {
                        Image(systemName: "sparkles")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(AppPalette.blue)
                            .padding(.top, 3)
                    }
                    Text(message.content)
                        .font(.system(size: 16, design: .rounded))
                        .multilineTextAlignment(.leading)
                        .foregroundStyle(isUser ? .white : AppPalette.ink)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "speaker.wave.2.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(isUser ? .white.opacity(0.72) : AppPalette.muted.opacity(0.7))
                        .padding(.top, 4)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(isUser ? AppPalette.blue : .white, in: RoundedRectangle(cornerRadius: 19))
            }
            .buttonStyle(.plain)
                        .accessibilityLabel("朗读消息：\(message.content)")
            if !isUser { Spacer(minLength: 42) }
        }
    }
}

struct NewConversationSheet: View {
    @EnvironmentObject private var store: CompanionStore
    @Environment(\.dismiss) private var dismiss
    @State private var customTopic = ""
    let onCreate: (ConversationTopic) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section("选择话题") {
                    topicButton(.general)
                    ForEach(ConversationTopic.predefined) { topic in topicButton(topic) }
                }
                Section("自定义话题") {
                    HStack(spacing: 8) {
                        TextField("输入你喜欢的话题", text: $customTopic)
                            .textInputAutocapitalization(.sentences)
                        Button("开始") {
                            guard let topic = ConversationTopic.custom(customTopic) else { return }
                            onCreate(topic)
                            dismiss()
                        }
                        .disabled(ConversationTopic.custom(customTopic) == nil)
                    }
                    Text("伙伴会使用适合年龄的简短英语交流，并避免询问个人信息。")
                        .font(.footnote)
                        .foregroundStyle(AppPalette.muted)
                }
            }
            .navigationTitle("新建对话")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("完成") { dismiss() } } }
        }
    }

    private func topicButton(_ topic: ConversationTopic) -> some View {
        Button {
            onCreate(topic)
            dismiss()
        } label: {
            HStack {
                Text(topic.title).foregroundStyle(AppPalette.ink)
                Spacer()
                if topic.branded { Text("DISCUSSION").font(.system(size: 9, weight: .bold)).foregroundStyle(AppPalette.muted) }
                Image(systemName: "arrow.up.right").foregroundStyle(AppPalette.blue)
            }
        }
        .buttonStyle(.plain)
    }
}

struct HistoryView: View {
    @EnvironmentObject private var store: CompanionStore

    var body: some View {
        NavigationStack {
            ZStack {
                AppPalette.canvas.ignoresSafeArea()
                let items = store.sessions(for: store.selectedAgeBand)
                if items.isEmpty {
                    ContentUnavailableView("还没有对话", systemImage: "bubble.left.and.bubble.right", description: Text("新建的对话会显示在这里。"))
                } else {
                    List {
                        ForEach(items) { session in
                            Button { store.selectSession(session.id) } label: {
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack {
                                        Text(session.title).font(.system(size: 16, weight: .bold, design: .rounded)).foregroundStyle(AppPalette.ink)
                                        Spacer()
                                        Text(session.ageBand.label).font(.caption2).foregroundStyle(AppPalette.muted)
                                    }
                                    Text(session.messages.last?.content ?? session.topic.title)
                                        .font(.system(size: 13, design: .rounded)).foregroundStyle(AppPalette.muted).lineLimit(2)
                                    Text(session.updatedAt.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption2).foregroundStyle(AppPalette.muted.opacity(0.8))
                                }
                                .padding(.vertical, 5)
                            }
                            .listRowBackground(Color.white)
                            .buttonStyle(.plain)
                            .swipeActions {
                                Button("删除", role: .destructive) { store.deleteSession(session.id) }
                            }
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle("历史对话")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        ForEach(AgeBand.allCases) { age in Button(age.label) { store.chooseAgeBand(age) } }
                    } label: { Image(systemName: "person.crop.circle") }
                }
            }
        }
    }
}
