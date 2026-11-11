package com.archieapps.oklo.core.sched

import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardState
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class Sm2Config(
    val learningStepsMinutes: List<Int> = listOf(1, 10),
    val relearningStepsMinutes: List<Int> = listOf(10),
    val graduatingIntervalDays: Int = 1,
    val easyIntervalDays: Int = 4,
    val startingEase: Double = 2.5,
    val easyBonus: Double = 1.3,
    val intervalModifier: Double = 1.0,
    val hardIntervalFactor: Double = 1.2,
    val newIntervalFactor: Double = 0.0,
    val minimumIntervalDays: Int = 1,
    val maximumIntervalDays: Int = 36500,
    val enableFuzz: Boolean = true,
)

class Sm2Scheduler(
    private val config: Sm2Config = Sm2Config(),
    private val zone: ZoneId = ZoneId.systemDefault(),
) : CardScheduler {
    private val learningSteps = config.learningStepsMinutes.map { it * MINUTE }
    private val relearningSteps = config.relearningStepsMinutes.map { it * MINUTE }
    private val startingEase = (config.startingEase * 1000).roundToInt()
    private val maximum = config.maximumIntervalDays

    override fun preview(card: SchedulingCard, now: Long): Map<Rating, SchedulingOutcome> {
        val fuzz = if (config.enableFuzz) Fuzz.factor(card.id, card.reps) else null
        return when (card.state) {
            CardState.New, CardState.Learning -> learning(card, now, fuzz)
            CardState.Relearning -> relearning(card, now, fuzz)
            CardState.Review -> reviewing(card, now, fuzz)
        }
    }

    private fun learning(card: SchedulingCard, now: Long, fuzz: Double?): Map<Rating, SchedulingOutcome> {
        val ease = card.easeFactor ?: startingEase
        val good = Fuzz.apply(config.graduatingIntervalDays.toDouble(), 1, maximum, fuzz)
        val easyTarget = max(config.easyIntervalDays, good + 1).toDouble()
        val easy = Fuzz.apply(easyTarget, good + 1, maximum, fuzz)
        val graduateGood = graduate(card, ease, good, now)
        val graduateEasy = graduate(card, ease, easy, now)
        if (learningSteps.isEmpty()) {
            return mapOf(Rating.Again to graduateGood, Rating.Hard to graduateGood, Rating.Good to graduateGood, Rating.Easy to graduateEasy)
        }
        val step = if (card.state == CardState.New) 0 else (card.step ?: 0).coerceIn(0, learningSteps.lastIndex)
        return mapOf(
            Rating.Again to stepped(card, CardState.Learning, 0, learningSteps[0], ease, 0, now),
            Rating.Hard to stepped(card, CardState.Learning, step, hardDelay(learningSteps, step), ease, 0, now),
            Rating.Good to if (step + 1 < learningSteps.size) {
                stepped(card, CardState.Learning, step + 1, learningSteps[step + 1], ease, 0, now)
            } else {
                graduateGood
            },
            Rating.Easy to graduateEasy,
        )
    }

    private fun relearning(card: SchedulingCard, now: Long, fuzz: Double?): Map<Rating, SchedulingOutcome> {
        val ease = card.easeFactor ?: startingEase
        val lapse = max(card.intervalDays, config.minimumIntervalDays).coerceAtMost(maximum)
        val good = graduate(card, ease, Fuzz.apply(lapse.toDouble(), lapse, maximum, fuzz), now)
        val easy = graduate(card, ease, Fuzz.apply((lapse + 1).toDouble(), lapse + 1, maximum, fuzz), now)
        if (relearningSteps.isEmpty()) {
            return mapOf(Rating.Again to good, Rating.Hard to good, Rating.Good to good, Rating.Easy to easy)
        }
        val step = (card.step ?: 0).coerceIn(0, relearningSteps.lastIndex)
        return mapOf(
            Rating.Again to stepped(card, CardState.Relearning, 0, relearningSteps[0], ease, lapse, now),
            Rating.Hard to stepped(card, CardState.Relearning, step, hardDelay(relearningSteps, step), ease, lapse, now),
            Rating.Good to if (step + 1 < relearningSteps.size) {
                stepped(card, CardState.Relearning, step + 1, relearningSteps[step + 1], ease, lapse, now)
            } else {
                good
            },
            Rating.Easy to easy,
        )
    }

    private fun reviewing(card: SchedulingCard, now: Long, fuzz: Double?): Map<Rating, SchedulingOutcome> {
        val ease = card.easeFactor ?: startingEase
        val factor = ease / 1000.0
        val interval = currentInterval(card)
        val elapsed = card.lastReviewAt?.let { LocalDay.daysBetween(it, now, zone).coerceAtLeast(0).toInt() } ?: interval
        val modifier = config.intervalModifier
        val hard: Int
        val good: Int
        val easy: Int
        if (elapsed < interval) {
            hard = Fuzz.apply(max(elapsed * config.hardIntervalFactor, interval * config.hardIntervalFactor / 2) * modifier, 1, maximum, fuzz)
            good = Fuzz.apply(max(elapsed * factor, interval.toDouble()) * modifier, hard + 1, maximum, fuzz)
            val reducedBonus = config.easyBonus - (config.easyBonus - 1) / 2
            easy = Fuzz.apply(max(elapsed * factor, interval.toDouble()) * reducedBonus * modifier, good + 1, maximum, fuzz)
        } else {
            val daysLate = elapsed - interval
            val hardMinimum = if (config.hardIntervalFactor > 1) interval + 1 else 1
            hard = Fuzz.apply(interval * config.hardIntervalFactor * modifier, hardMinimum, maximum, fuzz)
            good = Fuzz.apply((interval + daysLate / 2.0) * factor * modifier, hard + 1, maximum, fuzz)
            easy = Fuzz.apply((interval + daysLate) * factor * config.easyBonus * modifier, good + 1, maximum, fuzz)
        }
        val lapseEase = max(MINIMUM_EASE, ease - 200)
        val lapse = max(config.minimumIntervalDays, (interval * config.newIntervalFactor).roundToInt()).coerceAtMost(maximum)
        val again = if (relearningSteps.isEmpty()) {
            graduate(card, lapseEase, Fuzz.apply(lapse.toDouble(), 1, maximum, fuzz), now)
        } else {
            stepped(card, CardState.Relearning, 0, relearningSteps[0], lapseEase, lapse, now)
        }
        return mapOf(
            Rating.Again to again,
            Rating.Hard to graduate(card, max(MINIMUM_EASE, ease - 150), hard, now),
            Rating.Good to graduate(card, ease, good, now),
            Rating.Easy to graduate(card, ease + 150, easy, now),
        )
    }

    private fun currentInterval(card: SchedulingCard): Int {
        if (card.intervalDays > 0) return card.intervalDays
        val due = card.dueAt
        val last = card.lastReviewAt
        if (due != null && last != null) return max(1L, LocalDay.daysBetween(last, due, zone)).toInt()
        return 1
    }

    private fun hardDelay(steps: List<Long>, step: Int): Long = when {
        step == 0 && steps.size == 1 -> min(steps[0] * 3 / 2, steps[0] + DAY)
        step == 0 -> (steps[0] + steps[1]) / 2
        else -> steps[step]
    }

    private fun stepped(
        card: SchedulingCard,
        state: CardState,
        step: Int,
        delay: Long,
        ease: Int,
        interval: Int,
        now: Long,
    ): SchedulingOutcome {
        val due = if (delay >= DAY) {
            LocalDay.startAfterDays(now, (delay.toDouble() / DAY).roundToLong(), zone)
        } else {
            now + delay
        }
        return SchedulingOutcome(state, step, card.stability, card.difficulty, ease, interval, due)
    }

    private fun graduate(card: SchedulingCard, ease: Int, days: Int, now: Long): SchedulingOutcome =
        SchedulingOutcome(
            state = CardState.Review,
            step = null,
            stability = card.stability,
            difficulty = card.difficulty,
            easeFactor = ease,
            intervalDays = days,
            dueAt = LocalDay.startAfterDays(now, days.toLong(), zone),
        )

    companion object {
        const val MINUTE: Long = 60_000L
        const val DAY: Long = 86_400_000L
        const val MINIMUM_EASE: Int = 1300
    }
}
