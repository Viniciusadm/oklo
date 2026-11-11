package com.archieapps.oklo.core.sched

import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.data.model.CardState

data class SchedulingCard(
    val id: String,
    val state: CardState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val easeFactor: Int?,
    val intervalDays: Int,
    val dueAt: Long?,
    val lastReviewAt: Long?,
    val reps: Int,
)

data class SchedulingOutcome(
    val state: CardState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val easeFactor: Int?,
    val intervalDays: Int,
    val dueAt: Long,
)

interface CardScheduler {
    fun preview(card: SchedulingCard, now: Long): Map<Rating, SchedulingOutcome>

    fun review(card: SchedulingCard, rating: Rating, now: Long): SchedulingOutcome = preview(card, now).getValue(rating)
}

enum class SchedulerKind(val wire: String, val label: String) {
    Sm2("sm2", "SM-2 (padrão do Anki)"),
    Fsrs("fsrs", "FSRS");

    companion object {
        fun of(wire: String?): SchedulerKind = entries.firstOrNull { it.wire == wire } ?: Sm2
    }
}
