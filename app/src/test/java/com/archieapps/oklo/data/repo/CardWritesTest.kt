package com.archieapps.oklo.data.repo

import com.archieapps.oklo.data.db.CardEntity
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardWritesTest {
    private val content = CardContent(CardType.Basic, "Frente", back = "Verso")

    private fun entity(target: String?) = CardEntity(
        id = "c1", deckId = "d1", type = "basic", front = "F", back = "V", explanation = null, answer = null,
        choicesJson = null, state = "new", step = null, stability = null, difficulty = null, dueAt = null,
        lastReviewAt = null, reps = 0, lapses = 0, suspended = false, position = 0, createdAt = 100,
        updatedAt = 100, deletedAt = null, lessonTarget = target, dirty = false,
    )

    @Test
    fun `edit sets the lesson target and marks dirty`() {
        val edited = CardWrites.edit(entity(null), content, "anchor:clara", 500)
        assertEquals("anchor:clara", edited.lessonTarget)
        assertTrue(edited.dirty)
        assertEquals("Frente", edited.front)
    }

    @Test
    fun `edit clears the lesson target`() {
        val edited = CardWrites.edit(entity("page:intro"), content, null, 500)
        assertNull(edited.lessonTarget)
        assertTrue(edited.dirty)
    }

    @Test
    fun `edit keeps a broken target untouched when the caller passes it back`() {
        val edited = CardWrites.edit(entity("anchor:removida"), content, "anchor:removida", 500)
        assertEquals("anchor:removida", edited.lessonTarget)
    }

    @Test
    fun `a blank target is stored as null`() {
        assertNull(CardWrites.edit(entity("page:intro"), content, "  ", 500).lessonTarget)
    }

    @Test
    fun `a target over 120 bytes is rejected`() {
        val tooLong = "anchor:" + "a".repeat(114)
        val failure = runCatching { CardWrites.edit(entity(null), content, tooLong, 500) }.exceptionOrNull()
        assertTrue(failure is CardValidationException)
    }

    @Test
    fun `fresh cards carry the target and start dirty`() {
        val created = CardWrites.fresh("id", "d1", content, "page:intro", 3, 500)
        assertEquals("page:intro", created.lessonTarget)
        assertTrue(created.dirty)
        assertEquals(3, created.position)
    }

    @Test
    fun `a target outside the id grammar is rejected`() {
        listOf("anchor:custo\$alto", "anchor:", "pagina:intro", "anchor:com espaço", "anchor:ação").forEach { raw ->
            val failure = runCatching { CardWrites.edit(entity(null), content, raw, 500) }.exceptionOrNull()
            assertTrue(raw, failure is CardValidationException)
        }
    }

    @Test
    fun `supported targets follow the shared grammar`() {
        assertTrue(CardWrites.isSupportedTarget("anchor:fase-clara_2"))
        assertTrue(CardWrites.isSupportedTarget("page:Intro"))
        assertFalse(CardWrites.isSupportedTarget("anchor:custo\$alto"))
        assertFalse(CardWrites.isSupportedTarget("custo"))
    }

    private fun card(id: String, target: String?, deletedAt: Long? = null) =
        entity(target).copy(id = id, deletedAt = deletedAt)

    @Test
    fun `relink applies links as dirty edits with a later stamp`() {
        val changed = CardWrites.relink(listOf(card("c1", null)), mapOf("c1" to "anchor:clara"), null, 50)
        val edited = changed.single()
        assertEquals("anchor:clara", edited.lessonTarget)
        assertTrue(edited.dirty)
        assertEquals(101, edited.updatedAt)
    }

    @Test
    fun `relink clears targets missing from the new lesson unless re-linked`() {
        val cards = listOf(
            card("orphan", "anchor:velha"),
            card("kept", "anchor:clara"),
            card("relinked", "anchor:velha"),
            card("free", null),
        )
        val changed = CardWrites.relink(cards, mapOf("relinked" to "page:intro"), setOf("anchor:clara", "page:intro"), 500)
        assertEquals(mapOf("orphan" to null, "relinked" to "page:intro"), changed.associate { it.id to it.lessonTarget })
        assertTrue(changed.all { it.dirty && it.updatedAt == 500L })
    }

    @Test
    fun `relink without targets to keep never clears`() {
        val changed = CardWrites.relink(listOf(card("orphan", "anchor:velha")), emptyMap(), null, 500)
        assertTrue(changed.isEmpty())
    }

    @Test
    fun `relink skips deleted cards, unchanged cards and unsupported targets`() {
        val cards = listOf(card("gone", null, deletedAt = 9), card("same", "anchor:clara"), card("bad", null))
        val links = mapOf("gone" to "anchor:clara", "same" to "anchor:clara", "bad" to "anchor:custo\$alto")
        assertTrue(CardWrites.relink(cards, links, setOf("anchor:clara"), 500).isEmpty())
    }
}
