package com.archieapps.oklo.core.fsrs

import java.util.Locale
import kotlin.math.roundToLong

object IntervalFormat {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun short(durationMs: Long): String {
        val ms = durationMs.coerceAtLeast(0)
        return when {
            ms < MINUTE -> "<1 min"
            ms < HOUR -> "${(ms.toDouble() / MINUTE).roundToLong()} min"
            ms < DAY -> "${(ms.toDouble() / HOUR).roundToLong()} h"
            ms < 30 * DAY -> "${(ms.toDouble() / DAY).roundToLong()} d"
            ms < 365 * DAY -> decimal(ms.toDouble() / (30 * DAY)).let { "$it ${if (it == "1") "mês" else "meses"}" }
            else -> decimal(ms.toDouble() / (365 * DAY)).let { "$it ${if (it == "1") "ano" else "anos"}" }
        }
    }

    private fun decimal(value: Double): String {
        val rounded = (value * 10).roundToLong() / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.forLanguageTag("pt-BR"), "%.1f", rounded)
        }
    }

    fun duration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours} h ${minutes} min"
            minutes > 0 -> "$minutes min"
            else -> "$seconds s"
        }
    }
}
