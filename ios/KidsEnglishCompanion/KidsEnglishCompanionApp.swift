import SwiftUI

@main
struct KidsEnglishCompanionApp: App {
    @StateObject private var store = CompanionStore()
    @StateObject private var speech = SpeechManager()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(speech)
                .preferredColorScheme(.light)
        }
    }
}

enum AppPalette {
    static let ink = Color(red: 0.12, green: 0.18, blue: 0.28)
    static let muted = Color(red: 0.42, green: 0.47, blue: 0.55)
    static let canvas = Color(red: 0.97, green: 0.96, blue: 0.93)
    static let card = Color.white
    static let blue = Color(red: 0.22, green: 0.40, blue: 0.76)
    static let mint = Color(red: 0.78, green: 0.91, blue: 0.82)
    static let peach = Color(red: 0.98, green: 0.82, blue: 0.69)
    static let lavender = Color(red: 0.85, green: 0.83, blue: 0.96)
}

struct RootView: View {
    @EnvironmentObject private var store: CompanionStore

    var body: some View {
        Group {
            if store.selectedAgeBand == nil {
                AgeSelectionView()
            } else {
                MainTabView()
            }
        }
        .tint(AppPalette.blue)
    }
}

struct AgeSelectionView: View {
    @EnvironmentObject private var store: CompanionStore

    var body: some View {
        ZStack {
            AppPalette.canvas.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack {
                        Image(systemName: "sparkles")
                            .font(.system(size: 23, weight: .bold))
                            .foregroundStyle(AppPalette.blue)
                        Text("英语伙伴")
                            .font(.system(size: 20, weight: .bold, design: .rounded))
                            .foregroundStyle(AppPalette.ink)
                    }
                    .padding(.top, 36)

                    VStack(alignment: .leading, spacing: 12) {
                        Text("一起探索英语！")
                            .font(.system(size: 36, weight: .bold, design: .rounded))
                            .foregroundStyle(AppPalette.ink)
                        Text("选择年龄段，让对话和读物更适合孩子。")
                            .font(.system(size: 17, design: .rounded))
                            .foregroundStyle(AppPalette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(spacing: 12) {
                        ForEach(AgeBand.allCases.indices, id: \.self) { index in
                            let age = AgeBand.allCases[index]
                            Button { store.chooseAgeBand(age) } label: {
                                HStack(spacing: 16) {
                                    Text(["🌱", "🚀", "🌟"][index])
                                        .font(.system(size: 31))
                                        .frame(width: 54, height: 54)
                                        .background([AppPalette.mint, AppPalette.peach, AppPalette.lavender][index], in: RoundedRectangle(cornerRadius: 17))
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(age.label)
                                            .font(.system(size: 18, weight: .bold, design: .rounded))
                                        Text(["简单词汇和趣味故事", "轻松聊天，学习新词", "表达想法，练习自信交流"][index])
                                            .font(.system(size: 13, design: .rounded))
                                            .foregroundStyle(AppPalette.muted)
                                    }
                                    Spacer()
                                    Image(systemName: "arrow.right")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(AppPalette.blue)
                                }
                                .foregroundStyle(AppPalette.ink)
                                .padding(14)
                                .background(.white, in: RoundedRectangle(cornerRadius: 22))
                                .overlay(RoundedRectangle(cornerRadius: 22).stroke(AppPalette.ink.opacity(0.06), lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 36)
            }
        }
    }
}

struct MainTabView: View {
    @EnvironmentObject private var speech: SpeechManager

    var body: some View {
        TabView {
            ConversationView()
                .tabItem { Label("对话", systemImage: "bubble.left.and.bubble.right.fill") }
            StoryLibraryView()
                .tabItem { Label("读物", systemImage: "books.vertical.fill") }
            HistoryView()
                .tabItem { Label("历史", systemImage: "clock.arrow.circlepath") }
            SettingsView()
                .tabItem { Label("设置", systemImage: "slider.horizontal.3") }
        }
        .onDisappear { speech.stop() }
    }
}
