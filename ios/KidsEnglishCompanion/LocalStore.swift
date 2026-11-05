import Foundation
import Security
import SwiftUI

@MainActor
final class CompanionStore: ObservableObject {
    @Published private(set) var sessions: [ChatSession] = []
    @Published private(set) var selectedAgeBand: AgeBand?
    @Published private(set) var selectedSessionID: UUID?
    @Published private(set) var completedStoryIDs: Set<String>
    @Published var baseURL: String { didSet { defaults.set(baseURL, forKey: Key.baseURL) } }
    @Published var modelName: String { didSet { defaults.set(modelName, forKey: Key.modelName) } }
    @Published var speechRate: Double { didSet { defaults.set(speechRate, forKey: Key.speechRate) } }
    @Published private(set) var secureToken: String

    private let defaults = UserDefaults.standard
    private let fileURL: URL

    var currentSession: ChatSession? {
        guard let selectedSessionID else { return nil }
        return sessions.first { $0.id == selectedSessionID }
    }

    func session(id: UUID) -> ChatSession? { sessions.first { $0.id == id } }

    init() {
        baseURL = UserDefaults.standard.string(forKey: Key.baseURL) ?? "http://127.0.0.1:11434"
        modelName = UserDefaults.standard.string(forKey: Key.modelName) ?? "qwen3.5:4b-mlx"
        speechRate = UserDefaults.standard.object(forKey: Key.speechRate) as? Double ?? 0.48
        selectedAgeBand = UserDefaults.standard.string(forKey: Key.ageBand).flatMap(AgeBand.init(rawValue:))
        completedStoryIDs = Set(UserDefaults.standard.stringArray(forKey: Key.completedStories) ?? [])
        secureToken = SecureTokenStore.read() ?? ""

        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("KidsEnglishCompanion", isDirectory: true)
        fileURL = directory.appendingPathComponent("sessions.json")
        loadSessions()
        if let selectedAgeBand {
            selectedSessionID = defaults.string(forKey: activeSessionKey(selectedAgeBand)).flatMap(UUID.init(uuidString:))
            if currentSession == nil { selectedSessionID = nil }
            if selectedSessionID == nil { createSession(topic: .general) }
        }
    }

    func chooseAgeBand(_ ageBand: AgeBand) {
        selectedAgeBand = ageBand
        defaults.set(ageBand.rawValue, forKey: Key.ageBand)
        selectedSessionID = defaults.string(forKey: activeSessionKey(ageBand)).flatMap(UUID.init(uuidString:))
        if currentSession == nil { createSession(topic: .general) }
    }

    @discardableResult
    func createSession(topic: ConversationTopic) -> UUID {
        let ageBand = selectedAgeBand ?? .sixToEight
        let session = ChatSession(ageBand: ageBand, topic: topic, title: topic.title)
        sessions.append(session)
        sessions.sort { $0.updatedAt > $1.updatedAt }
        selectedAgeBand = ageBand
        selectedSessionID = session.id
        defaults.set(ageBand.rawValue, forKey: Key.ageBand)
        defaults.set(session.id.uuidString, forKey: activeSessionKey(ageBand))
        persistSessions()
        return session.id
    }

    func selectSession(_ id: UUID) {
        guard let session = sessions.first(where: { $0.id == id }) else { return }
        selectedAgeBand = session.ageBand
        selectedSessionID = session.id
        defaults.set(session.ageBand.rawValue, forKey: Key.ageBand)
        defaults.set(session.id.uuidString, forKey: activeSessionKey(session.ageBand))
    }

    func setToken(_ value: String) {
        secureToken = value.trimmingCharacters(in: .whitespacesAndNewlines)
        SecureTokenStore.write(secureToken)
    }

    func markStoryCompleted(_ story: Story) {
        completedStoryIDs.insert(story.id)
        defaults.set(Array(completedStoryIDs), forKey: Key.completedStories)
    }

    func appendUserMessage(_ text: String, to sessionID: UUID? = nil) {
        updateSession(id: sessionID ?? selectedSessionID) { session in
            session.messages.append(ChatMessage(role: .user, content: text))
            if session.messages.filter({ $0.role == .user }).count == 1 {
                session.title = String(text.prefix(36))
            }
        }
    }

    func appendAssistantMessage(_ outcome: ConversationOutcome, to sessionID: UUID? = nil) {
        updateSession(id: sessionID ?? selectedSessionID) { session in
            session.messages.append(ChatMessage(role: .assistant, content: outcome.reply))
            session.memory = ConversationMemory(
                summary: String(outcome.conversationSummary.prefix(1_500)),
                rememberedFacts: Array(outcome.rememberedFacts.map { String($0.prefix(300)) }.prefix(20)),
                nextDirection: String(outcome.nextDirection.prefix(500))
            )
        }
    }

    func appendTutorMessage(_ text: String, to sessionID: UUID? = nil) {
        updateSession(id: sessionID ?? selectedSessionID) { $0.messages.append(ChatMessage(role: .assistant, content: text)) }
    }

    func deleteSession(_ id: UUID) {
        let wasActive = selectedSessionID == id
        sessions.removeAll { $0.id == id }
        if wasActive {
            selectedSessionID = nil
            if let selectedAgeBand,
               let next = sessions.first(where: { $0.ageBand == selectedAgeBand }) {
                selectSession(next.id)
            } else {
                createSession(topic: .general)
            }
        }
        persistSessions()
    }

    func sessions(for ageBand: AgeBand?) -> [ChatSession] {
        sessions.filter { ageBand == nil || $0.ageBand == ageBand }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    private func updateSession(id: UUID?, _ mutation: (inout ChatSession) -> Void) {
        guard let id,
              let index = sessions.firstIndex(where: { $0.id == id }) else { return }
        mutation(&sessions[index])
        sessions[index].updatedAt = Date()
        sessions.sort { $0.updatedAt > $1.updatedAt }
        persistSessions()
    }

    private func activeSessionKey(_ ageBand: AgeBand) -> String { "active-session-\(ageBand.rawValue)" }

    private func loadSessions() {
        guard let data = try? Data(contentsOf: fileURL),
              let decoded = try? JSONDecoder().decode([ChatSession].self, from: data) else { return }
        sessions = decoded.sorted { $0.updatedAt > $1.updatedAt }
    }

    private func persistSessions() {
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            let data = try JSONEncoder().encode(sessions)
            try data.write(to: fileURL, options: .atomic)
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            var directory = fileURL.deletingLastPathComponent()
            try? directory.setResourceValues(values)
        } catch {
            assertionFailure("Could not save local conversations: \(error.localizedDescription)")
        }
    }

    private enum Key {
        static let ageBand = "companion.ageBand"
        static let baseURL = "companion.baseURL"
        static let modelName = "companion.modelName"
        static let speechRate = "companion.speechRate"
        static let completedStories = "companion.completedStories"
    }
}

enum SecureTokenStore {
    private static let service = Bundle.main.bundleIdentifier ?? "KidsEnglishCompanion"
    private static let account = "ollama-bearer-token"

    static func read() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func write(_ token: String) {
        let key: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        if token.isEmpty {
            SecItemDelete(key as CFDictionary)
            return
        }
        let data = Data(token.utf8)
        let update = [kSecValueData as String: data]
        let status = SecItemUpdate(key as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var insert = key
            insert[kSecValueData as String] = data
            insert[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            SecItemAdd(insert as CFDictionary, nil)
        }
    }
}
