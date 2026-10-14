package com.archieapps.oklo.core.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class FsrsState { Learning, Review, Relearning }

enum class Rating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4);

    companion object {
        fun of(value: Int): Rating = entries.first { it.value == value }
    }
}

data class FsrsCard(
    val state: FsrsState = FsrsState.Learning,
    val step: Int? = 0,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val due: Long,
    val lastReview: Long? = null,
)

data class FsrsParameters(
    val weights: List<Double> = DEFAULT_WEIGHTS,
    val desiredRetention: Double = 0.9,
    val learningStepsMinutes: List<Int> = listOf(1, 10),
    val relearningStepsMinutes: List<Int> = listOf(10),
    val maximumIntervalDays: Int = 36500,
    val enableFuzz: Boolean = true,
) {
    companion object {
        val DEFAULT_WEIGHTS: List<Double> = listOf(
            0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
            1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621,
        )
    }
}

class FsrsScheduler(
    val parameters: FsrsParameters = FsrsParameters(),
    private val random: () -> Double = { kotlin.random.Random.nextDouble() },
) {
    private val w = parameters.weights
    private val learningSteps = parameters.learningStepsMinutes.map { it * MINUTE }
    private val relearningSteps = parameters.relearningStepsMinutes.map { it * MINUTE }

    fun review(card: FsrsCard, rating: Rating, now: Long): FsrsCard =
        review(card, rating, now, parameters.enableFuzz)

    fun preview(card: FsrsCard, now: Long): Map<Rating, FsrsCard> =
        Rating.entries.associateWith { review(card, it, now, fuzz = false) }

    private fun review(card: FsrsCard, rating: Rating, now: Long, fuzz: Boolean): FsrsCard {
        val daysSinceLastReview = card.lastReview?.let { Math.floorDiv(now - it, DAY) }
        var state = card.state
        var step = card.step
        val stability: Double
        val difficulty: Double
        val interval: Long

        val currentStability = card.stability
        val currentDifficulty = card.difficulty

        if (state == FsrsState.Learning && currentStability == null && currentDifficulty == null) {
            stability = initialStability(rating)
            difficulty = initialDifficulty(rating)
        } else {
            val s = currentStability ?: initialStability(rating)
            val d = currentDifficulty ?: initialDifficulty(rating)
            if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                stability = shortTermStability(s, rating)
            } else {
                stability = nextStability(d, s, retrievability(s, card.lastReview, now), rating)
            }
            difficulty = nextDifficulty(d, rating)
        }

        when (state) {
            FsrsState.Learning, FsrsState.Relearning -> {
                val steps = if (state == FsrsState.Learning) learningSteps else relearningSteps
                val currentStep = step ?: 0
                val outOfSteps = currentStep > steps.size ||
                    (currentStep == steps.size && (rating == Rating.Hard || rating == Rating.Good))
                if (steps.isEmpty() || outOfSteps) {
                    state = FsrsState.Review
                    step = null
                    interval = nextIntervalDays(stability) * DAY
                } else {
                    when (rating) {
                        Rating.Again -> {
                            step = 0
                            interval = steps[0]
                        }

                        Rating.Hard -> {
                            step = currentStep
                            interval = when {
                                currentStep == 0 && steps.size == 1 -> (steps[0] * 3) / 2
                                currentStep == 0 && steps.size >= 2 -> (steps[0] + steps[1]) / 2
                                else -> steps[currentStep]
                            }
                        }

                        Rating.Good -> {
                            if (currentStep + 1 == steps.size) {
                                state = FsrsState.Review
                                step = null
                                interval = nextIntervalDays(stability) * DAY
                            } else {
                                step = currentStep + 1
                                interval = steps[currentStep + 1]
                            }
                        }

                        Rating.Easy -> {
                            state = FsrsState.Review
                            step = null
                            interval = nextIntervalDays(stability) * DAY
                        }
                    }
                }
            }

            FsrsState.Review -> {
                if (rating == Rating.Again && relearningSteps.isNotEmpty()) {
                    state = FsrsState.Relearning
                    step = 0
                    interval = relearningSteps[0]
                } else {
                    interval = nextIntervalDays(stability) * DAY
                }
            }
        }

        val finalInterval = if (fuzz && state == FsrsState.Review) fuzzedInterval(interval) else interval

        return FsrsCard(
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            due = now + finalInterval,
            lastReview = now,
        )
    }

    fun nextIntervalDays(stability: Double): Long {
        val raw = (stability / FACTOR) * (parameters.desiredRetention.pow(1.0 / DECAY) - 1)
        val rounded = Math.rint(raw).toLong()
        return min(max(rounded, 1L), parameters.maximumIntervalDays.toLong())
    }

    private fun retrievability(stability: Double, lastReview: Long?, now: Long): Double {
        if (lastReview == null) return 0.0
        val elapsed = max(0L, Math.floorDiv(now - lastReview, DAY))
        return (1 + FACTOR * elapsed / stability).pow(DECAY)
    }

    private fun initialStability(rating: Rating): Double = max(w[rating.value - 1], 0.1)

    private fun initialDifficulty(rating: Rating): Double {
        val value = w[4] - exp(w[5] * (rating.value - 1)) + 1
        return min(max(value, 1.0), 10.0)
    }

    private fun shortTermStability(stability: Double, rating: Rating): Double =
        stability * exp(w[17] * (rating.value - 3 + w[18]))

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val delta = -(w[6] * (rating.value - 3))
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Rating.Easy) + (1 - w[7]) * damped
        return min(max(reverted, 1.0), 10.0)
    }

    private fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: Rating): Double =
        if (rating == Rating.Again) {
            val longTerm = w[11] *
                difficulty.pow(-w[12]) *
                ((stability + 1).pow(w[13]) - 1) *
                exp((1 - retrievability) * w[14])
            val shortTerm = stability / exp(w[17] * w[18])
            min(longTerm, shortTerm)
        } else {
            val hardPenalty = if (rating == Rating.Hard) w[15] else 1.0
            val easyBonus = if (rating == Rating.Easy) w[16] else 1.0
            stability * (
                1 + exp(w[8]) *
                    (11 - difficulty) *
                    stability.pow(-w[9]) *
                    (exp((1 - retrievability) * w[10]) - 1) *
                    hardPenalty *
                    easyBonus
                )
        }

    private fun fuzzedInterval(interval: Long): Long {
        val intervalDays = Math.floorDiv(interval, DAY)
        if (intervalDays < 2.5) return interval
        var delta = 1.0
        FUZZ_RANGES.forEach { (start, end, factor) ->
            delta += factor * max(min(intervalDays.toDouble(), end) - start, 0.0)
        }
        var minIvl = Math.rint(intervalDays - delta).toLong()
        var maxIvl = Math.rint(intervalDays + delta).toLong()
        minIvl = max(2L, minIvl)
        maxIvl = min(maxIvl, parameters.maximumIntervalDays.toLong())
        minIvl = min(minIvl, maxIvl)
        val fuzzed = random() * (maxIvl - minIvl + 1) + minIvl
        val days = min(Math.rint(fuzzed).toLong(), parameters.maximumIntervalDays.toLong())
        return days * DAY
    }

    companion object {
        const val MINUTE: Long = 60_000L
        const val DAY: Long = 86_400_000L
        const val DECAY: Double = -0.5
        val FACTOR: Double = 0.9.pow(1.0 / DECAY) - 1

        private val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )
    }
}
