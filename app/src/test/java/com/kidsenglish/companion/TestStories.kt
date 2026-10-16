package com.kidsenglish.companion

import com.kidsenglish.companion.model.ComprehensionQuestion
import com.kidsenglish.companion.model.Story
import com.kidsenglish.companion.model.StoryParagraph
import com.kidsenglish.companion.model.StorySentence

fun sampleStory() = Story(
    id = "sample",
    title = "Mia and the Red Kite",
    ageBand = "6-8",
    cefr = "A1",
    summary = "Mia finds her kite.",
    paragraphs = listOf(
        StoryParagraph("p1", listOf(StorySentence("s1", "Mia flies a red kite in the park.", "米娅在公园放红风筝。"))),
        StoryParagraph("p2", listOf(StorySentence("s2", "The kite lands in a tall tree.", "风筝落在高树上。"))),
        StoryParagraph("p3", listOf(StorySentence("s3", "Ben helps Mia reach the string.", "本帮助米娅够到风筝线。"))),
    ),
    vocabulary = emptyList(),
    questions = listOf(ComprehensionQuestion("q1", "Where is the kite?", listOf("tree"))),
)
