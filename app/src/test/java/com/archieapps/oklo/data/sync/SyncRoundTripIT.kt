package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.CardDto
import com.archieapps.oklo.core.net.DeckDto
import com.archieapps.oklo.core.net.NetworkFactory
import com.archieapps.oklo.core.net.ReviewDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class SyncRoundTripIT {
    private val baseUrl = System.getenv("OKLO_IT_BASE_URL").orEmpty()
    private val token = System.getenv("OKLO_IT_TOKEN").orEmpty()

    @Before
    fun requireBackend() {
        assumeTrue("OKLO_IT_BASE_URL e OKLO_IT_TOKEN não definidos; teste de integração ignorado", baseUrl.isNotBlank() && token.isNotBlank())
    }

    private fun remote(): SyncRemote {
        val network = NetworkFactory(baseUrl, { token }, { null })
        return RetrofitSyncRemote(network.api, network.gatedApi, network.errors, null)
    }

    private fun sync(store: InMemorySyncStore): SyncReport.Done = runBlocking {
        val report = SyncEngine(store, remote()).sync()
        assertTrue("sync falhou: $report", report is SyncReport.Done)
        report as SyncReport.Done
    }

    @Test
    fun `two devices converge through the real backend`() {
        val now = System.currentTimeMillis()
        val deckId = UUID.randomUUID().toString()
        val cardId = UUID.randomUUID().toString()
        val reviewId = UUID.randomUUID().toString()
        val a = InMemorySyncStore()
        val b = InMemorySyncStore()
        sync(a)
        sync(b)

        a.putDeck(DeckDto(id = deckId, name = "IT $now", createdAt = now, updatedAt = now))
        a.putCard(CardDto(id = cardId, deckId = deckId, type = "qa", front = "Pergunta IT", back = "Resposta", createdAt = now, updatedAt = now))
        a.putReview(ReviewDto(id = reviewId, cardId = cardId, rating = 3, stateBefore = "new", stabilityAfter = 3.173, difficultyAfter = 5.28, scheduledSeconds = 600, durationMs = 4_000, reviewedAt = now, createdAt = now, updatedAt = now))
        val pushed = sync(a)
        assertEquals(3, pushed.pushed)
        assertEquals(0, pushed.rejected)
        assertEquals(0, a.dirtyCount())

        sync(b)
        assertEquals("IT $now", b.decks.getValue(deckId).row.name)
        assertEquals("Pergunta IT", b.cards.getValue(cardId).row.front)
        assertTrue(b.reviews.containsKey(reviewId))

        b.editCard(cardId, now + 1_000) { it.copy(front = "Editado em B") }
        sync(b)
        sync(a)
        assertEquals("Editado em B", a.cards.getValue(cardId).row.front)

        a.editCard(cardId, now + 2_000) { it.copy(deletedAt = now + 2_000) }
        a.editDeck(deckId, now + 2_000) { it.copy(deletedAt = now + 2_000) }
        sync(a)
        sync(b)
        assertNotNull(b.cards.getValue(cardId).row.deletedAt)
        assertNotNull(b.decks.getValue(deckId).row.deletedAt)
        assertEquals(a.usn, b.usn)
    }
}
