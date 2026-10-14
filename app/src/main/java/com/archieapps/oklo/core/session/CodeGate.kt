package com.archieapps.oklo.core.session

import com.archieapps.oklo.core.net.ApiResult
import kotlinx.coroutines.flow.StateFlow

class CodeGate(
    private val vault: CodeVault,
    private val persistBlocked: suspend (Int) -> Unit,
) {
    val budget: StateFlow<StrikeBudget> = vault.budget

    fun hasCode(): Boolean = vault.hasCodeForToday()

    fun canTypeCode(): Boolean = vault.currentBudget().canAttempt

    fun provide(code: String): String? {
        val trimmed = code.trim()
        if (!DayCode.isWellFormed(trimmed)) return "O código tem 8 dígitos."
        if (vault.currentBudget().hardStopped) return ApiResult.Failure.CodeHardStopped.userMessage
        vault.store(trimmed)
        return null
    }

    fun forget() = vault.forget()

    suspend fun <T> run(block: suspend () -> ApiResult<T>): ApiResult<T> {
        if (!vault.hasCodeForToday()) {
            return if (vault.currentBudget().hardStopped) ApiResult.Failure.CodeHardStopped else ApiResult.Failure.CodeMissing
        }
        val result = block()
        when (result) {
            ApiResult.Failure.CodeRejected -> {
                val budget = vault.onRejected()
                persistBlocked(budget.serverBlocked)
            }

            is ApiResult.Success,
            is ApiResult.Failure.AiNotConfigured,
            is ApiResult.Failure.AiUnreachable,
            is ApiResult.Failure.AiInvalidResponse,
            is ApiResult.Failure.TextTooLong,
            -> {
                vault.onAccepted()
                persistBlocked(0)
            }

            else -> Unit
        }
        return result
    }
}
