package com.archieapps.oklo.ui.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val PtBr: Locale = Locale.forLanguageTag("pt-BR")

fun minuteTicker(periodMs: Long = 30_000): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMs)
    }
}

fun longDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)).uppercase(PtBr)

fun shortDateTime(epochMs: Long): String {
    val zoned = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
    return zoned.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PtBr))
}

fun clock(epochMs: Long): String {
    val zoned = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
    return zoned.format(DateTimeFormatter.ofPattern("HH:mm", PtBr))
}
