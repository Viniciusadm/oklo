package com.archieapps.oklo.data.repo

import androidx.room.withTransaction
import com.archieapps.oklo.data.db.DeckEntity
import com.archieapps.oklo.data.db.OkloDatabase
import com.archieapps.oklo.data.db.toModel
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.data.model.Deck
import com.archieapps.oklo.domain.DeckTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DeleteImpact(
    val subdecks: Int,
    val cards: Int,
)

class DeckNameException(message: String) : IllegalArgumentException(message)

class DeckRepository(
    private val db: OkloDatabase,
    private val time: TimeSource = TimeSource.System,
) {
    val decks: Flow<List<Deck>> = db.decks().observeAlive().map { list -> list.map { it.toModel() } }

    suspend fun alive(): List<Deck> = db.decks().alive().map { it.toModel() }

    suspend fun get(id: String): Deck? = db.decks().get(id)?.takeIf { it.deletedAt == null }?.toModel()

    fun validateName(raw: String): String {
        val name = raw.trim()
        if (name.isEmpty()) throw DeckNameException("Dê um nome ao deck.")
        if (name.length > 120) throw DeckNameException("Use no máximo 120 caracteres.")
        return name
    }

    suspend fun create(rawName: String, parentId: String?): Deck {
        val name = validateName(rawName)
        val now = time.now()
        val siblings = db.decks().alive().filter { it.parentId == parentId }
        val entity = DeckEntity(
            id = newId(),
            parentId = parentId,
            name = name,
            position = (siblings.maxOfOrNull { it.position } ?: -1) + 1,
            lastStudiedAt = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            dirty = true,
        )
        db.decks().upsert(entity)
        return entity.toModel()
    }

    suspend fun rename(id: String, rawName: String) {
        val name = validateName(rawName)
        val current = db.decks().get(id) ?: return
        if (current.name == name) return
        db.decks().upsert(current.copy(name = name, updatedAt = Stamp.next(current.updatedAt, time.now()), dirty = true))
    }

    suspend fun move(id: String, newParentId: String?): Boolean = db.withTransaction {
        val all = db.decks().alive().map { it.toModel() }
        if (!DeckTree.canMove(all, id, newParentId)) return@withTransaction false
        val current = db.decks().get(id) ?: return@withTransaction false
        if (current.parentId == newParentId) return@withTransaction true
        val siblings = all.filter { it.parentId == newParentId && it.id != id }
        db.decks().upsert(
            current.copy(
                parentId = newParentId,
                position = (siblings.maxOfOrNull { it.position } ?: -1) + 1,
                updatedAt = Stamp.next(current.updatedAt, time.now()),
                dirty = true,
            ),
        )
        true
    }

    suspend fun impact(id: String): DeleteImpact {
        val all = db.decks().alive().map { it.toModel() }
        val subtree = DeckTree.subtreeIds(all, id)
        val cards = db.cards().aliveInDecks(subtree.toList()).size
        return DeleteImpact(subdecks = subtree.size - 1, cards = cards)
    }

    suspend fun delete(id: String) {
        db.withTransaction {
            val now = time.now()
            val all = db.decks().alive().map { it.toModel() }
            val subtree = DeckTree.subtreeIds(all, id).toList()
            db.decks().getMany(subtree).forEach { deck ->
                db.decks().upsert(deck.copy(deletedAt = now, updatedAt = Stamp.next(deck.updatedAt, now), dirty = true))
            }
            subtree.chunked(500).forEach { chunk ->
                db.cards().aliveInDecks(chunk).forEach { card ->
                    db.cards().upsert(card.copy(deletedAt = now, updatedAt = Stamp.next(card.updatedAt, now), dirty = true))
                }
            }
        }
    }

    suspend fun duplicate(id: String): Deck? = db.withTransaction {
        val now = time.now()
        val all = db.decks().alive().map { it.toModel() }
        val source = all.firstOrNull { it.id == id } ?: return@withTransaction null
        val subtree = DeckTree.subtreeIds(all, id)
        val mapping = subtree.associateWith { newId() }
        val siblings = all.filter { it.parentId == source.parentId }
        var created: Deck? = null
        all.filter { it.id in subtree }.forEach { deck ->
            val isRoot = deck.id == id
            val entity = DeckEntity(
                id = mapping.getValue(deck.id),
                parentId = if (isRoot) deck.parentId else mapping[deck.parentId] ?: deck.parentId,
                name = if (isRoot) copyName(deck.name) else deck.name,
                position = if (isRoot) (siblings.maxOfOrNull { it.position } ?: -1) + 1 else deck.position,
                lastStudiedAt = null,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                dirty = true,
            )
            db.decks().upsert(entity)
            if (isRoot) created = entity.toModel()
        }
        subtree.toList().chunked(500).forEach { chunk ->
            db.cards().aliveInDecks(chunk).forEach { card ->
                db.cards().upsert(
                    card.copy(
                        id = newId(),
                        deckId = mapping.getValue(card.deckId),
                        state = CardState.New.wire,
                        step = null,
                        stability = null,
                        difficulty = null,
                        dueAt = null,
                        lastReviewAt = null,
                        reps = 0,
                        lapses = 0,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        dirty = true,
                    ),
                )
            }
        }
        created
    }


    private fun copyName(name: String): String {
        val suffix = " (cópia)"
        return if (name.length + suffix.length <= 120) name + suffix else name.take(120 - suffix.length) + suffix
    }
}
