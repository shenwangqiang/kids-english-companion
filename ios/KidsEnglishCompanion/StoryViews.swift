import SwiftUI

enum StoryLibrary {
    static func load() -> [Story] {
        let decoder = JSONDecoder()
        return ["age_6_8", "age_9_10", "age_11_12"].flatMap { name -> [Story] in
            guard let url = Bundle.main.url(forResource: name, withExtension: "json"),
                  let data = try? Data(contentsOf: url),
                  let stories = try? decoder.decode([Story].self, from: data) else { return [] }
            return stories
        }
    }
}

struct StoryLibraryView: View {
    @EnvironmentObject private var store: CompanionStore
    @State private var stories = StoryLibrary.load()

    private var ageBand: AgeBand { store.selectedAgeBand ?? .sixToEight }
    private var ageStories: [Story] { stories.filter { $0.ageBand == ageBand.rawValue } }

    var body: some View {
        NavigationStack {
            ZStack {
                AppPalette.canvas.ignoresSafeArea()
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 5) {
                                Text("英语阅读小冒险")
                                    .font(.system(size: 25, weight: .bold, design: .rounded))
                                    .foregroundStyle(AppPalette.ink)
                                Text("为 \(ageBand.label) 准备的原创故事")
                                    .font(.system(size: 14, design: .rounded))
                                    .foregroundStyle(AppPalette.muted)
                            }
                            Spacer()
                            Menu {
                                ForEach(AgeBand.allCases) { age in Button(age.label) { store.chooseAgeBand(age) } }
                            } label: {
                                Image(systemName: "person.crop.circle.fill")
                                    .font(.system(size: 26))
                                    .foregroundStyle(AppPalette.blue)
                            }
                        }
                        .padding(.top, 10)

                        HStack(spacing: 10) {
                            Image(systemName: "book.closed.fill").foregroundStyle(AppPalette.blue)
                            Text("\(ageStories.count) stories · tap a sentence to hear it")
                                .font(.system(size: 13, weight: .medium, design: .rounded))
                                .foregroundStyle(AppPalette.ink)
                        }
                        .padding(13)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(AppPalette.lavender.opacity(0.55), in: RoundedRectangle(cornerRadius: 16))

                        LazyVStack(spacing: 12) {
                            ForEach(ageStories.indices, id: \.self) { index in
                                let story = ageStories[index]
                                NavigationLink { StoryDetailView(story: story) } label: {
                                    StoryCard(story: story, number: index + 1, isComplete: store.completedStoryIDs.contains(story.id))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .padding(18)
                }
            }
            .navigationTitle("英语读物")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct StoryCard: View {
    let story: Story
    let number: Int
    let isComplete: Bool

    private var accent: Color { [AppPalette.mint, AppPalette.peach, AppPalette.lavender][(number - 1) % 3] }

    var body: some View {
        HStack(spacing: 15) {
            ZStack {
                RoundedRectangle(cornerRadius: 17).fill(accent).frame(width: 64, height: 68)
                Text(String(format: "%02d", number))
                    .font(.system(size: 19, weight: .bold, design: .rounded))
                    .foregroundStyle(AppPalette.ink)
            }
            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text(story.title)
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                        .foregroundStyle(AppPalette.ink)
                        .lineLimit(2)
                    if isComplete { Image(systemName: "checkmark.seal.fill").foregroundStyle(.green) }
                }
                Text(story.summary)
                    .font(.system(size: 12, design: .rounded))
                    .foregroundStyle(AppPalette.muted)
                    .lineLimit(2)
                Text(story.cefr.uppercased())
                    .font(.system(size: 10, weight: .bold, design: .rounded))
                    .foregroundStyle(AppPalette.blue)
            }
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundStyle(AppPalette.muted.opacity(0.7))
        }
        .padding(13)
        .background(.white, in: RoundedRectangle(cornerRadius: 21))
        .overlay(RoundedRectangle(cornerRadius: 21).stroke(AppPalette.ink.opacity(0.05), lineWidth: 1))
    }
}

struct StoryDetailView: View {
    @EnvironmentObject private var store: CompanionStore
    @EnvironmentObject private var speech: SpeechManager
    let story: Story
    @State private var question = ""
    @State private var answer: String?
    @State private var isAsking = false
    @State private var alertMessage: String?
    private let service = OllamaService()

