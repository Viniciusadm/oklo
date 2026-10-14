package com.archieapps.oklo.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class DayCodeTest {
    private fun clockAt(instant: String) = Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"))

    @Test
    fun `server day is computed in sao paulo, not in the device zone`() {
        val justBeforeMidnight = clockAt("2026-08-24T02:59:00Z")
        val justAfterMidnight = clockAt("2026-08-24T03:01:00Z")

        assertEquals("2026-08-23", DayCode.serverToday(justBeforeMidnight).toString())
        assertEquals("2026-08-24", DayCode.serverToday(justAfterMidnight).toString())
    }

    @Test
    fun `a code validated yesterday is refused after the sao paulo rollover`() {
        val before = clockAt("2026-08-24T02:59:00Z")
        val after = clockAt("2026-08-24T03:01:00Z")

        assertTrue(DayCode.isValidFor("2026-08-23", before))
        assertFalse(DayCode.isValidFor("2026-08-23", after))
    }

    @Test
    fun `a device in tokyo does not roll the day early`() {
        val tokyoAlreadyTomorrow = Clock.fixed(Instant.parse("2026-08-23T16:00:00Z"), ZoneId.of("Asia/Tokyo"))

        assertEquals("2026-08-23", DayCode.serverToday(tokyoAlreadyTomorrow).toString())
        assertTrue(DayCode.isValidFor("2026-08-23", tokyoAlreadyTomorrow))
    }

    @Test
    fun `blank or null validity is never valid`() {
        assertFalse(DayCode.isValidFor(null, clockAt("2026-08-23T12:00:00Z")))
        assertFalse(DayCode.isValidFor("", clockAt("2026-08-23T12:00:00Z")))
    }

    @Test
    fun `only exactly eight characters are well formed`() {
        assertTrue(DayCode.isWellFormed("40601060"))
        assertFalse(DayCode.isWellFormed("4060106"))
        assertFalse(DayCode.isWellFormed("406010600"))
        assertFalse(DayCode.isWellFormed("4060 060"))
        assertFalse(DayCode.isWellFormed(""))
    }
}
