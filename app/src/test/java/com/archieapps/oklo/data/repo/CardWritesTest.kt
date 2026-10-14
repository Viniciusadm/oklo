package com.archieapps.oklo.data.repo

import com.archieapps.oklo.data.db.CardEntity
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import org.junit.Assert.assertEquals
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
}
