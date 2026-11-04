package com.kidsenglish.companion.content

import android.content.Context
import com.kidsenglish.companion.model.AgeBand
import com.kidsenglish.companion.model.Story
import kotlinx.serialization.json.Json

interface StoryRepository {
    fun stories(ageBand: AgeBand): List<Story>
    fun story(id: String): Story?
}

class JsonStoryRepository(
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val loadText: (String) -> String,
) : StoryRepository {
    private val catalog: Map<AgeBand, List<Story>> by lazy {
        AgeBand.entries.associateWith { band ->
            val fileName = "age_${band.wireValue.replace('-', '_')}.json"
            json.decodeFromString<List<Story>>(loadText(fileName)).also { stories ->
                require(stories.all { it.ageBand == band.wireValue }) {
                    "Story age band does not match $fileName"
                }
            }
        }
    }

    override fun stories(ageBand: AgeBand): List<Story> = catalog.getValue(ageBand)

    override fun story(id: String): Story? = catalog.values.flatten().firstOrNull { it.id == id }
}

class AssetStoryRepository(context: Context) : StoryRepository by JsonStoryRepository(
    loadText = { name -> context.assets.open("stories/$name").bufferedReader().use { it.readText() } },
)
