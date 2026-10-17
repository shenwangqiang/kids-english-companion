package com.kidsenglish.companion.conversation

data class ConversationTopic(
    val id: String,
    val title: String,
    val branded: Boolean = false,
)

object ConversationTopicCatalog {
    val general = ConversationTopic("general", "General Conversation")

    val predefined = listOf(
        ConversationTopic("how-to-train-your-dragon", "How to Train Your Dragon", branded = true),
        ConversationTopic("harry-potter", "Harry Potter", branded = true),
        ConversationTopic("space", "Space"),
        ConversationTopic("dinosaurs", "Dinosaurs"),
        ConversationTopic("animals", "Animals"),
        ConversationTopic("school-life", "School Life"),
    )

    fun custom(raw: String): ConversationTopic? {
        val title = raw.trim().replace(Regex("\\s+"), " ")
        if (title.isBlank() || title.length > 60) return null
        val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        return ConversationTopic("custom-$slug", title)
    }

    fun resolve(id: String, title: String): ConversationTopic =
        (predefined + general).firstOrNull { it.id == id }
            ?: ConversationTopic(id = id, title = title)
}
