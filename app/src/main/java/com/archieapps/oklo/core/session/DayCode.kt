package com.archieapps.oklo.core.session

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

object DayCode {
    val SERVER_ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

    const val LENGTH = 8

    fun serverToday(clock: Clock = Clock.system(SERVER_ZONE)): LocalDate =
        LocalDate.now(clock.withZone(SERVER_ZONE))

    fun isValidFor(validFor: String?, clock: Clock = Clock.system(SERVER_ZONE)): Boolean {
        if (validFor.isNullOrBlank()) {
            return false
        }

        return validFor == serverToday(clock).toString()
    }

    fun isWellFormed(input: String): Boolean =
        input.length == LENGTH && input.none { it.isWhitespace() }
}
