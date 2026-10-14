package com.archieapps.oklo.domain

import com.archieapps.oklo.core.time.DayWindow
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.data.model.ReviewStat

data class QueueCard(
    val id: String,
    val state: CardState,
    val dueAt: Long?,
    val position: Int,
    val createdAt: Long,
)

data class DailyLimits(
    val newPerDay: Int,
    val reviewsPerDay: Int,
)

data class DailyProgress(
    val newIntroduced: Int,
    val reviewsDone: Int,
) {
    companion object {
        fun from(reviews: List<ReviewStat>, window: DayWindow): DailyProgress {
            val today = reviews.filter { it.reviewedAt in window }
            return DailyProgress(
                newIntroduced = today.filter { it.stateBefore == CardState.New.wire }.map { it.cardId }.distinct().size,
                reviewsDone = today.filter { it.stateBefore == CardState.Review.wire }.map { it.cardId }.distinct().size,
            )
        }
    }
}

data class StudyPlan(
    val learning: List<QueueCard>,
    val reviews: List<QueueCard>,
    val news: List<QueueCard>,
    val learningLater: List<QueueCard>,
    val reviewBacklog: Int = reviews.size,
    val newBacklog: Int = news.size,
) {
    val reviewsCapped: Boolean get() = reviewBacklog > reviews.size
    val newsCapped: Boolean get() = newBacklog > news.size

    val sessionSize: Int get() = learning.size + reviews.size + news.size
}

data class DailySummary(
    val reviews: Int,
    val news: Int,
    val learning: Int,
) {
    val total: Int get() = reviews + news + learning
}

object StudyPlanner {
    const val LEARN_AHEAD_MS: Long = 20L * 60 * 1000

    fun plan(
        cards: List<QueueCard>,
        now: Long,
        window: DayWindow,
        limits: DailyLimits,
        progress: DailyProgress,
    ): StudyPlan {
        val learningAll = cards
            .filter { it.state == CardState.Learning || it.state == CardState.Relearning }
            .filter { (it.dueAt ?: 0L) < window.end }
            .sortedBy { it.dueAt ?: 0L }
        val learningNow = learningAll.filter { (it.dueAt ?: 0L) <= now + LEARN_AHEAD_MS }
        val learningLater = learningAll - learningNow.toSet()
        val reviewRoom = (limits.reviewsPerDay - progress.reviewsDone).coerceAtLeast(0)
        val reviewsDue = cards
            .filter { it.state == CardState.Review && (it.dueAt ?: 0L) < window.end }
            .sortedWith(compareBy<QueueCard> { it.dueAt ?: 0L }.thenBy { it.id })
        val reviews = reviewsDue.take(reviewRoom)
        val newRoom = (limits.newPerDay - progress.newIntroduced).coerceAtLeast(0)
        val newAll = cards
            .filter { it.state == CardState.New }
            .sortedWith(compareBy<QueueCard> { it.position }.thenBy { it.createdAt }.thenBy { it.id })
        val news = newAll.take(newRoom)
        return StudyPlan(learningNow, reviews, news, learningLater, reviewsDue.size, newAll.size)
    }

    fun summary(
        cards: List<QueueCard>,
        now: Long,
        window: DayWindow,
        limits: DailyLimits,
        progress: DailyProgress,
    ): DailySummary {
        val plan = plan(cards, now, window, limits, progress)
        return DailySummary(
            reviews = plan.reviews.size,
            news = plan.news.size,
            learning = plan.learning.size + plan.learningLater.size,
        )
    }
}

class StudySession(
    plan: StudyPlan,
    private val learnAheadMs: Long = StudyPlanner.LEARN_AHEAD_MS,
) {
    private val main = ArrayDeque<String>().apply {
        plan.reviews.forEach { add(it.id) }
        plan.news.forEach { add(it.id) }
    }

    private val learning = ArrayList<Pair<String, Long>>().apply {
        plan.learning.forEach { add(it.id to (it.dueAt ?: 0L)) }
        plan.learningLater.forEach { add(it.id to (it.dueAt ?: 0L)) }
        sortBy { it.second }
    }

    private val seen = LinkedHashSet<String>()

    var answered: Int = 0
        private set

    val seenCount: Int get() = seen.size

    fun next(now: Long): String? {
        val dueLearning = learning.firstOrNull()
        if (dueLearning != null && dueLearning.second <= now) {
            learning.removeAt(0)
            return dueLearning.first.also { seen.add(it) }
        }
        if (main.isNotEmpty()) {
            return main.removeFirst().also { seen.add(it) }
        }
        if (dueLearning != null && dueLearning.second <= now + learnAheadMs) {
            learning.removeAt(0)
            return dueLearning.first.also { seen.add(it) }
        }
        return null
    }

    fun answered(cardId: String, newState: CardState, newDue: Long, dayEnd: Long) {
        answered++
        learning.removeAll { it.first == cardId }
        main.remove(cardId)
        if ((newState == CardState.Learning || newState == CardState.Relearning) && newDue < dayEnd) {
            learning.add(cardId to newDue)
            learning.sortBy { it.second }
        }
    }

    fun skip(cardId: String) {
        learning.removeAll { it.first == cardId }
        main.remove(cardId)
    }

    fun remaining(now: Long): Int = main.size + learning.count { it.second <= now + learnAheadMs }

    fun laterToday(now: Long): List<Long> = learning.filter { it.second > now + learnAheadMs }.map { it.second }
}
