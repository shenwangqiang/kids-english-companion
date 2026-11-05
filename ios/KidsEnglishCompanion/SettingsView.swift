import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: CompanionStore
    @EnvironmentObject private var speech: SpeechManager
    @State private var tokenDraft = ""
    @State private var models: [String] = []
    @State private var isRefreshing = false
    @State private var isTesting = false
    @State private var statusMessage: String?
    private let service = OllamaService()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("年龄段", selection: Binding(
                        get: { store.selectedAgeBand ?? .sixToEight },
                        set: { store.chooseAgeBand($0) }
                    )) {
                        ForEach(AgeBand.allCases) { age in Text(age.label).tag(age) }
                    }
                } header: { Text("学习者") }

                Section {
                    TextField("http://Mac.local:11434", text: $store.baseURL)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                        Text("模拟器可填 http://127.0.0.1:11434。真机请填写 Mac 的局域网地址或以 .local 结尾的主机名。")
                        .font(.footnote)
                        .foregroundStyle(AppPalette.muted)

                    SecureField("Bearer Token（可选）", text: $tokenDraft)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("保存服务设置") {
                        store.setToken(tokenDraft)
                        statusMessage = "服务设置已保存在本机。"
                    }
                    .fontWeight(.semibold)

                    HStack {
                            TextField("模型名称", text: $store.modelName)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        if !models.isEmpty {
                            Menu {
                                ForEach(models, id: \.self) { model in
                                    Button(model) { store.modelName = model }
                                }
                            } label: {
                                Image(systemName: "chevron.down.circle.fill")
                                    .font(.system(size: 21))
                            }
                            .accessibilityLabel("选择已安装模型")
                        }
                    }

                    HStack(spacing: 12) {
                        Button(action: refreshModels) {
                            if isRefreshing { ProgressView() } else { Label("刷新模型", systemImage: "arrow.clockwise") }
                        }
                        .disabled(isRefreshing)
                        Spacer()
                        Button(action: testConnection) {
                            if isTesting { ProgressView() } else { Label("测试连接", systemImage: "checkmark.circle") }
                        }
                        .disabled(isTesting)
                    }
                } header: { Text("Ollama 服务") } footer: {
                    Text("远端服务请使用 HTTPS。HTTP 仅支持本机或私有网络地址。Token 使用 iOS 钥匙串保存。")
                }

                Section {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("朗读语速")
                            Spacer()
                            Text(String(format: "%.2f", store.speechRate)).foregroundStyle(AppPalette.muted)
                        }
                        Slider(value: $store.speechRate, in: 0.35...0.58, step: 0.01)
                            .onChange(of: store.speechRate) { _, value in speech.setSpeechRate(value) }
                    }
                    Label("英语音色：iPhone 系统语音", systemImage: "waveform")
                        .foregroundStyle(AppPalette.ink)
                    Text("iOS 使用系统朗读服务和设备上已安装的英语音色。")
                        .font(.footnote)
                        .foregroundStyle(AppPalette.muted)
                } header: { Text("语音") }

                Section("隐私") {
                    Label("聊天记录保存在本机", systemImage: "iphone")
                    Text("对话时，最近聊天内容、阶段总结和当前消息会发送至你配置的 Ollama 服务。语音由 Apple 语音识别服务处理。")
                        .font(.footnote)
                        .foregroundStyle(AppPalette.muted)
                }

                Section {
                    Text("英语伙伴 · iOS 原生版 · MIT 许可证")
                        .font(.footnote)
                        .foregroundStyle(AppPalette.muted)
                }
            }
            .navigationTitle("设置")
            .onAppear {
                tokenDraft = store.secureToken
                speech.setSpeechRate(store.speechRate)
            }
            .alert("Ollama", isPresented: Binding(
                get: { statusMessage != nil },
                set: { if !$0 { statusMessage = nil } }
            )) {
                Button("OK", role: .cancel) { statusMessage = nil }
            } message: { Text(statusMessage ?? "") }
        }
    }

    private func refreshModels() {
        isRefreshing = true
        Task {
            defer { isRefreshing = false }
            do {
                models = try await service.availableModels(connection: connection)
                if models.isEmpty { statusMessage = "未发现已安装的模型。" }
                else if !models.contains(store.modelName) { store.modelName = models[0] }
            } catch {
                statusMessage = error.localizedDescription
            }
        }
    }

    private func testConnection() {
        isTesting = true
        Task {
            defer { isTesting = false }
            do {
                try await service.testConnection(connection: connection)
                statusMessage = "连接成功，模型已正常回复。"
            } catch {
                statusMessage = error.localizedDescription
            }
        }
    }

    private var connection: OllamaConnection {
        OllamaConnection(baseURL: store.baseURL, model: store.modelName, token: tokenDraft.trimmingCharacters(in: .whitespacesAndNewlines))
    }
}
