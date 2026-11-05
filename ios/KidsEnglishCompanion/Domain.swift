import Foundation

enum AgeBand: String, CaseIterable, Codable, Identifiable {
    case sixToEight = "6-8"
    case nineToTen = "9-10"
    case elevenToTwelve = "11-12"

    var id: String { rawValue }
    var label: String {
        switch self {
        case .sixToEight: return "6–8 岁"
        case .nineToTen: return "9–10 岁"
        case .elevenToTwelve: return "11–12 岁"
        }
    }
    var replyWordLimit: Int {
        switch self {
        case .sixToEight: return 30
        case .nineToTen: return 50
        case .elevenToTwelve: return 75
        }
    }
    var tokenLimit: Int {
        switch self {
        case .sixToEight: return 180
        case .nineToTen: return 240
        case .elevenToTwelve: return 300
        }
    }
}

struct ConversationTopic: Identifiable, Codable, Hashable {
    let id: String
    let title: String
    var branded = false

    static let general = ConversationTopic(id: "general", title: "General Conversation")
    static let predefined: [ConversationTopic] = [
        ConversationTopic(id: "how-to-train-your-dragon", title: "How to Train Your Dragon", branded: true),
        ConversationTopic(id: "harry-potter", title: "Harry Potter", branded: true),
        ConversationTopic(id: "space", title: "Space"),
        ConversationTopic(id: "dinosaurs", title: "Dinosaurs"),
        ConversationTopic(id: "animals", title: "Animals"),
        ConversationTopic(id: "school-life", title: "School Life")
    ]

    static func custom(_ raw: String) -> ConversationTopic? {
        let title = raw.split(whereSeparator: \.isWhitespace).joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !title.isEmpty, title.count <= 60 else { return nil }
        let slug = title.lowercased().unicodeScalars.map { CharacterSet.alphanumerics.contains($0) ? String($0) : "-" }
            .joined().split(separator: "-").joined(separator: "-")
        return ConversationTopic(id: "custom-\(slug)", title: title)
    }
}

enum MessageRole: String, Codable {
    case user
    case assistant
}

struct ChatMessage: Identifiable, Codable, Equatable {
    var id = UUID()
    var role: MessageRole
    var content: String
    var createdAt = Date()
}

struct ConversationMemory: Codable, Equatable {
    var summary = ""
    var rememberedFacts: [String] = []
    var nextDirection = ""
}

struct ConversationOutcome: Codable {
    var reply: String
    var conversationSummary: String
    var nextDirection: String
    var rememberedFacts: [String]
}

struct ChatSession: Identifiable, Codable, Equatable {
    var id = UUID()
    var ageBand: AgeBand
    var topic: ConversationTopic
    var title: String
    var createdAt = Date()
    var updatedAt = Date()
    var messages: [ChatMessage] = []
    var memory = ConversationMemory()
}

struct StorySentence: Codable, Identifiable {
    let id: String
    let text: String
    let chineseHint: String
}

struct StoryParagraph: Codable, Identifiable {
    let id: String
    let sentences: [StorySentence]
}

struct VocabularyItem: Codable, Identifiable {
    var id: String { word }
    let word: String
    let definition: String
    let chinese: String
    let example: String
}

struct ComprehensionQuestion: Codable, Identifiable {
    let id: String
    let prompt: String
    let answerConcepts: [String]
}

struct Story: Codable, Identifiable {
    let id: String
    let title: String
    let ageBand: String
    let cefr: String
    let summary: String
    let paragraphs: [StoryParagraph]
    let vocabulary: [VocabularyItem]
    let questions: [ComprehensionQuestion]
}

enum ChildInputPolicy {
    static let redirect = "Let's stay with our story and learning activity."
    private static let blockedPatterns = [
        #"\b(home )?address\b"#,
        #"\b(phone|mobile|contact) number\b"#,
        #"\b(my|your) school\b"#,
        #"\b(send|share|show).{0,20}(photo|picture|secret)\b"#,
        #"\b(self[- ]?harm|suicide|kill myself)\b"#,
        #"\b(make|build).{0,20}(bomb|weapon)\b"#,
        #"\b(nude|sexual|porn)\b"#
    ]

    static func isBlocked(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty || trimmed.count > 500 || blockedPatterns.contains { trimmed.range(of: $0, options: [.regularExpression, .caseInsensitive]) != nil }
    }
}
