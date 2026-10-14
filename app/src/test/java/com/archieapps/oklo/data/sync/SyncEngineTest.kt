package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.CardDto
import com.archieapps.oklo.core.net.DeckDto
import com.archieapps.oklo.core.net.LessonDto
import com.archieapps.oklo.core.net.ReviewDto
import com.archieapps.oklo.core.net.SettingsDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    private fun deck(id: String, name: String = id, at: Long = 1_000, parentId: String? = null) =
        DeckDto(id = id, parentId = parentId, name = name, position = 0, createdAt = at, updatedAt = at)

    private fun card(id: String, deckId: String, front: String = id, at: Long = 1_000) =
        CardDto(id = id, deckId = deckId, type = "basic", front = front, back = "verso", createdAt = at, updatedAt = at)

    private fun review(id: String, cardId: String, at: Long = 1_000) =
        ReviewDto(id = id, cardId = cardId, rating = 3, stateBefore = "new", reviewedAt = at, createdAt = at, updatedAt = at)

    @Test
    fun `push sends dirty rows, cleans them and a second device rebuilds everything from since zero`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        a.putDeck(deck("d1", "Programação"))
        a.putCard(card("c1", "d1"))
        a.putReview(review("r1", "c1"))
        a.putSettings(SettingsDto(newPerDay = 30, updatedAt = 1_000))

        val report = SyncEngine(a, server.remote()).sync()

        assertTrue(report is SyncReport.Done)
        assertEquals(0, a.dirtyCount())
        assertEquals(server.usn, a.usn)
        val b = InMemorySyncStore()
        SyncEngine(b, server.remote()).sync()
        assertEquals("Programação", b.decks.getValue("d1").row.name)
        assertEquals(30, b.settings!!.row.newPerDay)
        assertEquals(setOf("c1"), b.cards.keys)
        assertEquals(setOf("r1"), b.reviews.keys)
        assertEquals(0, b.dirtyCount())
    }

    @Test
    fun `a row edited while the push is in flight stays dirty and wins over the older server copy on pull`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        a.putDeck(deck("d1", "Antes", at = 1_000))
        server.beforePushResponse = { a.editDeck("d1", 5_000) { it.copy(name = "Durante") } }

        SyncEngine(a, server.remote()).sync()

        val local = a.decks.getValue("d1")
        assertTrue(local.dirty)
        assertEquals("Durante", local.row.name)
        assertEquals("Antes", server.decks.getValue("d1").row.name)

        server.beforePushResponse = null
        SyncEngine(a, server.remote()).sync()
        assertFalse(a.decks.getValue("d1").dirty)
        assertEquals("Durante", server.decks.getValue("d1").row.name)
    }

    @Test
    fun `last write wins in both directions between two devices`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        val b = InMemorySyncStore()
        a.putDeck(deck("d1", "Original", at = 1_000))
        SyncEngine(a, server.remote()).sync()
        SyncEngine(b, server.remote()).sync()

        a.editDeck("d1", 3_000) { it.copy(name = "A mais novo") }
        b.editDeck("d1", 2_000) { it.copy(name = "B mais velho") }
        SyncEngine(a, server.remote()).sync()
        val bReport = SyncEngine(b, server.remote()).sync() as SyncReport.Done

        assertEquals("A mais novo", b.decks.getValue("d1").row.name)
        assertFalse(b.decks.getValue("d1").dirty)
        assertEquals("A mais novo", server.decks.getValue("d1").row.name)
        assertEquals(0, bReport.rejected)

        b.editDeck("d1", 9_000) { it.copy(name = "B depois") }
        SyncEngine(b, server.remote()).sync()
        SyncEngine(a, server.remote()).sync()
        assertEquals("B depois", a.decks.getValue("d1").row.name)
    }

    @Test
    fun `a dirty local row newer than the pulled one is kept and the older pulled row is ignored`() = runTest {
        val server = FakeServer()
        val other = InMemorySyncStore()
        other.putDeck(deck("d1", "Servidor", at = 2_000))
        SyncEngine(other, server.remote()).sync()

        val local = InMemorySyncStore()
        server.failPush = ApiResult.Failure.Network
        local.putDeck(deck("d1", "Local", at = 8_000))
        val failed = SyncEngine(local, server.remote()).sync()
        assertTrue(failed is SyncReport.Failed)
        assertEquals(0L, local.usn)

        server.failPush = null
        server.beforePushResponse = { local.editDeck("d1", 9_000) { it.copy(name = "Local editado") } }
        SyncEngine(local, server.remote()).sync()
        assertEquals("Local editado", local.decks.getValue("d1").row.name)
        assertTrue(local.decks.getValue("d1").dirty)
    }

    @Test
    fun `tombstones propagate and a later edit resurrects the row`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        val b = InMemorySyncStore()
        a.putDeck(deck("d1"))
        a.putCard(card("c1", "d1"))
        SyncEngine(a, server.remote()).sync()
        SyncEngine(b, server.remote()).sync()

        a.editCard("c1", 4_000) { it.copy(deletedAt = 4_000) }
        SyncEngine(a, server.remote()).sync()
        SyncEngine(b, server.remote()).sync()
        assertEquals(4_000L, b.cards.getValue("c1").row.deletedAt)

        b.editCard("c1", 6_000) { it.copy(deletedAt = null, front = "Ressuscitado") }
        SyncEngine(b, server.remote()).sync()
        SyncEngine(a, server.remote()).sync()
        assertNull(a.cards.getValue("c1").row.deletedAt)
        assertEquals("Ressuscitado", a.cards.getValue("c1").row.front)
    }

    @Test
    fun `pull follows every cursor page and keeps the ceiling usn`() = runTest {
        val server = FakeServer(pageSize = 2)
        val a = InMemorySyncStore()
        repeat(4) { a.putDeck(deck("d$it")) }
        repeat(3) { a.putCard(card("c$it", "d0")) }
        SyncEngine(a, server.remote()).sync()

        val b = InMemorySyncStore()
        val late = InMemorySyncStore()
        late.putDeck(deck("late"))
        server.afterFirstPullPage = { server.push(com.archieapps.oklo.core.net.PushRequest("x", late.decks.values.first().row.let { com.archieapps.oklo.core.net.ChangeSet(decks = listOf(it)) })) }
        val report = SyncEngine(b, server.remote()).sync() as SyncReport.Done
        server.afterFirstPullPage = null

        assertEquals(7, report.pulled)
        assertEquals(7, b.decks.size + b.cards.size)
        assertFalse(b.decks.containsKey("late"))
        assertEquals(server.usn - 1, b.usn)

        SyncEngine(b, server.remote()).sync()
        assertTrue(b.decks.containsKey("late"))
        assertEquals(server.usn, b.usn)
    }

    @Test
    fun `dirty rows are pushed in batches of at most 500`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        repeat(3) { a.putDeck(deck("d$it")) }
        repeat(1_200) { a.putCard(card("c$it", "d0")) }
        a.putSettings(SettingsDto(updatedAt = 1_000))

        val report = SyncEngine(a, server.remote()).sync() as SyncReport.Done

        assertEquals(listOf(500, 500, 204), server.pushSizes)
        assertEquals(1_204, report.pushed)
        assertEquals(0, a.dirtyCount())
        assertNotNull(server.settings)
    }

    @Test
    fun `rejected and skipped rows leave dirty and rejections are recorded`() = runTest {
        val server = FakeServer()
        val other = InMemorySyncStore()
        other.putDeck(deck("d1", "Mais novo", at = 9_000))
        SyncEngine(other, server.remote()).sync()

        val a = InMemorySyncStore()
        a.putDeck(deck("d1", "Velho", at = 1_000))
        a.putCard(card("bad", "d1"))
        server.rejectIds += "bad"
        val report = SyncEngine(a, server.remote()).sync() as SyncReport.Done

        assertEquals(1, report.rejected)
        assertEquals(listOf("bad"), a.rejected.map { it.id })
        assertFalse(a.cards.getValue("bad").dirty)
        assertFalse(a.decks.getValue("d1").dirty)
        assertEquals("Mais novo", a.decks.getValue("d1").row.name)
    }

    @Test
    fun `a failed pull keeps the old usn and the next sync finishes the job`() = runTest {
        val server = FakeServer(pageSize = 1)
        val a = InMemorySyncStore()
        repeat(3) { a.putDeck(deck("d$it")) }
        SyncEngine(a, server.remote()).sync()

        val b = InMemorySyncStore()
        server.pullCalls = 0
        server.failPullAtPage = 2
        val failed = SyncEngine(b, server.remote()).sync()
        assertTrue(failed is SyncReport.Failed && failed.stage == SyncReport.Stage.Pull)
        assertEquals(0L, b.usn)

        server.failPullAtPage = null
        SyncEngine(b, server.remote()).sync()
        assertEquals(3, b.decks.size)
        assertEquals(server.usn, b.usn)
    }

    @Test
    fun `pulled reviews never overwrite an existing local review`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        a.putReview(review("r1", "c1", at = 1_000).copy(rating = 1))
        SyncEngine(a, server.remote()).sync()

        val b = InMemorySyncStore()
        b.putReview(review("r1", "c1", at = 1_000).copy(rating = 4), dirty = false)
        SyncEngine(b, server.remote()).sync()
        assertEquals(4, b.reviews.getValue("r1").row.rating)
    }

    @Test
    fun `settings follow last write wins too`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        val b = InMemorySyncStore()
        a.putSettings(SettingsDto(newPerDay = 10, updatedAt = 5_000))
        SyncEngine(a, server.remote()).sync()
        b.putSettings(SettingsDto(newPerDay = 99, updatedAt = 4_000))
        SyncEngine(b, server.remote()).sync()
        assertEquals(10, b.settings!!.row.newPerDay)
        assertFalse(b.settings!!.dirty)
    }

    @Test
    fun `restore replaces local data only after the whole snapshot arrived`() = runTest {
        val server = FakeServer(pageSize = 1)
        val a = InMemorySyncStore()
        a.putDeck(deck("d1"))
        a.putDeck(deck("gone").copy(deletedAt = 1_000))
        a.putCard(card("c1", "d1"))
        SyncEngine(a, server.remote()).sync()

        val b = InMemorySyncStore()
        b.putDeck(deck("local-only", "Só aqui"))
        server.failSnapshot = ApiResult.Failure.CodeMissing
        val failed = SyncEngine(b, server.remote()).restore()
        assertTrue(failed is SyncReport.Failed && failed.failure == ApiResult.Failure.CodeMissing)
        assertTrue(b.decks.containsKey("local-only"))

        server.failSnapshot = null
        val done = SyncEngine(b, server.remote()).restore() as SyncReport.Done
        assertEquals(setOf("d1"), b.decks.keys)
        assertEquals(setOf("c1"), b.cards.keys)
        assertEquals(server.usn, b.usn)
        assertEquals(0, b.dirtyCount())
        assertEquals(2, done.applied)
    }

    @Test
    fun `merge rule only lets a strictly newer dirty local row win`() {
        val dirty = com.archieapps.oklo.data.db.RowVersion("x", 100, true)
        val clean = com.archieapps.oklo.data.db.RowVersion("x", 100, false)
        assertTrue(SyncMerge.incomingWins(null, 1))
        assertTrue(SyncMerge.incomingWins(clean, 1))
        assertFalse(SyncMerge.incomingWins(dirty, 99))
        assertTrue(SyncMerge.incomingWins(dirty, 100))
        assertTrue(SyncMerge.incomingWins(dirty, 101))
    }

    private fun lesson(id: String, deckId: String, source: String = "# Aula", at: Long = 1_000, version: Int = 1, deletedAt: Long? = null) =
        LessonDto(id = id, deckId = deckId, source = source, version = version, ribaltaVersion = "0.3.0", origin = "manual", createdAt = at, updatedAt = at, deletedAt = deletedAt)

    @Test
    fun `lessons and card lesson targets travel from one device to another`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        a.putDeck(deck("d1"))
        a.putCard(card("c1", "d1").copy(lessonTarget = "anchor:fase-clara"))
        a.putLesson(lesson("l1", "d1", source = "# Fotossíntese {#fotossintese}"))

        SyncEngine(a, server.remote()).sync()

        assertEquals(0, a.dirtyCount())
        assertEquals("anchor:fase-clara", server.cards.getValue("c1").row.lessonTarget)
        val b = InMemorySyncStore()
        SyncEngine(b, server.remote()).sync()
        assertEquals("# Fotossíntese {#fotossintese}", b.lessons.getValue("l1").row.source)
        assertEquals("0.3.0", b.lessons.getValue("l1").row.ribaltaVersion)
        assertEquals("anchor:fase-clara", b.cards.getValue("c1").row.lessonTarget)
        assertEquals(0, b.dirtyCount())
    }

    @Test
    fun `lesson last write wins and tombstones propagate`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        val b = InMemorySyncStore()
        a.putLesson(lesson("l1", "d1", source = "original", at = 1_000))
        SyncEngine(a, server.remote()).sync()
        SyncEngine(b, server.remote()).sync()

        a.putLesson(lesson("l1", "d1", source = "A", at = 3_000, version = 2))
        b.putLesson(lesson("l1", "d1", source = "B", at = 2_000, version = 2))
        SyncEngine(a, server.remote()).sync()
        SyncEngine(b, server.remote()).sync()
        SyncEngine(a, server.remote()).sync()

        assertEquals("A", a.lessons.getValue("l1").row.source)
        assertEquals("A", b.lessons.getValue("l1").row.source)
        assertEquals(0, a.dirtyCount())
        assertEquals(0, b.dirtyCount())

        a.putLesson(lesson("l1", "d1", source = "A", at = 4_000, version = 3, deletedAt = 4_000))
        SyncEngine(a, server.remote()).sync()
        SyncEngine(b, server.remote()).sync()
        assertEquals(4_000L, b.lessons.getValue("l1").row.deletedAt)
    }

    @Test
    fun `a second lesson id for the same deck is rejected and recorded`() = runTest {
        val server = FakeServer()
        val a = InMemorySyncStore()
        a.putLesson(lesson("l1", "d1"))
        SyncEngine(a, server.remote()).sync()
        a.putLesson(lesson("l2", "d1", at = 2_000))

        SyncEngine(a, server.remote()).sync()

        assertEquals(listOf("lesson"), a.rejected.map { it.entity })
        assertEquals(setOf("l1"), server.lessons.keys)
        assertFalse(a.lessons.getValue("l2").dirty)
    }

    @Test
    fun `a manual lesson rejected because the deck has a server lesson is kept under the server id and pushed next`() = runTest {
        val server = FakeServer()
        val other = InMemorySyncStore()
        other.putDeck(deck("d1"))
        other.putLesson(lesson("B", "d1", source = "aula da IA", at = 1_000))
        SyncEngine(other, server.remote()).sync()
        val a = InMemorySyncStore()
        a.putLesson(lesson("A", "d1", source = "aula manual", at = 2_000))

        val first = SyncEngine(a, server.remote()).sync() as SyncReport.Done

        assertEquals(1, first.rejected)
        assertEquals(setOf("B"), a.lessons.keys)
        val merged = a.lessons.getValue("B")
        assertEquals("aula manual", merged.row.source)
        assertTrue(merged.dirty)
        assertEquals("aula da IA", server.lessons.getValue("B").row.source)

        val second = SyncEngine(a, server.remote()).sync() as SyncReport.Done

        assertEquals(0, second.rejected)
        assertEquals("aula manual", server.lessons.getValue("B").row.source)
        assertEquals(setOf("B"), server.lessons.keys)
        assertFalse(a.lessons.getValue("B").dirty)
        assertEquals(0, a.dirtyCount())
    }
}
