package com.archieapps.oklo.data.repo

import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.core.net.LessonLinkDto
import com.archieapps.oklo.data.db.LessonEntity
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonAiFinisherTest {
    private val events = mutableListOf<String>()
    private val dao = FakeLessonDao()
    private var applied: Triple<String, Map<String, String>, Set<String>?>? = null

    private fun TestScope.finisher(targets: Set<String> = setOf("page:intro", "anchor:clara")) = LessonAiFinisher(
        syncNow = {
            events += "sync"
            dao.upsert(lesson("lesson-1"))
            SyncReport.Done(pushed = 0, pulled = 1, applied = 1, rejected = 0, usn = 2)
        },
        lessons = dao,
        cards = LessonLinkWriter { deckId, links, keep ->
            events += "apply"
            applied = Triple(deckId, links, keep)
        },
        targetsOf = { source ->
            events += "targets:$source"
            targets
        },
        requestSync = { events += "requestSync" },
        scope = this,
        compileDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun lesson(id: String, deckId: String = "d1") = LessonEntity(
        id = id, deckId = deckId, source = "# Nova", version = 2, ribaltaVersion = "0.3.0", origin = "ai",
        createdAt = 1, updatedAt = 2, deletedAt = null, dirty = false,
    )

    private fun body(clear: Boolean = true, lessonId: String = "lesson-1") = LessonJobBody(
        "done",
        lessonId = lessonId,
        links = listOf(LessonLinkDto("c1", "anchor:clara")),
        clearTargets = clear,
    )

    @Test
    fun syncsDownThenAppliesLinksWithTheNewLessonTargetsThenRequestsSync() = runTest {
        finisher().complete("d1", body())
        assertEquals(listOf("sync", "targets:# Nova", "apply", "requestSync"), events)
        assertEquals(Triple("d1", mapOf("c1" to "anchor:clara"), setOf("page:intro", "anchor:clara")), applied)
    }

    @Test
    fun withoutClearTargetsOnlyLinksAreApplied() = runTest {
        finisher().complete("d1", body(clear = false))
        assertNull(applied!!.third)
    }

    @Test
    fun aLessonThatDidNotArriveNeverClearsTargets() = runTest {
        finisher().complete("d1", body(lessonId = "outra"))
        assertEquals(mapOf("c1" to "anchor:clara"), applied!!.second)
        assertNull(applied!!.third)
    }

    @Test
    fun aLessonWithoutTargetsNeverClearsEverything() = runTest {
        finisher(targets = emptySet()).complete("d1", body())
        assertNull(applied!!.third)
    }

    @Test
    fun finishRunsOnTheGivenScope() = runTest {
        finisher().finish("d1", body())
        testScheduler.advanceUntilIdle()
        assertEquals("requestSync", events.last())
    }
}
