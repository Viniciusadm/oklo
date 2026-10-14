package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.AppliedCounts
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.CardDto
import com.archieapps.oklo.core.net.ChangeSet
import com.archieapps.oklo.core.net.DeckDto
import com.archieapps.oklo.core.net.PullBody
import com.archieapps.oklo.core.net.PushBody
import com.archieapps.oklo.core.net.PushRequest
import com.archieapps.oklo.core.net.RejectedRow
import com.archieapps.oklo.core.net.ReviewDto
import com.archieapps.oklo.core.net.RowRef
import com.archieapps.oklo.core.net.SettingsDto

class FakeServer(private val pageSize: Int = 1000) {
    data class Stored<T>(val row: T, val usn: Long)

    val decks = LinkedHashMap<String, Stored<DeckDto>>()
    val cards = LinkedHashMap<String, Stored<CardDto>>()
    val reviews = LinkedHashMap<String, Stored<ReviewDto>>()
    var settings: Stored<SettingsDto>? = null
    var usn = 0L
    val rejectIds = mutableSetOf<String>()
    val pushSizes = mutableListOf<Int>()
    var beforePushResponse: (suspend () -> Unit)? = null
    var afterFirstPullPage: (() -> Unit)? = null
    var failPush: ApiResult.Failure? = null
    var failPullAtPage: Int? = null
    var failSnapshot: ApiResult.Failure? = null
    var pullCalls = 0

    fun push(request: PushRequest): ApiResult<PushBody> {
        val changes = request.changes
        pushSizes += changes.size
        if (changes.size > 500) return ApiResult.Failure.Validation("Envie no máximo 500 linhas por vez.")
        val next = usn + 1
        val skipped = mutableListOf<RowRef>()
        val rejected = mutableListOf<RejectedRow>()
        var appliedDecks = 0
        var appliedCards = 0
        var appliedReviews = 0
        var appliedSettings = 0
        changes.decks.forEach { row ->
            when {
                row.id in rejectIds -> rejected += RejectedRow("deck", row.id, "Recusado.")
                decks[row.id]?.let { row.updatedAt < it.row.updatedAt } == true -> skipped += RowRef("deck", row.id)
                else -> {
                    decks[row.id] = Stored(row, next)
                    appliedDecks++
                }
            }
        }
        changes.cards.forEach { row ->
            when {
                row.id in rejectIds -> rejected += RejectedRow("card", row.id, "Múltipla escolha precisa de exatamente uma alternativa correta.")
                cards[row.id]?.let { row.updatedAt < it.row.updatedAt } == true -> skipped += RowRef("card", row.id)
                else -> {
                    cards[row.id] = Stored(row, next)
                    appliedCards++
                }
            }
        }
        changes.reviews.forEach { row ->
            if (reviews.containsKey(row.id)) {
                skipped += RowRef("review", row.id)
            } else {
                reviews[row.id] = Stored(row, next)
                appliedReviews++
            }
        }
        changes.settings?.let { row ->
            if (settings?.let { row.updatedAt < it.row.updatedAt } == true) {
                skipped += RowRef("settings", row.id)
            } else {
                settings = Stored(row, next)
                appliedSettings++
            }
        }
        if (appliedDecks + appliedCards + appliedReviews + appliedSettings > 0) usn = next
        return ApiResult.Success(
            PushBody(usn, AppliedCounts(appliedDecks, appliedCards, appliedReviews, appliedSettings), skipped, rejected),
        )
    }

    private sealed interface Item {
        val usn: Long
        val id: String
    }

    private data class DeckItem(val stored: Stored<DeckDto>) : Item {
        override val usn get() = stored.usn
        override val id get() = stored.row.id
    }

    private data class CardItem(val stored: Stored<CardDto>) : Item {
        override val usn get() = stored.usn
        override val id get() = stored.row.id
    }

    private data class ReviewItem(val stored: Stored<ReviewDto>) : Item {
        override val usn get() = stored.usn
        override val id get() = stored.row.id
    }

    private data class SettingsItem(val stored: Stored<SettingsDto>) : Item {
        override val usn get() = stored.usn
        override val id get() = stored.row.id
    }

    private fun items(since: Long, ceiling: Long, withTombstones: Boolean): List<Item> = buildList {
        addAll(decks.values.filter { it.usn in (since + 1)..ceiling && (withTombstones || it.row.deletedAt == null) }.sortedWith(compareBy({ it.usn }, { it.row.id })).map(::DeckItem))
        addAll(cards.values.filter { it.usn in (since + 1)..ceiling && (withTombstones || it.row.deletedAt == null) }.sortedWith(compareBy({ it.usn }, { it.row.id })).map(::CardItem))
        addAll(reviews.values.filter { it.usn in (since + 1)..ceiling && (withTombstones || it.row.deletedAt == null) }.sortedWith(compareBy({ it.usn }, { it.row.id })).map(::ReviewItem))
        settings?.takeIf { it.usn in (since + 1)..ceiling }?.let { add(SettingsItem(it)) }
    }

    private fun page(since: Long, cursor: String?, withTombstones: Boolean): PullBody {
        val (offset, ceiling) = cursor?.split(":")?.let { it[0].toInt() to it[1].toLong() } ?: (0 to usn)
        val all = items(since, ceiling, withTombstones)
        val slice = all.drop(offset).take(pageSize)
        val nextOffset = offset + slice.size
        return PullBody(
            changes = ChangeSet(
                decks = slice.filterIsInstance<DeckItem>().map { it.stored.row },
                cards = slice.filterIsInstance<CardItem>().map { it.stored.row },
                reviews = slice.filterIsInstance<ReviewItem>().map { it.stored.row },
                settings = slice.filterIsInstance<SettingsItem>().firstOrNull()?.stored?.row,
            ),
            usn = ceiling,
            nextCursor = if (nextOffset < all.size) "$nextOffset:$ceiling" else null,
        )
    }

    fun pull(since: Long, cursor: String?): ApiResult<PullBody> {
        pullCalls++
        if (failPullAtPage == pullCalls) return ApiResult.Failure.Network
        val body = page(since, cursor, withTombstones = true)
        if (cursor == null) afterFirstPullPage?.invoke()
        return ApiResult.Success(body)
    }

    fun snapshot(cursor: String?): ApiResult<PullBody> {
        failSnapshot?.let { return it }
        return ApiResult.Success(page(0, cursor, withTombstones = false))
    }

    fun remote(): SyncRemote = object : SyncRemote {
        override suspend fun push(request: PushRequest): ApiResult<PushBody> {
            failPush?.let { return it }
            val result = this@FakeServer.push(request)
            beforePushResponse?.invoke()
            return result
        }

        override suspend fun pull(since: Long, cursor: String?): ApiResult<PullBody> = this@FakeServer.pull(since, cursor)

        override suspend fun snapshot(cursor: String?): ApiResult<PullBody> = this@FakeServer.snapshot(cursor)
    }
}
