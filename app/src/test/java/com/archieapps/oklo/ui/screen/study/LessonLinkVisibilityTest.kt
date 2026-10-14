package com.archieapps.oklo.ui.screen.study

import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.data.model.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonLinkVisibilityTest {
    private fun question(revealed: Boolean, target: String?, lessonAvailable: Boolean) = StudyUi.Question(
        deckName = "D",
        card = Card(
            id = "c1", deckId = "d1", content = CardContent(CardType.Basic, "F", back = "V"), state = CardState.New,
            step = null, stability = null, difficulty = null, dueAt = null, lastReviewAt = null, reps = 0, lapses = 0,
            suspended = false, position = 0, createdAt = 1, updatedAt = 1, lessonTarget = target,
        ),
        position = 1, total = 3, revealed = revealed, response = null, answeredCorrect = null,
        intervals = emptyMap(), busy = false, lessonAvailable = lessonAvailable,
    )

    @Test
    fun `hidden before the card is flipped`() {
        assertFalse(question(false, "anchor:x", true).canViewInLesson)
    }

    @Test
    fun `shown after flip with a target and a lesson`() {
        assertTrue(question(true, "anchor:x", true).canViewInLesson)
    }

    @Test
    fun `hidden without a target`() {
        assertFalse(question(true, null, true).canViewInLesson)
    }

    @Test
    fun `hidden when the deck has no lesson`() {
        assertFalse(question(true, "anchor:x", false).canViewInLesson)
    }

    @Test
    fun `flipping keeps the same card and the link`() {
        val before = question(false, "anchor:x", true)
        val after = before.copy(revealed = true)
        assertEquals(before.card, after.card)
        assertTrue(after.canViewInLesson)
    }
}