    var body: some View {
        ZStack {
            AppPalette.canvas.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 9) {
                        Text(story.cefr.uppercased())
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(AppPalette.blue)
                        Text(story.title)
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                            .foregroundStyle(AppPalette.ink)
                        Text(story.summary)
                            .font(.system(size: 16, design: .rounded))
                            .foregroundStyle(AppPalette.muted)
                        Button {
                            store.markStoryCompleted(story)
                        } label: {
                            Label(store.completedStoryIDs.contains(story.id) ? "已完成阅读" : "标记为已读",
                                  systemImage: store.completedStoryIDs.contains(story.id) ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                        }
                        .padding(.top, 3)
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.white, in: RoundedRectangle(cornerRadius: 22))

                    ForEach(story.paragraphs.indices, id: \.self) { index in
                        let paragraph = story.paragraphs[index]
                        VStack(alignment: .leading, spacing: 12) {
                            Text("PART \(index + 1)")
                                .font(.system(size: 11, weight: .bold, design: .rounded))
                                .foregroundStyle(AppPalette.blue)
                            ForEach(paragraph.sentences) { sentence in
                                Button { speech.speak(sentence.text) } label: {
                                    HStack(alignment: .top, spacing: 10) {
                                        Image(systemName: "speaker.wave.2.fill")
                                            .font(.system(size: 12))
                                            .foregroundStyle(AppPalette.blue)
                                            .padding(.top, 5)
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(sentence.text)
                                                .font(.system(size: 16, design: .rounded))
                                                .foregroundStyle(AppPalette.ink)
                                                .multilineTextAlignment(.leading)
                                            Text(sentence.chineseHint)
                                                .font(.system(size: 12, design: .rounded))
                                                .foregroundStyle(AppPalette.muted)
                                                .multilineTextAlignment(.leading)
                                        }
                                        Spacer(minLength: 0)
                                    }
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(17)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(.white, in: RoundedRectangle(cornerRadius: 21))
                    }

                    vocabularySection
                    questionsSection
                    tutorSection
                }
                .padding(16)
            }
        }
        .navigationTitle("阅读")
        .navigationBarTitleDisplayMode(.inline)
        .alert("阅读助手", isPresented: Binding(
            get: { alertMessage != nil },
            set: { if !$0 { alertMessage = nil } }
        )) {
            Button("确定", role: .cancel) { alertMessage = nil }
        } message: { Text(alertMessage ?? "") }
    }

    private var vocabularySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("新单词").font(.system(size: 19, weight: .bold, design: .rounded)).foregroundStyle(AppPalette.ink)
            ForEach(story.vocabulary) { item in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(item.word).font(.system(size: 15, weight: .bold, design: .rounded)).foregroundStyle(AppPalette.blue)
                        Text(item.chinese).font(.system(size: 12, design: .rounded)).foregroundStyle(AppPalette.muted)
                    }
                    Text(item.definition).font(.system(size: 13, design: .rounded)).foregroundStyle(AppPalette.ink)
                    Text(item.example).font(.system(size: 12, design: .rounded)).italic().foregroundStyle(AppPalette.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(AppPalette.canvas, in: RoundedRectangle(cornerRadius: 14))
            }
        }
        .padding(17)
        .background(.white, in: RoundedRectangle(cornerRadius: 21))
    }

    private var questionsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("想一想").font(.system(size: 19, weight: .bold, design: .rounded)).foregroundStyle(AppPalette.ink)
            ForEach(story.questions.indices, id: \.self) { index in
                let item = story.questions[index]
                HStack(alignment: .top, spacing: 10) {
                    Text("\(index + 1)").font(.system(size: 12, weight: .bold, design: .rounded)).foregroundStyle(AppPalette.blue)
                    Text(item.prompt).font(.system(size: 14, design: .rounded)).foregroundStyle(AppPalette.ink)
                }
            }
        }
        .padding(17)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: RoundedRectangle(cornerRadius: 21))
    }

    private var tutorSection: some View {
        VStack(alignment: .leading, spacing: 11) {
            Label("问问故事内容", systemImage: "sparkles")
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(AppPalette.ink)
            HStack {
                TextField("你想了解什么？", text: $question, axis: .vertical)
                    .lineLimit(1...3)
                    .textInputAutocapitalization(.sentences)
                Button(action: askTutor) {
                    if isAsking { ProgressView().tint(.white) }
                    else { Image(systemName: "arrow.up").font(.system(size: 15, weight: .bold)).foregroundStyle(.white) }
                }
                .frame(width: 38, height: 38)
                .background(AppPalette.blue, in: Circle())
                .disabled(isAsking || question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(AppPalette.canvas, in: RoundedRectangle(cornerRadius: 17))
            if let answer {
                Text(answer)
                    .font(.system(size: 15, design: .rounded))
                    .foregroundStyle(AppPalette.ink)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppPalette.mint.opacity(0.5), in: RoundedRectangle(cornerRadius: 15))
                    .onTapGesture { speech.speak(answer) }
            }
            Text("回答会根据当前故事内容生成。")
                .font(.system(size: 11, design: .rounded))
                .foregroundStyle(AppPalette.muted)
        }
        .padding(17)
        .background(.white, in: RoundedRectangle(cornerRadius: 21))
    }

    private func askTutor() {
        let text = question.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        guard !ChildInputPolicy.isBlocked(text) else { answer = ChildInputPolicy.redirect; question = ""; return }
        question = ""
        isAsking = true
        Task {
            defer { isAsking = false }
            do {
                answer = try await service.tutor(
                    story: story,
                    question: text,
                    connection: OllamaConnection(baseURL: store.baseURL, model: store.modelName, token: store.secureToken)
                )
            } catch {
                alertMessage = error.localizedDescription
            }
        }
    }
}
