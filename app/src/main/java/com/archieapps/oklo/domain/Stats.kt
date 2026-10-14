package com.archieapps.oklo.domain

import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardStat
import com.archieapps.oklo.data.model.ReviewStat
import java.time.LocalDate
import java.time.ZoneId

data class DayActivity(
    val date: LocalDate,
    val reviews: Int,
    val durationMs: Long,
)

data class Distribution(
    val newCount: Int,
    val learning: Int,
    val review: Int,
    val relearning: Int,
    val suspended: Int,
) {
    val total: Int get() = newCount + learning + review + relearning + suspended
}

data class StatsSummary(
    val totalCards: Int,
    val totalDecks: Int,
    val reviewedToday: Int,
    val cardsReviewedToday: Int,
    val pending: Int,
    val timeTodayMs: Long,
    val timeWindowMs: Long,
    val activity: List<DayActivity>,
    val distribution: Distribution,
    val activeDays: Int,
    val correctRate: Double?,
)

object StatsCalculator {
    fun compute(
        deckCount: Int,
        cards: List<CardStat>,
        reviews: List<ReviewStat>,
        now: Long,
        days: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StatsSummary {
        val window = LocalDay.window(now, zone)
        val today = reviews.filter { it.reviewedAt in window }
        val firstDate = window.date.minusDays((days - 1).toLong())
        val byDate = reviews.groupBy { LocalDay.dateOf(it.reviewedAt, zone) }
        val activity = (0 until days).map { offset ->
            val date = firstDate.plusDays(offset.toLong())
            val items = byDate[date].orEmpty()
            DayActivity(date, items.size, items.sumOf { it.durationMs })
        }
        val counts = DeckTree.countCards(cards, window)
        val distribution = Distribution(
            newCount = cards.count { !it.suspended && it.state == "new" },
            learning = cards.count { !it.suspended && it.state == "learning" },
            review = cards.count { !it.suspended && it.state == "review" },
            relearning = cards.count { !it.suspended && it.state == "relearning" },
            suspended = cards.count { it.suspended },
        )
        val windowReviews = activity.sumOf { it.reviews }
        val correct = reviews.filter { LocalDay.dateOf(it.reviewedAt, zone) >= firstDate }
        val correctRate = if (correct.isEmpty()) null else correct.count { it.rating > 1 }.toDouble() / correct.size
        return StatsSummary(
            totalCards = cards.size,
            totalDecks = deckCount,
            reviewedToday = today.size,
            cardsReviewedToday = today.map { it.cardId }.distinct().size,
            pending = counts.pending,
            timeTodayMs = today.sumOf { it.durationMs },
            timeWindowMs = activity.sumOf { it.durationMs },
            activity = activity,
            distribution = distribution,
            activeDays = activity.count { it.reviews > 0 }.takeIf { windowReviews > 0 } ?: 0,
            correctRate = correctRate,
        )
    }
}
