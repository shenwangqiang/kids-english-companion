package com.kidsenglish.companion.domain

import com.kidsenglish.companion.model.Story
import com.kidsenglish.companion.model.StoryParagraph
import java.util.Locale

class PassageRetriever {
    fun retrieve(
        story: Story,
        query: String,
        currentParagraphId: String?,
        limit: Int = 3,
    ): List<StoryParagraph> {
        if (limit <= 0) return emptyList()
        val current = story.paragraphs.firstOrNull { it.id == currentParagraphId }
        val queryTokens = normalizeTokens(query).filterNot(STOP_WORDS::contains).distinct()
        val ranked = story.paragraphs
            .filterNot { it.id == currentParagraphId }
            .mapIndexed { index, paragraph ->
                val paragraphTokens = normalizeTokens(paragraph.sentences.joinToString(" ") { it.text }).toSet()
                val score = queryTokens.withIndex().sumOf { (tokenIndex, token) ->
                    if (token in paragraphTokens) tokenIndex + 1 else 0
                }
                RankedParagraph(paragraph, score, index)
            }
            .sortedWith(compareByDescending<RankedParagraph> { it.score }.thenBy { it.index })
            .map { it.paragraph }

        return buildList {
            current?.let(::add)
            addAll(ranked.take(limit - size))
        }
    }

    private data class RankedParagraph(val paragraph: StoryParagraph, val score: Int, val index: Int)

    companion object {
        private val STOP_WORDS = setOf("a", "an", "the", "is", "are", "was", "were", "do", "does", "did", "where", "what", "who", "why", "how", "to", "in", "on", "of")

        internal fun normalizeTokens(text: String): List<String> = text
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9']+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map { token ->
                when {
                    token.endsWith("ies") && token.length > 4 -> token.dropLast(3) + "y"
                    token.endsWith("s") && token.length > 3 -> token.dropLast(1)
                    else -> token
                }
            }
    }
}
