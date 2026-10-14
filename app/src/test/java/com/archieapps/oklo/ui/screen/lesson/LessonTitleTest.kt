package com.archieapps.oklo.ui.screen.lesson

import org.junit.Assert.assertEquals
import org.junit.Test

class LessonTitleTest {
    @Test
    fun usesFirstHeadingWithoutAnchor() {
        assertEquals("Fotossíntese", lessonTitle("@title X\n\n# Fotossíntese {#fotossintese}\n\ntexto", "Deck"))
    }

    @Test
    fun fallsBackToDeckName() {
        assertEquals("Deck", lessonTitle("só texto\n\n- item", "Deck"))
    }

    @Test
    fun acceptsDeeperHeadings() {
        assertEquals("Sub", lessonTitle("texto\n## Sub", "Deck"))
    }
}
