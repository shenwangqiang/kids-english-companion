package com.kidsenglish.companion.tutor

import com.kidsenglish.companion.domain.PassageRetriever
import com.kidsenglish.companion.model.AgeBand

class PromptBuilder(private val passageRetriever: PassageRetriever) {
    fun messages(request: TutorRequest): List<ChatMessage> {
        val wordLimit = when (request.ageBand) {
            AgeBand.AGE_6_8 -> 45
            AgeBand.AGE_9_10 -> 80
            AgeBand.AGE_11_12 -> 120
        }
        val passages = passageRetriever.retrieve(
            story = request.story,
            query = request.childMessage,
            currentParagraphId = request.currentParagraphId,
        )
        val evidence = passages.joinToString("\n") { paragraph ->
            "[${paragraph.id}] " + paragraph.sentences.joinToString(" ") { it.text }
        }
        val system = ChatMessage(
            ChatRole.SYSTEM,
            "You are a careful English reading tutor for ages ${request.ageBand.wireValue}. " +
                "Use only the supplied story evidence. If the evidence does not answer the question, say so. " +
                "Keep the reply within $wordLimit words. Never request personal information. " +
                "Keep language age-appropriate and redirect unrelated requests to the story.",
        )
        val evidenceMessage = ChatMessage(ChatRole.SYSTEM, "Story evidence:\n$evidence")
        val current = ChatMessage(
            ChatRole.USER,
            "Story: ${request.story.title}\nEvidence:\n$evidence\nChild's message: ${request.childMessage}",
        )
        return listOf(system, evidenceMessage) + request.history.takeLast(6) + current
    }
}
