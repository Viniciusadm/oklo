package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.CardDto
import com.archieapps.oklo.core.net.ChangeSet
import com.archieapps.oklo.core.net.DeckDto
import com.archieapps.oklo.core.net.LessonDto
import com.archieapps.oklo.core.net.PullBody
import com.archieapps.oklo.core.net.PushBody
import com.archieapps.oklo.core.net.PushRequest
import com.archieapps.oklo.core.net.RejectedRow
import com.archieapps.oklo.core.net.ReviewDto
import com.archieapps.oklo.core.net.SettingsDto
import com.archieapps.oklo.data.db.RowVersion
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncEntity(val wire: String) {
    Deck("deck"),
    Card("card"),
    Review("review"),
    Lesson("lesson"),
    Settings("settings"),
}

data class CleanRef(val entity: SyncEntity, val id: String, val updatedAt: Long)

interface SyncStore {
    suspend fun deviceId(): String

    suspend fun lastUsn(): Long

    suspend fun setLastUsn(usn: Long)

    suspend fun dirtyChanges(): ChangeSet

    suspend fun markClean(refs: List<CleanRef>)

    suspend fun recordRejected(rows: List<RejectedRow>)

    suspend fun <R> transaction(block: suspend () -> R): R

    suspend fun versions(entity: SyncEntity, ids: List<String>): Map<String, RowVersion>

    suspend fun writeClean(changes: ChangeSet)

    suspend fun replaceAll(changes: ChangeSet, usn: Long)
}

interface SyncRemote {
    suspend fun push(request: PushRequest): ApiResult<PushBody>

    suspend fun pull(since: Long, cursor: String?): ApiResult<PullBody>

    suspend fun snapshot(cursor: String?): ApiResult<PullBody>
}

sealed interface SyncReport {
    data class Done(
        val pushed: Int,
        val pulled: Int,
        val applied: Int,
        val rejected: Int,
        val usn: Long,
    ) : SyncReport

    data class Failed(val failure: ApiResult.Failure, val stage: Stage) : SyncReport

    enum class Stage { Push, Pull, Snapshot }
}

object SyncMerge {
    fun incomingWins(local: RowVersion?, incomingUpdatedAt: Long): Boolean {
        if (local == null) return true
        if (!local.dirty) return true
        return incomingUpdatedAt >= local.updatedAt
    }
}

