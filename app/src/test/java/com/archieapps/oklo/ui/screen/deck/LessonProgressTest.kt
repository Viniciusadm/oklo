package com.archieapps.oklo.ui.screen.deck

import com.archieapps.oklo.data.repo.LessonJobState
import com.archieapps.oklo.data.repo.LessonJobTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonProgressTest {
    private val states = MutableStateFlow<Map<String, LessonJobState>>(emptyMap())

    private val tracker = object : LessonJobTracker {
        override suspend fun track(deckId: String, jobId: String) = Unit

        override fun jobState(deckId: String): Flow<LessonJobState?> = states.map { it[deckId] }

        override fun clear(deckId: String) = Unit
    }

    private suspend fun progress(deckId: String = "deck-1") = lessonProgress(tracker, deckId).first()

    @Test
    fun noJobIsIdle() = runTest {
        assertEquals(LessonProgress.Idle, progress())
    }

    @Test
    fun pollingIsGenerating() = runTest {
        states.value = mapOf("deck-1" to LessonJobState.Polling)
        assertEquals(LessonProgress.Generating, progress())
    }

    @Test
    fun otherDeckJobDoesNotLeak() = runTest {
        states.value = mapOf("deck-2" to LessonJobState.Polling)
        assertEquals(LessonProgress.Idle, progress())
    }

    @Test
    fun failureCarriesTheError() = runTest {
        states.value = mapOf("deck-1" to LessonJobState.Failed("deu ruim"))
        assertEquals(LessonProgress.Failed("deu ruim"), progress())
    }

    @Test
    fun doneIsIdleSoTheLessonShowsOnceSynced() = runTest {
        states.value = mapOf("deck-1" to LessonJobState.Done(emptyList()))
        assertEquals(LessonProgress.Idle, progress())
    }

    @Test
    fun delayedIsFailedWithWaitMessage() = runTest {
        states.value = mapOf("deck-1" to LessonJobState.Delayed)
        assertEquals(LessonProgress.Failed(LESSON_DELAYED), progress())
    }
}
