package com.archieapps.oklo.data.repo

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.data.db.LessonDao
import com.archieapps.oklo.data.db.LessonEntity
import com.archieapps.oklo.data.prefs.PendingLessonJob
import com.archieapps.oklo.data.prefs.PendingLessonJobs
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface LessonLinkWriter {
    suspend fun applyLessonLinks(deckId: String, links: Map<String, String>, keepTargets: Set<String>?)
}

sealed interface LessonJobState {
    data object Polling : LessonJobState

    data class Done(val warnings: List<String>) : LessonJobState

    data class Failed(val error: String) : LessonJobState

    data object Delayed : LessonJobState
}

interface LessonJobTracker {
    suspend fun track(deckId: String, jobId: String)

    fun jobState(deckId: String): Flow<LessonJobState?>

    fun clear(deckId: String)
}

class LessonAiFinisher(
    private val generator: LessonGenerator,
    private val pending: PendingLessonJobs,
    private val syncNow: suspend () -> SyncReport?,
    private val lessons: LessonDao,
    private val cards: LessonLinkWriter,
    private val targetsOf: (String) -> Set<String>,
    private val requestSync: () -> Unit,
    private val scope: CoroutineScope,
    private val compileDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LessonJobTracker {
    private enum class Outcome { Applied, Skipped, NotArrived }

    private val states = MutableStateFlow<Map<String, LessonJobState>>(emptyMap())
    private val pollers = mutableMapOf<String, Job>()

    override suspend fun track(deckId: String, jobId: String) {
        pending.addPendingLessonJob(PendingLessonJob(deckId, jobId))
        startPolling(deckId, jobId, replaceRunning = true)
    }

    override fun jobState(deckId: String): Flow<LessonJobState?> = states.map { it[deckId] }.distinctUntilChanged()

    override fun clear(deckId: String) {
        states.update { current -> if (current[deckId] == LessonJobState.Polling) current else current - deckId }
    }

    fun resumePending(): Job = scope.launch {
        pending.pendingLessonJobs().forEach { startPolling(it.deckId, it.jobId, replaceRunning = false) }
    }

    private fun startPolling(deckId: String, jobId: String, replaceRunning: Boolean) {
        synchronized(pollers) {
            val running = pollers[deckId]
            if (running?.isActive == true) {
                if (!replaceRunning) return
                running.cancel()
            }
            setState(deckId, LessonJobState.Polling)
            pollers[deckId] = scope.launch { poll(deckId, jobId) }
        }
    }

    private suspend fun poll(deckId: String, jobId: String) {
        repeat(MAX_POLLS) { attempt ->
            if (attempt > 0) delay(POLL_INTERVAL_MILLIS)
            when (val result = generator.lessonJob(jobId)) {
                is ApiResult.Success -> when (result.data.status) {
                    STATUS_DONE -> {
                        complete(deckId, jobId, result.data)
                        return
                    }
                    STATUS_FAILED -> {
                        pending.removePendingLessonJob(jobId)
                        setState(deckId, LessonJobState.Failed(result.data.error ?: DEFAULT_FAILURE))
                        return
                    }
                }
                ApiResult.Failure.Network, is ApiResult.Failure.RateLimited -> Unit
                is ApiResult.Failure -> {
                    if (result !in RESUMABLE_FAILURES) pending.removePendingLessonJob(jobId)
                    setState(deckId, LessonJobState.Failed(result.userMessage))
                    return
                }
            }
        }
        setState(deckId, LessonJobState.Delayed)
    }

    private suspend fun complete(deckId: String, jobId: String, body: LessonJobBody) {
        syncNow()
        val outcome = applyLinks(deckId, body)
        if (outcome != Outcome.NotArrived) pending.removePendingLessonJob(jobId)
        setState(deckId, LessonJobState.Done(body.warnings))
    }

    private suspend fun applyLinks(deckId: String, body: LessonJobBody): Outcome {
        val lesson = lessons.getByDeck(deckId) ?: return Outcome.NotArrived
        when (stillTheJobLesson(lesson, body)) {
            null -> return Outcome.NotArrived
            false -> return Outcome.Skipped
            true -> Unit
        }
        val keepTargets = if (body.clearTargets) {
            withContext(compileDispatcher) { targetsOf(lesson.source) }.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        cards.applyLessonLinks(deckId, body.links.associate { it.cardId to it.target }, keepTargets)
        requestSync()
        return Outcome.Applied
    }

    private fun stillTheJobLesson(lesson: LessonEntity, body: LessonJobBody): Boolean? {
        if (lesson.id != body.lessonId || lesson.deletedAt != null || lesson.dirty) return false
        val produced = body.lessonVersion ?: return lesson.origin == ORIGIN_AI
        return when {
            lesson.version < produced -> null
            lesson.version == produced -> lesson.origin == ORIGIN_AI
            else -> false
        }
    }

    private fun setState(deckId: String, state: LessonJobState) {
        states.update { it + (deckId to state) }
    }

    companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
        const val MAX_POLLS = 300
        private val RESUMABLE_FAILURES = setOf<ApiResult.Failure>(ApiResult.Failure.Unauthorized, ApiResult.Failure.NotSignedIn)
        private const val STATUS_DONE = "done"
        private const val STATUS_FAILED = "failed"
        private const val ORIGIN_AI = "ai"
        private const val DEFAULT_FAILURE = "A geração da aula falhou. Tente de novo."
    }
}
