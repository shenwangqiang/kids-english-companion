package com.kidsenglish.companion.domain

import kotlin.math.roundToInt

data class SpeakingFeedback(
    val percent: Int,
    val recognizedText: String,
    val correctWords: List<String>,
    val missedWords: List<String>,
    val extraWords: List<String>,
)

object SpeakingScore {
    fun compare(target: String, recognized: String): SpeakingFeedback {
        val targetTokens = PassageRetriever.normalizeTokens(target)
        require(targetTokens.isNotEmpty()) { "Target sentence must not be empty" }
        val recognizedTokens = PassageRetriever.normalizeTokens(recognized)
        val aligned = longestCommonSubsequence(targetTokens, recognizedTokens)
        val correct = aligned.map { targetTokens[it.first] }
        val targetIndexes = aligned.map { it.first }.toSet()
        val recognizedIndexes = aligned.map { it.second }.toSet()
        return SpeakingFeedback(
            percent = (100.0 * correct.size / targetTokens.size).roundToInt().coerceIn(0, 100),
            recognizedText = recognized.trim(),
            correctWords = correct,
            missedWords = targetTokens.filterIndexed { index, _ -> index !in targetIndexes },
            extraWords = recognizedTokens.filterIndexed { index, _ -> index !in recognizedIndexes },
        )
    }

    private fun longestCommonSubsequence(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val lengths = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lengths[i][j] = if (a[i] == b[j]) 1 + lengths[i + 1][j + 1]
                else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val result = mutableListOf<Pair<Int, Int>>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { result += i to j; i++; j++ }
                lengths[i + 1][j] >= lengths[i][j + 1] -> i++
                else -> j++
            }
        }
        return result
    }
}
