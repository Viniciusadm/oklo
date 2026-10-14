package com.archieapps.oklo.data.repo

import com.archieapps.oklo.core.fsrs.FsrsParameters
import com.archieapps.oklo.core.fsrs.FsrsScheduler
import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.core.net.DeckDto
import com.archieapps.oklo.data.db.CardEntity
import com.archieapps.oklo.data.db.toDto
import com.archieapps.oklo.data.sync.InMemorySyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyWritesTest {
    private val deck = DeckDto(id = "d1", name = "Laravel", lastStudiedAt = 111, createdAt = 100, updatedAt = 100)

    private fun card(id: String) = CardEntity(
        id = id, deckId = "d1", type = "basic", front = "F", back = "V", explanation = null, answer = null,
        choicesJson = null, state = "new", step = null, stability = null, difficulty = null, dueAt = null,
        lastReviewAt = null, reps = 0, lapses = 0, suspended = false, position = 0, createdAt = 100,
        updatedAt = 100, deletedAt = null, dirty = false,
    )

    @Test
    fun `a study session writes cards and reviews but never dirties the deck`() = runTest {
        val store = InMemorySyncStore()
        store.putDeck(deck, dirty = false)
        val scheduler = FsrsScheduler(FsrsParameters(enableFuzz = false))
        var now = 1_791_460_800_000L
        listOf("c1", "c2").forEach { store.putCard(card(it).toDto(), dirty = false) }
        listOf("c1" to Rating.Good, "c2" to Rating.Again, "c1" to Rating.Good, "c2" to Rating.Easy).forEachIndexed { index, (id, rating) ->
            val current = store.cards.getValue(id).row
            val entity = card(id).copy(
                state = current.state, step = current.step, stability = current.stability, difficulty = current.difficulty,
                dueAt = current.dueAt, lastReviewAt = current.lastReviewAt, reps = current.reps, updatedAt = current.updatedAt,
            )
            val write = StudyWrites.answer(entity, rating, scheduler, now, 4_000, null, "r$index")
            assertEquals("d1", write.card.deckId)
            store.putCard(write.card.toDto())
            store.putReview(write.review.toDto())
            now += 11 * 60_000
        }

        val dirty = store.dirtyChanges()
        assertTrue(dirty.decks.isEmpty())
        assertEquals(2, dirty.cards.size)
        assertEquals(4, dirty.reviews.size)
        assertEquals(deck, store.decks.getValue("d1").row)
    }
}
