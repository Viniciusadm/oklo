package com.archieapps.oklo.ui.screen.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonLinksTest {
    private val options = listOf(
        LinkOption("page:intro", "Introdução", "Introdução"),
        LinkOption("anchor:clara", "Fase clara nos tilacoides", "Fotossíntese"),
        LinkOption("anchor:escura", "Ciclo de Calvin", "Fotossíntese"),
    )
    private val source = LessonTargetSource { raw -> if (raw == "lesson") options else emptyList() }

    @Test
    fun `no lesson means no field and no options`() {
        val link = LessonLinks.build(lessonSource = null, current = "anchor:clara", targets = source)
        assertFalse(link.hasLesson)
        assertTrue(link.options.isEmpty())
        assertEquals(LinkStatus.None, link.status)
    }

    @Test
    fun `options come from the lesson targets`() {
        val link = LessonLinks.build("lesson", null, source)
        assertTrue(link.hasLesson)
        assertEquals(options, link.options)
        assertEquals(LinkStatus.None, link.status)
    }

    @Test
    fun `an existing target resolves to its option`() {
        val link = LessonLinks.build("lesson", "anchor:escura", source)
        assertEquals(LinkStatus.Linked(options[2]), link.status)
    }

    @Test
    fun `a target missing from the lesson is broken`() {
        val link = LessonLinks.build("lesson", "anchor:removida", source)
        assertEquals(LinkStatus.Broken("anchor:removida"), link.status)
    }

    @Test
    fun `a blank target counts as no link`() {
        assertEquals(LinkStatus.None, LessonLinks.build("lesson", "", source).status)
    }

    @Test
    fun `search matches label and page title ignoring case and accents`() {
        assertEquals(listOf(options[1]), LessonLinks.filter(options, "TILACOIDES"))
        assertEquals(listOf(options[1], options[2]), LessonLinks.filter(options, "fotossintese"))
        assertEquals(options, LessonLinks.filter(options, "  "))
    }
}
