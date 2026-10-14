package com.archieapps.oklo.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayWindow(val date: LocalDate, val start: Long, val end: Long) {
    operator fun contains(instant: Long): Boolean = instant in start until end
}

object LocalDay {
    fun window(now: Long, zone: ZoneId = ZoneId.systemDefault()): DayWindow {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return of(date, zone)
    }

    fun of(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayWindow {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return DayWindow(date, start, end)
    }

    fun dateOf(instant: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()
}
