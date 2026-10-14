package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.LessonDto
import com.archieapps.oklo.data.db.LessonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonDeckMergeTest {
    private val incoming = LessonDto(
        id = "server", deckId = "d1", source = "servidor", version = 3, ribaltaVersion = "0.3.0",
        origin = "ai", createdAt = 1_000, updatedAt = 5_000, deletedAt = 4_000,
    )

    private fun local(dirty: Boolean) = LessonEntity(
        id = "local", deckId = "d1", source = "meu texto", version = 2, ribaltaVersion = "0.3.0",
        origin = "manual", createdAt = 900, updatedAt = 2_000, deletedAt = null, dirty = dirty,
    )

    @Test
    fun `without a competing local lesson the incoming row is written clean`() {
        val result = LessonDeckMerge.resolve(null, incoming)

        assertEquals("server", result.id)
        assertEquals("servidor", result.source)
        assertFalse(result.dirty)
    }

    @Test
    fun `a clean competing local lesson is simply replaced`() {
        val result = LessonDeckMerge.resolve(local(dirty = false), incoming)

        assertEquals("servidor", result.source)
        assertFalse(result.dirty)
    }

    @Test
    fun `a dirty competing local lesson keeps its source under the server id and stays dirty`() {
        val result = LessonDeckMerge.resolve(local(dirty = true), incoming)

        assertEquals("server", result.id)
        assertEquals("meu texto", result.source)
        assertEquals("manual", result.origin)
        assertEquals(4, result.version)
        assertEquals(5_001L, result.updatedAt)
        assertNull(result.deletedAt)
        assertTrue(result.dirty)
    }
}
