package com.kidsenglish.companion.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDatabaseTest {
    private lateinit var database: ProgressDatabase
    private lateinit var repository: ProgressRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java).allowMainThreadQueries().build()
        repository = ProgressRepository(database.progressDao())
    }

    @After fun tearDown() = database.close()

    @Test fun keepsCompletionAttemptCountAndBestScore() = runTest {
        repository.markStoryOpened("story-1")
        repository.markStoryCompleted("story-1")
        repository.recordSpeakingAttempt("story-1", 64)
        repository.recordSpeakingAttempt("story-1", 88)

        val progress = repository.observeAll().first().single()
        assertNotNull(progress.completedAt)
        assertEquals(2, progress.speakingAttempts)
        assertEquals(88, progress.bestSpeakingScore)
    }
}
