package com.archieapps.oklo.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DayWindow(val date: LocalDate, val start: Long, val end: Long) {
    operator fun contains(instant: Long): Boolean = instant in start until end
}

object LocalDay {
    const val DAY_START_HOUR: Long = 4

    fun window(now: Long, zone: ZoneId = ZoneId.systemDefault()): DayWindow = of(dateOf(now, zone), zone)

    fun of(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayWindow =
        DayWindow(date, startOf(date, zone), startOf(date.plusDays(1), zone))

    fun dateOf(instant: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(instant).atZone(zone).minusHours(DAY_START_HOUR).toLocalDate()

    fun daysBetween(from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        ChronoUnit.DAYS.between(dateOf(from, zone), dateOf(to, zone))

    fun startAfterDays(now: Long, days: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOf(dateOf(now, zone).plusDays(days), zone)

    private fun startOf(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusHours(DAY_START_HOUR).toInstant().toEpochMilli()
}
