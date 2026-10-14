package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.CardDto
import com.archieapps.oklo.core.net.ChangeSet
import com.archieapps.oklo.core.net.DeckDto
import com.archieapps.oklo.core.net.LessonDto
import com.archieapps.oklo.core.net.RejectedRow
import com.archieapps.oklo.core.net.ReviewDto
import com.archieapps.oklo.core.net.SettingsDto
import com.archieapps.oklo.data.db.RowVersion
import java.util.UUID

data class Local<T>(val row: T, val dirty: Boolean)

class InMemorySyncStore(private val device: String = UUID.randomUUID().toString()) : SyncStore {
    val decks = LinkedHashMap<String, Local<DeckDto>>()
    val cards = LinkedHashMap<String, Local<CardDto>>()
    val reviews = LinkedHashMap<String, Local<ReviewDto>>()
    val lessons = LinkedHashMap<String, Local<LessonDto>>()
    var settings: Local<SettingsDto>? = null
    var usn = 0L
    val rejected = mutableListOf<RejectedRow>()

    fun putDeck(deck: DeckDto, dirty: Boolean = true) {
        decks[deck.id] = Local(deck, dirty)
    }

    fun putCard(card: CardDto, dirty: Boolean = true) {
        cards[card.id] = Local(card, dirty)
    }

    fun putLesson(lesson: LessonDto, dirty: Boolean = true) {
        lessons[lesson.id] = Local(lesson, dirty)
    }

    fun putReview(review: ReviewDto, dirty: Boolean = true) {
        reviews[review.id] = Local(review, dirty)
    }

    fun putSettings(value: SettingsDto, dirty: Boolean = true) {
        settings = Local(value, dirty)
    }

    fun editDeck(id: String, at: Long, transform: (DeckDto) -> DeckDto) {
        val current = decks.getValue(id).row
        val stamp = maxOf(at, current.updatedAt + 1)
        decks[id] = Local(transform(current).copy(updatedAt = stamp), dirty = true)
    }

    fun editCard(id: String, at: Long, transform: (CardDto) -> CardDto) {
        val current = cards.getValue(id).row
        val stamp = maxOf(at, current.updatedAt + 1)
        cards[id] = Local(transform(current).copy(updatedAt = stamp), dirty = true)
    }

    fun dirtyCount(): Int =
        decks.values.count { it.dirty } + cards.values.count { it.dirty } + reviews.values.count { it.dirty } + lessons.values.count { it.dirty } + (if (settings?.dirty == true) 1 else 0)

    override suspend fun deviceId(): String = device

    override suspend fun lastUsn(): Long = usn

    override suspend fun setLastUsn(usn: Long) {
        this.usn = usn
    }

    override suspend fun dirtyChanges(): ChangeSet = ChangeSet(
        decks = decks.values.filter { it.dirty }.map { it.row },
        cards = cards.values.filter { it.dirty }.map { it.row },
        reviews = reviews.values.filter { it.dirty }.map { it.row },
        lessons = lessons.values.filter { it.dirty }.map { it.row },
        settings = settings?.takeIf { it.dirty }?.row,
    )

    override suspend fun markClean(refs: List<CleanRef>) {
        refs.forEach { ref ->
            when (ref.entity) {
                SyncEntity.Deck -> decks[ref.id]?.let { if (it.row.updatedAt == ref.updatedAt) decks[ref.id] = it.copy(dirty = false) }
                SyncEntity.Card -> cards[ref.id]?.let { if (it.row.updatedAt == ref.updatedAt) cards[ref.id] = it.copy(dirty = false) }
                SyncEntity.Review -> reviews[ref.id]?.let { if (it.row.updatedAt == ref.updatedAt) reviews[ref.id] = it.copy(dirty = false) }
                SyncEntity.Lesson -> lessons[ref.id]?.let { if (it.row.updatedAt == ref.updatedAt) lessons[ref.id] = it.copy(dirty = false) }
                SyncEntity.Settings -> settings?.let { if (it.row.updatedAt == ref.updatedAt) settings = it.copy(dirty = false) }
            }
        }
    }

    override suspend fun recordRejected(rows: List<RejectedRow>) {
        rejected += rows
    }

    override suspend fun <R> transaction(block: suspend () -> R): R = block()

    override suspend fun versions(entity: SyncEntity, ids: List<String>): Map<String, RowVersion> = when (entity) {
        SyncEntity.Deck -> ids.mapNotNull { id -> decks[id]?.let { id to RowVersion(id, it.row.updatedAt, it.dirty) } }.toMap()
        SyncEntity.Card -> ids.mapNotNull { id -> cards[id]?.let { id to RowVersion(id, it.row.updatedAt, it.dirty) } }.toMap()
        SyncEntity.Review -> ids.mapNotNull { id -> reviews[id]?.let { id to RowVersion(id, it.row.updatedAt, it.dirty) } }.toMap()
        SyncEntity.Lesson -> ids.mapNotNull { id -> lessons[id]?.let { id to RowVersion(id, it.row.updatedAt, it.dirty) } }.toMap()
        SyncEntity.Settings -> settings?.let { mapOf(it.row.id to RowVersion(it.row.id, it.row.updatedAt, it.dirty)) } ?: emptyMap()
    }

    override suspend fun writeClean(changes: ChangeSet) {
        changes.decks.forEach { decks[it.id] = Local(it, false) }
        changes.cards.forEach { cards[it.id] = Local(it, false) }
        changes.reviews.forEach { if (!reviews.containsKey(it.id)) reviews[it.id] = Local(it, false) }
        changes.lessons.forEach { lessons[it.id] = Local(it, false) }
        changes.settings?.let { settings = Local(it, false) }
    }

    override suspend fun replaceAll(changes: ChangeSet, usn: Long) {
        decks.clear()
        cards.clear()
        reviews.clear()
        lessons.clear()
        settings = null
        writeClean(changes)
        this.usn = usn
    }
}
