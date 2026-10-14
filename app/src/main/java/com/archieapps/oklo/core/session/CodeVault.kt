package com.archieapps.oklo.core.session

import com.archieapps.oklo.core.net.CodeProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate

class CodeVault(private val clock: Clock = Clock.system(DayCode.SERVER_ZONE)) : CodeProvider {
    @Volatile
    private var code: String? = null

    @Volatile
    private var codeDay: LocalDate? = null

    @Volatile
    private var strikeDay: LocalDate? = null

    private val _budget = MutableStateFlow(StrikeBudget())
    val budget: StateFlow<StrikeBudget> = _budget.asStateFlow()

    override fun currentCode(): String? {
        val today = DayCode.serverToday(clock)
        return code?.takeIf { codeDay == today }
    }

    fun hasCodeForToday(): Boolean = currentCode() != null

    fun currentBudget(): StrikeBudget {
        val today = DayCode.serverToday(clock)
        if (strikeDay != null && strikeDay != today) {
            _budget.update { it.copy(localStrikes = 0) }
            strikeDay = null
        }
        return _budget.value
    }

    fun store(input: String) {
        code = input
        codeDay = DayCode.serverToday(clock)
    }

    fun onRejected(): StrikeBudget {
        code = null
        codeDay = null
        strikeDay = DayCode.serverToday(clock)
        _budget.update { it.afterRejection(null) }
        return _budget.value
    }

    fun onAccepted() {
        _budget.update { it.afterSuccess() }
        strikeDay = null
    }

    fun seedServerBlocked(value: Int) {
        _budget.update { it.withServerBlocked(value) }
    }

    fun forget() {
        code = null
        codeDay = null
    }
}
