package com.archieapps.oklo.core.sched

import java.util.Random
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Fuzz {
    private val RANGES = listOf(
        Triple(2.5, 7.0, 0.15),
        Triple(7.0, 20.0, 0.1),
        Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
    )

    fun factor(cardId: String, reps: Int): Double =
        Random(cardId.hashCode().toLong() * 1_000_003L + reps).nextDouble()

    fun bounds(interval: Double, minimum: Int, maximum: Int): IntRange {
        val floorValue = min(minimum, maximum)
        val clamped = interval.coerceIn(floorValue.toDouble(), maximum.toDouble())
        var lower: Int
        var upper: Int
        if (clamped < 2.5) {
            lower = clamped.roundToInt()
            upper = lower
        } else {
            var delta = 1.0
            RANGES.forEach { (start, end, factor) -> delta += factor * max(min(clamped, end) - start, 0.0) }
            lower = max(2, (clamped - delta).roundToInt())
            upper = (clamped + delta).roundToInt()
        }
        lower = lower.coerceIn(floorValue, maximum)
        upper = upper.coerceIn(floorValue, maximum)
        if (upper == lower && upper > 2 && upper < maximum) upper = lower + 1
        return lower..upper
    }

    fun apply(interval: Double, minimum: Int, maximum: Int, factor: Double?): Int {
        if (factor == null) {
            return interval.roundToInt().coerceIn(min(minimum, maximum), maximum)
        }
        val range = bounds(interval, minimum, maximum)
        val span = range.last - range.first + 1
        return min(range.first + floor(factor * span).toInt(), range.last)
    }
}
