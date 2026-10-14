package com.archieapps.oklo.domain

import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.data.model.ReviewStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StudyQueueTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val window = LocalDay.of(LocalDate.of(2026, 10, 8), zone)
    private val now = window.start + 9 * 3_600_000L
    private val minute = 60_000L

    private fun card(id: String, state: CardState, due: Long? = null, position: Int = 0) =
        QueueCard(id, state, due, position, 0)

    @Test
    fun `daily limits subtract what was already done today`() {
        val cards = (1..10).map { card("n$it", CardState.New, position = it) } +
            (1..10).map { card("r$it", CardState.Review, due = now - it * minute) }
        val reviews = listOf(
            ReviewStat("old", 3, "new", window.start - 1, 0),
            ReviewStat("x1", 3, "new", now - 1, 0),
            ReviewStat("x1", 3, "learning", now - 1, 0),
            ReviewStat("x2", 3, "new", now - 2, 0),
            ReviewStat("y1", 3, "review", now - 3, 0),
        )
        val progress = DailyProgress.from(reviews, window)
        assertEquals(2, progress.newIntroduced)
        assertEquals(1, progress.reviewsDone)
        val plan = StudyPlanner.plan(cards, now, window, DailyLimits(newPerDay = 5, reviewsPerDay = 4), progress)
        assertEquals(listOf("n1", "n2", "n3"), plan.news.map { it.id })
        assertEquals(3, plan.reviews.size)
        assertEquals("r10", plan.reviews.first().id)
        assertTrue(plan.newsCapped)
        assertTrue(plan.reviewsCapped)
    }

    @Test
    fun `reviews due later today are included but tomorrow is not, and learning splits by learn ahead`() {
        val cards = listOf(
            card("today", CardState.Review, due = window.end - 1),
            card("tomorrow", CardState.Review, due = window.end),
            card("soon", CardState.Learning, due = now + 15 * minute),
            card("later", CardState.Relearning, due = now + 3 * 60 * minute),
            card("nextDay", CardState.Learning, due = window.end + 5),
        )
        val plan = StudyPlanner.plan(cards, now, window, DailyLimits(20, 200), DailyProgress(0, 0))
        assertEquals(listOf("today"), plan.reviews.map { it.id })
        assertEquals(listOf("soon"), plan.learning.map { it.id })
        assertEquals(listOf("later"), plan.learningLater.map { it.id })
        val summary = StudyPlanner.summary(cards, now, window, DailyLimits(20, 200), DailyProgress(0, 0))
        assertEquals(1, summary.reviews)
        assertEquals(2, summary.learning)
    }

    @Test
    fun `session serves due learning first, then reviews and new, and reinserts relearned cards`() {
        val plan = StudyPlan(
            learning = listOf(card("l1", CardState.Learning, due = now - minute)),
            reviews = listOf(card("r1", CardState.Review, due = now - 10)),
            news = listOf(card("n1", CardState.New)),
            learningLater = emptyList(),
        )
        val session = StudySession(plan)
        assertEquals("l1", session.next(now))
        session.answered("l1", CardState.Review, now + 3 * 86_400_000L, window.end)
        assertEquals("r1", session.next(now))
        session.answered("r1", CardState.Relearning, now + 10 * minute, window.end)
        assertEquals("n1", session.next(now))
        session.answered("n1", CardState.Learning, now + minute, window.end)
        assertEquals(2, session.remaining(now))
        assertEquals("n1", session.next(now + 30_000))
        session.answered("n1", CardState.Learning, now + 30_000 + 10 * minute, window.end)
        assertEquals("r1", session.next(now + 60_000))
        session.answered("r1", CardState.Review, now + 86_400_000L, window.end)
        assertEquals("n1", session.next(now + 2 * minute))
        session.answered("n1", CardState.Review, now + 86_400_000L, window.end)
        assertNull(session.next(now + 3 * minute))
        assertEquals(6, session.answered)
        assertEquals(3, session.seenCount)
    }

    @Test
    fun `learn ahead only pulls cards due within twenty minutes`() {
        val plan = StudyPlan(emptyList(), emptyList(), listOf(card("n1", CardState.New)), emptyList())
        val session = StudySession(plan)
        assertEquals("n1", session.next(now))
        session.answered("n1", CardState.Learning, now + 25 * minute, window.end)
        assertNull(session.next(now))
        assertEquals(listOf(now + 25 * minute), session.laterToday(now))
        assertEquals("n1", session.next(now + 6 * minute))
    }

    @Test
    fun `learning cards that fall due after the local day ends leave the session`() {
        val plan = StudyPlan(emptyList(), emptyList(), listOf(card("n1", CardState.New)), emptyList())
        val session = StudySession(plan)
        session.next(window.end - minute)
        session.answered("n1", CardState.Learning, window.end + 9 * minute, window.end)
        assertNull(session.next(window.end))
        assertTrue(session.laterToday(window.end).isEmpty())
    }
}
