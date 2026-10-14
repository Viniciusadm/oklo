package com.archieapps.oklo.domain

import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardStat
import com.archieapps.oklo.data.model.ReviewStat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StatsCalculatorTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDay.of(LocalDate.of(2026, 10, 8), zone)
    private val now = today.start + 12 * 3_600_000L

    @Test
    fun `summarizes today, the window and the distribution`() {
        val cards = listOf(
            CardStat("a", "d", "new", null, false, 0),
            CardStat("b", "d", "review", now - 1, false, 0),
            CardStat("c", "d", "learning", now + 1, false, 0),
            CardStat("e", "d", "relearning", now + 1, true, 0),
        )
        val reviews = listOf(
            ReviewStat("b", 3, "review", now - 1_000, 5_000),
            ReviewStat("b", 1, "review", now - 2_000, 4_000),
            ReviewStat("c", 3, "new", today.start - 1, 7_000),
            ReviewStat("c", 3, "new", today.start - 6 * 86_400_000L, 1_000),
            ReviewStat("c", 3, "new", today.start - 8 * 86_400_000L, 1_000),
        )
        val stats = StatsCalculator.compute(2, cards, reviews, now, 7, zone)
        assertEquals(4, stats.totalCards)
        assertEquals(2, stats.reviewedToday)
        assertEquals(1, stats.cardsReviewedToday)
        assertEquals(9_000L, stats.timeTodayMs)
        assertEquals(2, stats.pending)
        assertEquals(7, stats.activity.size)
        assertEquals(LocalDate.of(2026, 10, 2), stats.activity.first().date)
        assertEquals(listOf(1, 0, 0, 0, 0, 1, 2), stats.activity.map { it.reviews })
        assertEquals(3, stats.activeDays)
        assertEquals(Distribution(1, 1, 1, 0, 1), stats.distribution)
        assertEquals(0.75, stats.correctRate!!, 1e-9)
    }
}
