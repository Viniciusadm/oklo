package com.archieapps.oklo.data.repo

import androidx.room.withTransaction
import com.archieapps.oklo.data.db.OkloDatabase
import com.archieapps.oklo.data.db.toModel
import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CardValidationException(message: String) : IllegalArgumentException(message)

class CardRepository(
    private val db: OkloDatabase,
    private val time: TimeSource = TimeSource.System,
) {
    val stats: Flow<List<CardStat>> = db.cards().observeStats().map { list -> list.map { it.toModel() } }

    fun byDeck(deckId: String): Flow<List<Card>> = db.cards().observeByDeck(deckId).map { list -> list.map { it.toModel() } }

    suspend fun get(id: String): Card? = db.cards().get(id)?.takeIf { it.deletedAt == null }?.toModel()

    suspend fun create(deckId: String, content: CardContent, lessonTarget: String? = null): Card =
        createMany(deckId, listOf(content), lessonTarget).first()

    suspend fun createMany(deckId: String, contents: List<CardContent>, lessonTarget: String? = null): List<Card> = db.withTransaction {
        contents.forEach { content ->
            content.validationError()?.let { throw CardValidationException(it) }
        }
        CardWrites.normalizedTarget(lessonTarget)
        val now = time.now()
        var position = db.cards().maxPosition(deckId)
        contents.map { content ->
            position++
            val entity = CardWrites.fresh(newId(), deckId, content, lessonTarget, position, now)
            db.cards().upsert(entity)
            entity.toModel()
        }
    }

    suspend fun update(id: String, raw: CardContent, lessonTarget: String? = null) {
        raw.validationError()?.let { throw CardValidationException(it) }
        val current = db.cards().get(id) ?: return
        db.cards().upsert(CardWrites.edit(current, raw, lessonTarget, time.now()))
    }

    suspend fun setSuspended(id: String, suspended: Boolean) {
        val current = db.cards().get(id) ?: return
        if (current.suspended == suspended) return
        db.cards().upsert(current.copy(suspended = suspended, updatedAt = Stamp.next(current.updatedAt, time.now()), dirty = true))
    }

    suspend fun delete(id: String) {
        val current = db.cards().get(id) ?: return
        val now = time.now()
        db.cards().upsert(current.copy(deletedAt = now, updatedAt = Stamp.next(current.updatedAt, now), dirty = true))
    }

    suspend fun moveToDeck(id: String, deckId: String) {
        db.withTransaction {
            val current = db.cards().get(id) ?: return@withTransaction
            if (current.deckId == deckId) return@withTransaction
            val position = db.cards().maxPosition(deckId) + 1
            db.cards().upsert(
                current.copy(deckId = deckId, position = position, updatedAt = Stamp.next(current.updatedAt, time.now()), dirty = true),
            )
        }
    }

    suspend fun reorder(orderedIds: List<String>) {
        db.withTransaction {
            val now = time.now()
            val byId = db.cards().getMany(orderedIds).associateBy { it.id }
            orderedIds.forEachIndexed { index, id ->
                val card = byId[id] ?: return@forEachIndexed
                if (card.position != index) {
                    db.cards().upsert(card.copy(position = index, updatedAt = Stamp.next(card.updatedAt, now), dirty = true))
                }
            }
        }
    }
}
