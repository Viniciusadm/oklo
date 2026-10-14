package com.archieapps.oklo.data.repo

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.core.net.LessonJobStarted
import com.archieapps.oklo.core.net.LessonLinkDto
import com.archieapps.oklo.data.db.LessonEntity
import com.archieapps.oklo.data.prefs.PendingLessonJob
import com.archieapps.oklo.data.prefs.PendingLessonJobs
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonAiFinisherTest {
    private class FakePending : PendingLessonJobs {
        val jobs = mutableListOf<PendingLessonJob>()

        override suspend fun pendingLessonJobs(): List<PendingLessonJob> = jobs.toList()

        override suspend fun addPendingLessonJob(job: PendingLessonJob) {
            jobs.removeAll { it.deckId == job.deckId }
            jobs += job
        }

        override suspend fun removePendingLessonJob(jobId: String) {
            jobs.removeAll { it.jobId == jobId }
        }
    }

    private class FakeGenerator : LessonGenerator {
        val statuses = ArrayDeque<ApiResult<LessonJobBody>>()
        var polls = 0

        override suspend fun lessonGenerate(request: LessonGenerateRequest): ApiResult<LessonJobStarted> =
            ApiResult.Success(LessonJobStarted("unused"))

        override suspend fun lessonJob(jobId: String): ApiResult<LessonJobBody> {
            polls++
            return statuses.removeFirstOrNull() ?: ApiResult.Success(LessonJobBody("running"))
        }
    }

    private val events = mutableListOf<String>()
    private val dao = FakeLessonDao()
    private val pending = FakePending()
    private val generator = FakeGenerator()
    private var applied: Triple<String, Map<String, String>, Set<String>?>? = null
    private var pulled: LessonEntity? = lesson()

    private fun TestScope.finisher(targets: Set<String> = setOf("page:intro", "anchor:clara")) = LessonAiFinisher(
        generator = generator,
        pending = pending,
        syncNow = {
            events += "sync"
            pulled?.let { dao.upsert(it) }
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
        scope = backgroundScope,
        compileDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun lesson(id: String = "lesson-1", version: Int = 2, origin: String = "ai", dirty: Boolean = false) = LessonEntity(
        id = id, deckId = "d1", source = "# Nova", version = version, ribaltaVersion = "0.3.0", origin = origin,
        createdAt = 1, updatedAt = 2, deletedAt = null, dirty = dirty,
    )

    private fun done(clear: Boolean = true, lessonId: String = "lesson-1", version: Int? = 2) = ApiResult.Success(
        LessonJobBody(
            "done",
            lessonId = lessonId,
            lessonVersion = version,
            warnings = listOf("Aviso"),
            links = listOf(LessonLinkDto("c1", "anchor:clara")),
            clearTargets = clear,
        ),
    )

    @Test
    fun trackPersistsThePendingJobBeforePolling() = runTest {
        val finisher = finisher()
        finisher.track("d1", "job-1")
        assertEquals(listOf(PendingLessonJob("d1", "job-1")), pending.jobs)
        assertEquals(LessonJobState.Polling, finisher.jobState("d1").first())
    }

    @Test
    fun completesAloneWithoutAnySheetAndClearsThePendingJob() = runTest {
        generator.statuses += listOf(ApiResult.Success(LessonJobBody("running")), done())
        val finisher = finisher()
        finisher.track("d1", "job-1")
        runCurrent()
        assertEquals(1, generator.polls)
        advanceTimeBy(LessonAiFinisher.POLL_INTERVAL_MILLIS)
        runCurrent()
        assertEquals(listOf("sync", "targets:# Nova", "apply", "requestSync"), events)
        assertEquals(Triple("d1", mapOf("c1" to "anchor:clara"), setOf("page:intro", "anchor:clara")), applied)
        assertTrue(pending.jobs.isEmpty())
        assertEquals(LessonJobState.Done(listOf("Aviso")), finisher.jobState("d1").first())
    }

    @Test
    fun transientNetworkErrorsKeepPolling() = runTest {
        generator.statuses += listOf(ApiResult.Failure.Network, done())
        val finisher = finisher()
        finisher.track("d1", "job-1")
        runCurrent()
        assertEquals(LessonJobState.Polling, finisher.jobState("d1").first())
        advanceTimeBy(LessonAiFinisher.POLL_INTERVAL_MILLIS)
        runCurrent()
        assertTrue(finisher.jobState("d1").first() is LessonJobState.Done)
    }

    @Test
    fun failedJobRemovesThePendingEntry() = runTest {
        generator.statuses += ApiResult.Success(LessonJobBody("failed", error = "A IA falhou."))
        val finisher = finisher()
        finisher.track("d1", "job-1")
        runCurrent()
        assertEquals(LessonJobState.Failed("A IA falhou."), finisher.jobState("d1").first())
        assertTrue(pending.jobs.isEmpty())
        assertNull(applied)
    }

    @Test
    fun resumesPersistedJobsOnStart() = runTest {
        pending.jobs += PendingLessonJob("d1", "job-1")
        generator.statuses += done()
        val finisher = finisher()
        finisher.resumePending()
        runCurrent()
        assertEquals(mapOf("c1" to "anchor:clara"), applied!!.second)
        assertTrue(pending.jobs.isEmpty())
    }

    @Test
    fun resumeDoesNotStartASecondPollerForTheSameDeck() = runTest {
        val finisher = finisher()
        finisher.track("d1", "job-1")
        runCurrent()
        finisher.resumePending()
        runCurrent()
        assertEquals(1, generator.polls)
    }

    @Test
    fun skipsLinksWhenTheLessonWasEditedAfterTheJob() = runTest {
        pulled = lesson(version = 3, origin = "manual")
        generator.statuses += done()
        val finisher = finisher()
        finisher.track("d1", "job-1")
        runCurrent()
        assertNull(applied)
        assertTrue(pending.jobs.isEmpty())
        assertTrue(finisher.jobState("d1").first() is LessonJobState.Done)
    }

    @Test
    fun skipsLinksWhenTheLocalLessonHasUnsyncedEdits() = runTest {
        pulled = lesson(version = 2, dirty = true)
        generator.statuses += done()
        finisher().track("d1", "job-1")
        runCurrent()
        assertNull(applied)
        assertTrue(pending.jobs.isEmpty())
    }

    @Test
    fun keepsThePendingJobWhenTheLessonHasNotArrivedYet() = runTest {
        pulled = null
        generator.statuses += done()
        val finisher = finisher()
        finisher.track("d1", "job-1")
        runCurrent()
        assertNull(applied)
        assertEquals(listOf(PendingLessonJob("d1", "job-1")), pending.jobs)
        assertTrue(finisher.jobState("d1").first() is LessonJobState.Done)
    }

    @Test
    fun withoutClearTargetsOnlyLinksAreApplied() = runTest {
        generator.statuses += done(clear = false)
        finisher().track("d1", "job-1")
        runCurrent()
        assertNull(applied!!.third)
    }

    @Test
    fun aLessonWithoutTargetsNeverClearsEverything() = runTest {
        generator.statuses += done()
        finisher(targets = emptySet()).track("d1", "job-1")
        runCurrent()
        assertNull(applied!!.third)
    }

    @Test
    fun tooManyPollsLeaveTheJobPendingAsDelayed() = runTest {
        val finisher = finisher()
        finisher.track("d1", "job-1")
        advanceTimeBy(LessonAiFinisher.POLL_INTERVAL_MILLIS * LessonAiFinisher.MAX_POLLS)
        runCurrent()
        assertEquals(LessonAiFinisher.MAX_POLLS, generator.polls)
        assertEquals(LessonJobState.Delayed, finisher.jobState("d1").first())
        assertEquals(listOf(PendingLessonJob("d1", "job-1")), pending.jobs)
    }

    @Test
    fun clearForgetsAFinishedStateButNotARunningOne() = runTest {
        generator.statuses += done()
        val finisher = finisher()
        finisher.track("d1", "job-1")
        finisher.clear("d1")
        assertEquals(LessonJobState.Polling, finisher.jobState("d1").first())
        runCurrent()
        finisher.clear("d1")
        assertNull(finisher.jobState("d1").first())
    }
}
