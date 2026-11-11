package com.archieapps.oklo.core.sched

import com.archieapps.oklo.core.fsrs.FsrsCard
import com.archieapps.oklo.core.fsrs.FsrsParameters
import com.archieapps.oklo.core.fsrs.FsrsScheduler
import com.archieapps.oklo.core.fsrs.FsrsState
import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardState
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

class FsrsCardScheduler(
    private val parameters: FsrsParameters = FsrsParameters(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : CardScheduler {
    private val core = FsrsScheduler(parameters.copy(enableFuzz = false))
    private val maximum = parameters.maximumIntervalDays

    override fun preview(card: SchedulingCard, now: Long): Map<Rating, SchedulingOutcome> {
        val raw = core.preview(toFsrs(card, now), now)
        val days = raw.filterValues { it.state == FsrsState.Review }
            .mapValues { (_, next) -> max(1L, (next.due - now) / FsrsScheduler.DAY).toInt() }
            .toMutableMap()
        val fuzz = if (parameters.enableFuzz) Fuzz.factor(card.id, card.reps) else null
        val ordered = mutableMapOf<Rating, Int>()
        var floor = 1
        val good = days[Rating.Good]
        listOf(Rating.Again, Rating.Hard, Rating.Good, Rating.Easy).forEach { rating ->
            val target = days[rating] ?: return@forEach
            val capped = if (rating == Rating.Hard && good != null) min(target, good) else target
            val minimum = if (rating == Rating.Again) 1 else floor
            val value = Fuzz.apply(max(capped, minimum).toDouble(), minimum, maximum, fuzz)
            ordered[rating] = value
            if (rating != Rating.Again) floor = value + 1
        }
        return raw.mapValues { (rating, next) ->
            val state = stateOf(next.state)
            val reviewDays = ordered[rating]
            SchedulingOutcome(
                state = state,
                step = next.step,
                stability = next.stability,
                difficulty = next.difficulty,
                easeFactor = card.easeFactor,
                intervalDays = reviewDays ?: if (state == CardState.Relearning) card.intervalDays else 0,
                dueAt = reviewDays?.let { LocalDay.startAfterDays(now, it.toLong(), zone) } ?: learningDue(next.due, now),
            )
        }
    }

    private fun learningDue(due: Long, now: Long): Long {
        val delay = due - now
        if (delay < FsrsScheduler.DAY) return due
        return LocalDay.startAfterDays(now, delay / FsrsScheduler.DAY, zone)
    }

    private fun toFsrs(card: SchedulingCard, now: Long): FsrsCard {
        val lastReview = card.lastReviewAt?.let { last ->
            val days = LocalDay.daysBetween(last, now, zone)
            if (days <= 0) maxOf(last, now - FsrsScheduler.DAY + 1) else now - days * FsrsScheduler.DAY
        }
        val due = card.dueAt ?: now
        return when (card.state) {
            CardState.New -> FsrsCard(FsrsState.Learning, 0, null, null, due, null)
            CardState.Learning -> FsrsCard(FsrsState.Learning, card.step ?: 0, card.stability, card.difficulty, due, lastReview)
            CardState.Review -> FsrsCard(FsrsState.Review, null, card.stability, card.difficulty, due, lastReview)
            CardState.Relearning -> FsrsCard(FsrsState.Relearning, card.step ?: 0, card.stability, card.difficulty, due, lastReview)
        }
    }

    private fun stateOf(state: FsrsState): CardState = when (state) {
        FsrsState.Learning -> CardState.Learning
        FsrsState.Review -> CardState.Review
        FsrsState.Relearning -> CardState.Relearning
    }
}
