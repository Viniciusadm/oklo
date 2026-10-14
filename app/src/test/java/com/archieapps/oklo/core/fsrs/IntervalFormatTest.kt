package com.archieapps.oklo.core.fsrs

import org.junit.Assert.assertEquals
import org.junit.Test

class IntervalFormatTest {
    private val minute = 60_000L
    private val day = 86_400_000L

    @Test
    fun `formats each range like the rating buttons need`() {
        assertEquals("<1 min", IntervalFormat.short(30_000))
        assertEquals("1 min", IntervalFormat.short(minute))
        assertEquals("6 min", IntervalFormat.short(330_000))
        assertEquals("10 min", IntervalFormat.short(10 * minute))
        assertEquals("1 h", IntervalFormat.short(60 * minute))
        assertEquals("23 h", IntervalFormat.short(23 * 60 * minute))
        assertEquals("1 d", IntervalFormat.short(day))
        assertEquals("29 d", IntervalFormat.short(29 * day))
        assertEquals("1 mês", IntervalFormat.short(30 * day))
        assertEquals("1,5 meses", IntervalFormat.short(45 * day))
        assertEquals("1 ano", IntervalFormat.short(365 * day))
        assertEquals("2,5 anos", IntervalFormat.short((365 * 2.5 * day).toLong()))
        assertEquals("<1 min", IntervalFormat.short(-5))
    }

    @Test
    fun `formats session durations`() {
        assertEquals("45 s", IntervalFormat.duration(45_000))
        assertEquals("8 min", IntervalFormat.duration(8 * minute + 20_000))
        assertEquals("1 h 5 min", IntervalFormat.duration(65 * minute))
    }
}
