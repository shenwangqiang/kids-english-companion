import Foundation

struct OllamaConnection: Sendable {
    var baseURL: String
    var model: String
    var token: String
}

enum OllamaError: LocalizedError {
    case invalidURL
    case insecureEndpoint
    case authenticationRequired
    case serverUnavailable
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .invalidURL: "请输入有效的 Ollama 服务地址。"
        case .insecureEndpoint: "远程服务请使用 HTTPS；HTTP 仅支持本机或私有网络地址。"
        case .authenticationRequired: "Ollama 拒绝了访问令牌，请检查设置中的 Token。"
        case .serverUnavailable: "对话服务当前不可用，请检查服务地址和网络连接。"
        case .invalidResponse: "模型返回内容无法读取，请重试。"
        }
    }
}

actor OllamaService {
    func testConnection(connection: OllamaConnection) async throws {
        let content = try await chat(
            messages: [OllamaMessage(role: "user", content: "Reply only with OK.")],
            maxTokens: 32,
            connection: connection
        )
        guard !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw OllamaError.invalidResponse }
    }

    func availableModels(connection: OllamaConnection) async throws -> [String] {
        let url = try endpoint(baseURL: connection.baseURL, path: "/api/tags")
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.httpMethod = "GET"
        addAuthorization(to: &request, token: connection.token)
        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response)
        let result = try JSONDecoder().decode(TagsResponse.self, from: data)
        return Array(Set(result.models.map(\.name))).sorted { lhs, rhs in
            modelRank(lhs) == modelRank(rhs) ? lhs < rhs : modelRank(lhs) < modelRank(rhs)
        }
    }

    func converse(session: ChatSession, opening: Bool, connection: OllamaConnection) async throws -> ConversationOutcome {
        let latest = session.messages.last(where: { $0.role == .user })?.content ?? ""
        let prompt = conversationPrompt(session: session, childMessage: latest, opening: opening)
        let first = try await chat(messages: prompt, maxTokens: session.ageBand.tokenLimit, connection: connection)
        if let outcome = parseOutcome(first) { return outcome }

        let repair: [OllamaMessage] = prompt + [
            OllamaMessage(role: "assistant", content: String(first.prefix(2_000))),
            OllamaMessage(role: "user", content: "The previous response was invalid. Return JSON only with reply, conversationSummary, nextDirection, and rememberedFacts. Do not use markdown.")
        ]
        guard let repaired = parseOutcome(try await chat(messages: repair, maxTokens: session.ageBand.tokenLimit, connection: connection)) else {
            throw OllamaError.invalidResponse
        }
        return repaired
    }

    func tutor(story: Story, question: String, connection: OllamaConnection) async throws -> String {
        let evidence = story.paragraphs.flatMap { paragraph in
            paragraph.sentences.map { "[\(paragraph.id)] \($0.text)" }
        }.joined(separator: "\n")
        let wordLimit = 45
        let messages = [
            OllamaMessage(role: "system", content: "You are a careful English reading tutor for a child. Use only the story evidence supplied. If the evidence does not answer the question, say so. Keep the answer under \(wordLimit) words. Never request personal information."),
            OllamaMessage(role: "user", content: "Story: \(story.title)\nStory evidence:\n\(evidence)\nChild's question: \(question)")
        ]
        return try await chat(messages: messages, maxTokens: 180, connection: connection).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func chat(messages: [OllamaMessage], maxTokens: Int, connection: OllamaConnection) async throws -> String {
        let url = try endpoint(baseURL: connection.baseURL, path: "/api/chat")
        var request = URLRequest(url: url, timeoutInterval: 90)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        addAuthorization(to: &request, token: connection.token)
        request.httpBody = try JSONEncoder().encode(ChatRequest(
            model: connection.model.isEmpty ? "qwen3.5:4b-mlx" : connection.model,
            messages: messages,
            options: ChatOptions(numPredict: maxTokens)
        ))

        let (data, response) = try await URLSession.shared.data(for: request)
        try validate(response)
        guard let content = try? JSONDecoder().decode(ChatResponse.self, from: data).message?.content,
              !content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw OllamaError.invalidResponse
        }
        return content
    }

    private func endpoint(baseURL: String, path: String) throws -> URL {
        let trimmed = baseURL.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let normalized = trimmed.hasSuffix("/v1") ? String(trimmed.dropLast(3)) : trimmed
        guard var components = URLComponents(string: normalized),
              let scheme = components.scheme?.lowercased(),
              let host = components.host?.lowercased(),
              !host.isEmpty,
              components.user == nil,
              components.password == nil,
              components.fragment == nil else { throw OllamaError.invalidURL }

        if scheme != "https" {
            guard scheme == "http", isPrivateHost(host) else { throw OllamaError.insecureEndpoint }
        }
        components.path = path
        components.query = nil
        guard let url = components.url else { throw OllamaError.invalidURL }
        return url
    }

    private func isPrivateHost(_ host: String) -> Bool {
        if host == "localhost" || host == "127.0.0.1" || host.hasSuffix(".local") { return true }
        let bytes = host.split(separator: ".").compactMap { UInt8($0) }
        guard bytes.count == 4 else { return false }
        return bytes[0] == 10 ||
            (bytes[0] == 172 && (16...31).contains(bytes[1])) ||
            (bytes[0] == 192 && bytes[1] == 168)
    }

    private func addAuthorization(to request: inout URLRequest, token: String) {
        guard !token.isEmpty else { return }
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    }

    private func validate(_ response: URLResponse) throws {
        guard let http = response as? HTTPURLResponse else { throw OllamaError.serverUnavailable }
        if http.statusCode == 401 || http.statusCode == 403 { throw OllamaError.authenticationRequired }
        guard (200..<300).contains(http.statusCode) else { throw OllamaError.serverUnavailable }
    }

    private func conversationPrompt(session: ChatSession, childMessage: String, opening: Bool) -> [OllamaMessage] {
        let topicRules = session.topic.branded
            ? "The model does not contain the book. Do not quote long passages, reconstruct chapters, invent uncertain details, or reveal spoilers."
            : ""
        let facts = session.memory.rememberedFacts.prefix(20).map { "- \($0.prefix(300))" }.joined(separator: "\n")
        let system = """
        You are a friendly English conversation coach for a child aged \(session.ageBand.rawValue).
        Use age-appropriate English and no more than \(session.ageBand.replyWordLimit) English words in the reply.
        Conversation topic: \(session.topic.title). Stay on topic unless the child clearly changes it.
        Briefly reflect the child's latest answer, then ask exactly one natural next question.
        Never ask for a real name, address, school, contact details, photos, location, or secrets.
        Do not invent facts about the child. Use saved memory only when relevant. \(topicRules)
        Saved conversation summary: \(session.memory.summary.isEmpty ? "None yet" : session.memory.summary.prefix(1_500))
        Remembered facts:
        \(facts.isEmpty ? "- None yet" : facts)
        Planned direction: \(session.memory.nextDirection.isEmpty ? "Continue a friendly age-appropriate conversation." : session.memory.nextDirection.prefix(500))
        Return JSON only, without markdown, with exactly these keys:
        reply (string), conversationSummary (string), nextDirection (string), rememberedFacts (array of strings).
        """
        let previous = session.messages.dropLast().suffix(8).map { message in
            OllamaMessage(role: message.role.rawValue, content: String(message.content.prefix(500)))
        }
        let current = opening
            ? "Open a friendly conversation about \(session.topic.title). Give a short welcome and ask exactly one question."
            : String(childMessage.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500))
        return [OllamaMessage(role: "system", content: system)] + previous + [OllamaMessage(role: "user", content: current)]
    }

    private func parseOutcome(_ raw: String) -> ConversationOutcome? {
        var candidate = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if candidate.hasPrefix("```") {
            candidate = candidate.replacingOccurrences(of: #"^```(?:json)?\s*|\s*```$"#, with: "", options: .regularExpression)
        }
        guard let data = candidate.data(using: .utf8),
              let outcome = try? JSONDecoder().decode(ConversationOutcome.self, from: data) else { return nil }
        let reply = String(outcome.reply.trimmingCharacters(in: .whitespacesAndNewlines).prefix(2_000))
        guard !reply.isEmpty else { return nil }
        return ConversationOutcome(
            reply: reply,
            conversationSummary: String(outcome.conversationSummary.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1_500)),
            nextDirection: String(outcome.nextDirection.trimmingCharacters(in: .whitespacesAndNewlines).prefix(500)),
            rememberedFacts: Array(outcome.rememberedFacts.map { String($0.trimmingCharacters(in: .whitespacesAndNewlines).prefix(300)) }.filter { !$0.isEmpty }.uniqued().prefix(20))
        )
    }

    private func modelRank(_ name: String) -> Int {
        let value = name.lowercased()
        if value.contains("smollm2:1.7b") { return 0 }
        if value.contains("qwen3:4b") { return 1 }
        if value.contains("qwen3.5:4b") { return 2 }
        if value.contains("qwen3.5:9b") { return 3 }
        return 10
    }
}

private extension Sequence where Element: Hashable {
    func uniqued() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}

private struct OllamaMessage: Codable {
    let role: String
    let content: String
}

private struct ChatRequest: Encodable {
    let model: String
    let messages: [OllamaMessage]
    let stream = false
    let think = false
    let keepAlive = "30m"
    let options: ChatOptions

    enum CodingKeys: String, CodingKey {
        case model, messages, stream, think, options
        case keepAlive = "keep_alive"
    }
}

private struct ChatOptions: Encodable {
    let temperature = 0.2
    let numPredict: Int
    enum CodingKeys: String, CodingKey {
        case temperature
        case numPredict = "num_predict"
    }
}

private struct ChatResponse: Decodable {
    struct Message: Decodable { let content: String? }
    let message: Message?
}

private struct TagsResponse: Decodable {
    struct Model: Decodable { let name: String }
    let models: [Model]
}
