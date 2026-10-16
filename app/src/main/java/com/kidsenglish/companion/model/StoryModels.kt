package com.kidsenglish.companion.model

import kotlinx.serialization.Serializable

enum class AgeBand(val wireValue: String, val label: String) {
    AGE_6_8("6-8", "Ages 6–8"),
    AGE_9_10("9-10", "Ages 9–10"),
    AGE_11_12("11-12", "Ages 11–12"),
}

@Serializable
data class StorySentence(val id: String, val text: String, val chineseHint: String)

@Serializable
data class StoryParagraph(val id: String, val sentences: List<StorySentence>)

@Serializable
data class VocabularyItem(
    val word: String,
    val definition: String,
    val chinese: String,
    val example: String,
)

@Serializable
data class ComprehensionQuestion(
    val id: String,
    val prompt: String,
    val answerConcepts: List<String>,
)

@Serializable
data class Story(
    val id: String,
    val title: String,
    val ageBand: String,
    val cefr: String,
    val summary: String,
    val paragraphs: List<StoryParagraph>,
    val vocabulary: List<VocabularyItem>,
    val questions: List<ComprehensionQuestion>,
    val attribution: String = "Original content for Kids English Companion",
)
