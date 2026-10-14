package com.archieapps.oklo.domain

import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardStat
import com.archieapps.oklo.data.model.Deck
import com.archieapps.oklo.data.model.LibrarySort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DeckTreeTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val window = LocalDay.of(LocalDate.of(2026, 10, 8), zone)
    private val now = window.start + 10 * 3_600_000L

    private fun deck(id: String, name: String, parent: String? = null, created: Long = 0, studied: Long? = null, position: Int = 0) =
        Deck(id, parent, name, position, studied, created, created)

    private fun stat(id: String, deck: String, state: String, due: Long? = null, suspended: Boolean = false) =
        CardStat(id, deck, state, due, suspended, 0)

    private val decks = listOf(
        deck("prog", "Programação"),
        deck("php", "PHP", "prog"),
        deck("laravel", "Laravel", "php"),
        deck("composer", "Composer", "php"),
        deck("js", "JavaScript", "prog"),
        deck("hist", "História"),
        deck("br", "Brasil", "hist"),
        deck("rep", "República", "br"),
    )

    private val stats = listOf(
        stat("1", "laravel", "new"),
        stat("2", "laravel", "review", due = now - 1),
        stat("3", "laravel", "review", due = window.end + 1),
        stat("4", "laravel", "learning", due = now + 60_000),
        stat("5", "composer", "review", due = window.end - 1),
        stat("6", "composer", "new", suspended = true),
        stat("7", "rep", "relearning", due = now - 5),
    )

    @Test
    fun `aggregates counts through arbitrary depth`() {
        val roots = DeckTree.build(decks, stats, window, LibrarySort.Name)
        val prog = roots.first { it.id == "prog" }
        assertEquals(6, prog.aggregate.total)
        assertEquals(1, prog.aggregate.newCount)
        assertEquals(2, prog.aggregate.reviewDue)
        assertEquals(1, prog.aggregate.learningDue)
        assertEquals(1, prog.aggregate.suspended)
        assertEquals(4, prog.descendantCount)
        val laravel = DeckTree.find(roots, "laravel")!!
        assertEquals(listOf("Programação", "PHP", "Laravel"), laravel.path)
        assertEquals(2, laravel.depth)
        assertEquals(2, laravel.own.pending)
        val republica = DeckTree.find(roots, "rep")!!
        assertEquals("História / Brasil / República", republica.pathLabel)
        assertEquals(1, roots.first { it.id == "hist" }.aggregate.learningDue)
    }

    @Test
    fun `orphans and cycles become roots instead of disappearing`() {
        val broken = listOf(
            deck("a", "A", parent = "b"),
            deck("b", "B", parent = "a"),
            deck("orphan", "Órfão", parent = "missing"),
            deck("child", "Filho", parent = "orphan"),
        )
        val roots = DeckTree.build(broken, emptyList(), window, LibrarySort.Name)
        val flat = DeckTree.flatten(roots).map { it.id }.toSet()
        assertEquals(setOf("a", "b", "orphan", "child"), flat)
        assertTrue(roots.any { it.id == "orphan" })
    }

    @Test
    fun `visible rows follow the expanded set`() {
        val roots = DeckTree.build(decks, stats, window, LibrarySort.Name)
        assertEquals(listOf("hist", "prog"), DeckTree.visible(roots, emptySet()).map { it.id })
        assertEquals(listOf("hist", "prog", "js", "php"), DeckTree.visible(roots, setOf("prog")).map { it.id })
        assertEquals(
            listOf("hist", "prog", "js", "php", "composer", "laravel"),
            DeckTree.visible(roots, setOf("prog", "php")).map { it.id },
        )
    }

    @Test
    fun `search ignores accents and case and shows the full path`() {
        val withDuplicate = decks + deck("php2", "PHP", "hist")
        val roots = DeckTree.build(withDuplicate, stats, window, LibrarySort.Name)
        val results = DeckTree.search(roots, "republica", DeckFilter.All, now)
        assertEquals(listOf("História / Brasil / República"), results.map { it.pathLabel })
        val php = DeckTree.search(roots, "php", DeckFilter.All, now).map { it.pathLabel }
        assertEquals(listOf("História / PHP", "Programação / PHP"), php)
    }

    @Test
    fun `filters by pending, new and recently created`() {
        val recent = decks.map { if (it.id == "js") it.copy(createdAt = now - 3_600_000) else it.copy(createdAt = now - 30L * 86_400_000) }
        val roots = DeckTree.build(recent, stats, window, LibrarySort.Name)
        val pending = DeckTree.search(roots, "", DeckFilter.Pending, now).map { it.id }.toSet()
        assertEquals(setOf("prog", "php", "laravel", "composer", "hist", "br", "rep"), pending)
        val withNew = DeckTree.search(roots, "", DeckFilter.WithNew, now).map { it.id }.toSet()
        assertEquals(setOf("prog", "php", "laravel"), withNew)
        assertEquals(listOf("js"), DeckTree.search(roots, "", DeckFilter.Recent, now).map { it.id })
    }

    @Test
    fun `sorts by name, recent activity or card count`() {
        val list = listOf(
            deck("b", "beta"),
            deck("a", "Álgebra"),
            deck("c", "cálculo"),
        )
        val studied = mapOf("b" to 10L, "a" to 50L, "c" to 30L)
        val cards = listOf(stat("1", "c", "new"), stat("2", "c", "new"), stat("3", "b", "new"))
        assertEquals(listOf("a", "b", "c"), DeckTree.build(list, cards, window, LibrarySort.Name).map { it.id })
        assertEquals(listOf("a", "c", "b"), DeckTree.build(list, cards, window, LibrarySort.Recent, studied).map { it.id })
        assertEquals(listOf("c", "b", "a"), DeckTree.build(list, cards, window, LibrarySort.Cards).map { it.id })
    }

    @Test
    fun `last studied is derived from reviews and rolls up through subdecks`() {
        val studied = mapOf("laravel" to 500L, "composer" to 900L, "rep" to 300L)
        val roots = DeckTree.build(decks, stats, window, LibrarySort.Recent, studied)
        assertEquals(900L, DeckTree.find(roots, "prog")!!.lastStudied)
        assertEquals(900L, DeckTree.find(roots, "php")!!.lastStudied)
        assertEquals(500L, DeckTree.find(roots, "laravel")!!.lastStudied)
        assertEquals(null, DeckTree.find(roots, "js")!!.lastStudied)
        assertEquals(300L, DeckTree.find(roots, "hist")!!.lastStudied)
        assertEquals(listOf("prog", "hist"), roots.map { it.id })
    }

    @Test
    fun `a deck can not be moved into itself or its descendants`() {
        assertFalse(DeckTree.canMove(decks, "prog", "laravel"))
        assertFalse(DeckTree.canMove(decks, "php", "php"))
        assertTrue(DeckTree.canMove(decks, "laravel", "hist"))
        assertTrue(DeckTree.canMove(decks, "laravel", null))
        assertEquals(setOf("php", "laravel", "composer", "js"), DeckTree.descendantIds(decks, "prog"))
        assertEquals(listOf("prog", "php", "laravel"), DeckTree.ancestors(decks, "laravel").map { it.id })
    }
}