class SyncEngine(
    private val store: SyncStore,
    private val remote: SyncRemote,
    private val batchLimit: Int = MAX_BATCH,
) {
    private val mutex = Mutex()

    suspend fun sync(): SyncReport = mutex.withLock {
        val pushed = when (val outcome = push()) {
            is PushOutcome.Failed -> return@withLock SyncReport.Failed(outcome.failure, SyncReport.Stage.Push)
            is PushOutcome.Pushed -> outcome
        }
        when (val pulled = pull()) {
            is PullOutcome.Failed -> SyncReport.Failed(pulled.failure, SyncReport.Stage.Pull)
            is PullOutcome.Pulled -> SyncReport.Done(
                pushed = pushed.rows,
                pulled = pulled.received,
                applied = pulled.applied,
                rejected = pushed.rejected,
                usn = pulled.usn,
            )
        }
    }

    suspend fun restore(): SyncReport = mutex.withLock {
        var cursor: String? = null
        var usn = 0L
        val decks = mutableListOf<DeckDto>()
        val cards = mutableListOf<CardDto>()
        val reviews = mutableListOf<ReviewDto>()
        val lessons = mutableListOf<LessonDto>()
        var settings: SettingsDto? = null
        do {
            when (val page = remote.snapshot(cursor)) {
                is ApiResult.Failure -> return@withLock SyncReport.Failed(page, SyncReport.Stage.Snapshot)
                is ApiResult.Success -> {
                    val body = page.data
                    usn = body.usn
                    decks += body.changes.decks
                    cards += body.changes.cards
                    reviews += body.changes.reviews
                    lessons += body.changes.lessons
                    body.changes.settings?.let { settings = it }
                    cursor = body.nextCursor
                }
            }
        } while (cursor != null)
        val all = ChangeSet(
            decks = decks.distinctBy { it.id },
            cards = cards.distinctBy { it.id },
            reviews = reviews.distinctBy { it.id },
            lessons = lessons.distinctBy { it.id },
            settings = settings,
        )
        store.replaceAll(all, usn)
        SyncReport.Done(pushed = 0, pulled = all.size, applied = all.size, rejected = 0, usn = usn)
    }

    private sealed interface PushOutcome {
        data class Pushed(val rows: Int, val rejected: Int) : PushOutcome
        data class Failed(val failure: ApiResult.Failure) : PushOutcome
    }

    private sealed interface PullOutcome {
        data class Pulled(val received: Int, val applied: Int, val usn: Long) : PullOutcome
        data class Failed(val failure: ApiResult.Failure) : PullOutcome
    }

    private suspend fun push(): PushOutcome {
        val dirty = store.dirtyChanges()
        if (dirty.isEmpty()) return PushOutcome.Pushed(0, 0)
        val deviceId = store.deviceId()
        var rows = 0
        var rejected = 0
        for (batch in batches(dirty)) {
            when (val result = remote.push(PushRequest(deviceId, batch))) {
                is ApiResult.Failure -> return PushOutcome.Failed(result)
                is ApiResult.Success -> {
                    store.markClean(cleanRefs(batch))
                    if (result.data.rejected.isNotEmpty()) {
                        store.recordRejected(result.data.rejected)
                    }
                    rows += batch.size
                    rejected += result.data.rejected.size
                }
            }
        }
        return PushOutcome.Pushed(rows, rejected)
    }

    private suspend fun pull(): PullOutcome {
        val since = store.lastUsn()
        var cursor: String? = null
        var usn = since
        var received = 0
        var applied = 0
        do {
            when (val page = remote.pull(since, cursor)) {
                is ApiResult.Failure -> return PullOutcome.Failed(page)
                is ApiResult.Success -> {
                    val body = page.data
                    received += body.changes.size
                    applied += apply(body.changes)
                    usn = body.usn
                    cursor = body.nextCursor
                }
            }
        } while (cursor != null)
        store.setLastUsn(usn)
        return PullOutcome.Pulled(received, applied, usn)
    }

    private suspend fun apply(changes: ChangeSet): Int {
        if (changes.isEmpty()) return 0
        return store.transaction {
            val deckVersions = store.versions(SyncEntity.Deck, changes.decks.map { it.id })
            val cardVersions = store.versions(SyncEntity.Card, changes.cards.map { it.id })
            val reviewVersions = store.versions(SyncEntity.Review, changes.reviews.map { it.id })
            val lessonVersions = store.versions(SyncEntity.Lesson, changes.lessons.map { it.id })
            val settingsVersion = changes.settings?.let { store.versions(SyncEntity.Settings, listOf(it.id))[it.id] }
            val winners = ChangeSet(
                decks = changes.decks.filter { SyncMerge.incomingWins(deckVersions[it.id], it.updatedAt) },
                cards = changes.cards.filter { SyncMerge.incomingWins(cardVersions[it.id], it.updatedAt) },
                reviews = changes.reviews.filter { reviewVersions[it.id] == null },
                lessons = changes.lessons.filter { SyncMerge.incomingWins(lessonVersions[it.id], it.updatedAt) },
                settings = changes.settings?.takeIf { SyncMerge.incomingWins(settingsVersion, it.updatedAt) },
            )
            store.writeClean(winners)
            winners.size
        }
    }

    private fun batches(changes: ChangeSet): List<ChangeSet> {
        val items = buildList<Any> {
            addAll(changes.decks)
            addAll(changes.cards)
            addAll(changes.reviews)
            addAll(changes.lessons)
            changes.settings?.let { add(it) }
        }
        return items.chunked(batchLimit).map { chunk ->
            ChangeSet(
                decks = chunk.filterIsInstance<DeckDto>(),
                cards = chunk.filterIsInstance<CardDto>(),
                reviews = chunk.filterIsInstance<ReviewDto>(),
                lessons = chunk.filterIsInstance<LessonDto>(),
                settings = chunk.filterIsInstance<SettingsDto>().firstOrNull(),
            )
        }
    }

    private fun cleanRefs(batch: ChangeSet): List<CleanRef> = buildList {
        batch.decks.forEach { add(CleanRef(SyncEntity.Deck, it.id, it.updatedAt)) }
        batch.cards.forEach { add(CleanRef(SyncEntity.Card, it.id, it.updatedAt)) }
        batch.reviews.forEach { add(CleanRef(SyncEntity.Review, it.id, it.updatedAt)) }
        batch.lessons.forEach { add(CleanRef(SyncEntity.Lesson, it.id, it.updatedAt)) }
        batch.settings?.let { add(CleanRef(SyncEntity.Settings, it.id, it.updatedAt)) }
    }

    companion object {
        const val MAX_BATCH = 500
    }
}
