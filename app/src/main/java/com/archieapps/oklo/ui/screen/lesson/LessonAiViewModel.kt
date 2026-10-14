package com.archieapps.oklo.ui.screen.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.data.repo.AiRepository
import com.archieapps.oklo.data.repo.LessonGenerator
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LessonAiPhase { Form, ConfirmReplace, Sending, Polling, Done, Failed, Delayed }

data class LessonAiState(
    val phase: LessonAiPhase = LessonAiPhase.Form,
    val text: String = "",
    val hasLesson: Boolean = false,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
) {
    val busy: Boolean get() = phase == LessonAiPhase.Sending || phase == LessonAiPhase.Polling
    val tooLong: Boolean get() = text.length > AiRepository.MAX_TEXT
}

class LessonAiViewModel(
    private val deckId: String,
    private val generator: LessonGenerator,
    private val model: suspend () -> String,
    private val isOnline: () -> Boolean,
    private val syncNow: suspend () -> SyncReport?,
    private val finish: (LessonJobBody) -> Unit,
    var openCodePrompt: (onProvided: () -> Unit) -> Unit = {},
    private val pollIntervalMillis: Long = POLL_INTERVAL_MILLIS,
) : ViewModel() {
    private val _state = MutableStateFlow(LessonAiState())
    val state: StateFlow<LessonAiState> = _state

    private var job: Job? = null

    fun prepare(hasLesson: Boolean) {
        if (_state.value.busy) return
        val keepText = _state.value.phase == LessonAiPhase.Form
        _state.value = LessonAiState(hasLesson = hasLesson, text = if (keepText) _state.value.text else "")
    }

    fun setText(text: String) {
        _state.update { it.copy(text = text, error = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.busy) return
        when {
            current.tooLong -> _state.update {
                it.copy(error = "O texto tem ${current.text.length} caracteres; o limite é ${AiRepository.MAX_TEXT}.")
            }
            current.hasLesson && current.phase != LessonAiPhase.ConfirmReplace ->
                _state.update { it.copy(phase = LessonAiPhase.ConfirmReplace, error = null) }
            else -> send()
        }
    }

    fun confirmReplace() {
        if (_state.value.busy) return
        if (_state.value.tooLong) {
            _state.update { it.copy(phase = LessonAiPhase.Form) }
            submit()
            return
        }
        send()
    }

    fun cancelReplace() {
        _state.update { it.copy(phase = LessonAiPhase.Form) }
    }

    fun retry() {
        if (_state.value.busy) return
        send()
    }

    fun dispose() {
        job?.cancel()
        job = null
    }

    override fun onCleared() {
        dispose()
    }

    private fun send() {
        if (!isOnline()) {
            _state.update {
                it.copy(phase = LessonAiPhase.Form, error = "Sem conexão com a internet. A geração com IA precisa de rede.")
            }
            return
        }
        _state.update { it.copy(phase = LessonAiPhase.Sending, error = null, warnings = emptyList()) }
        job?.cancel()
        job = viewModelScope.launch {
            when (val report = syncNow()) {
                is SyncReport.Done -> Unit
                null -> {
                    _state.update { it.copy(phase = LessonAiPhase.Form, error = ApiResult.Failure.NotSignedIn.userMessage) }
                    return@launch
                }
                is SyncReport.Failed -> {
                    val message = if (report.failure == ApiResult.Failure.Network) SYNC_FAILED else "$SYNC_FAILED_PREFIX ${report.failure.userMessage}"
                    _state.update { it.copy(phase = LessonAiPhase.Form, error = message) }
                    return@launch
                }
            }
            val request = LessonGenerateRequest(
                deckId = deckId,
                text = _state.value.text.trim().ifEmpty { null },
                model = model(),
            )
            when (val result = generator.lessonGenerate(request)) {
                is ApiResult.Success -> poll(result.data.jobId)
                ApiResult.Failure.CodeMissing -> {
                    _state.update { it.copy(phase = LessonAiPhase.Form) }
                    openCodePrompt { send() }
                }
                is ApiResult.Failure.Validation -> _state.update {
                    it.copy(phase = LessonAiPhase.Form, error = DECK_NOT_ON_SERVER)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(phase = LessonAiPhase.Form, error = result.userMessage)
                }
            }
        }
    }

    private suspend fun poll(jobId: String) {
        _state.update { it.copy(phase = LessonAiPhase.Polling) }
        repeat(MAX_POLLS) { attempt ->
            if (attempt > 0) delay(pollIntervalMillis)
            when (val result = generator.lessonJob(jobId)) {
                is ApiResult.Success -> when (result.data.status) {
                    STATUS_DONE -> {
                        finish(result.data)
                        _state.update { it.copy(phase = LessonAiPhase.Done, warnings = result.data.warnings) }
                        return
                    }
                    STATUS_FAILED -> {
                        _state.update {
                            it.copy(phase = LessonAiPhase.Failed, error = result.data.error ?: DEFAULT_FAILURE)
                        }
                        return
                    }
                }
                ApiResult.Failure.Network, is ApiResult.Failure.RateLimited -> Unit
                is ApiResult.Failure -> {
                    _state.update { it.copy(phase = LessonAiPhase.Failed, error = result.userMessage) }
                    return
                }
            }
        }
        _state.update { it.copy(phase = LessonAiPhase.Delayed) }
    }

    companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
        const val MAX_POLLS = 300
        private const val STATUS_DONE = "done"
        private const val STATUS_FAILED = "failed"
        const val SYNC_FAILED = "Não foi possível sincronizar o deck com o servidor antes de gerar a aula. Verifique a conexão e tente de novo."
        const val SYNC_FAILED_PREFIX = "Não foi possível sincronizar o deck antes de gerar a aula:"
        const val DECK_NOT_ON_SERVER = "Este deck ainda não chegou ao servidor. Sincronize o app e tente de novo."
        private const val DEFAULT_FAILURE = "A geração da aula falhou. Tente de novo."
    }
}
