package com.archieapps.oklo.ui.screen.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.data.repo.AiRepository
import com.archieapps.oklo.data.repo.LessonGenerator
import com.archieapps.oklo.data.repo.LessonJobState
import com.archieapps.oklo.data.repo.LessonJobTracker
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.Job
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
    private val tracker: LessonJobTracker,
    private val model: suspend () -> String,
    private val isOnline: () -> Boolean,
    private val syncNow: suspend () -> SyncReport?,
    var openCodePrompt: (onProvided: () -> Unit) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(LessonAiState())
    val state: StateFlow<LessonAiState> = _state

    private var job: Job? = null

    init {
        viewModelScope.launch {
            tracker.jobState(deckId).collect { mirror(it) }
        }
    }

    fun prepare(hasLesson: Boolean) {
        if (_state.value.busy) return
        if (_state.value.phase in FINISHED) tracker.clear(deckId)
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
                is ApiResult.Success -> tracker.track(deckId, result.data.jobId)
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

    private fun mirror(job: LessonJobState?) {
        when (job) {
            null -> Unit
            LessonJobState.Polling -> _state.update { it.copy(phase = LessonAiPhase.Polling, error = null) }
            is LessonJobState.Done -> _state.update { it.copy(phase = LessonAiPhase.Done, warnings = job.warnings, error = null) }
            is LessonJobState.Failed -> _state.update { it.copy(phase = LessonAiPhase.Failed, error = job.error) }
            LessonJobState.Delayed -> _state.update { it.copy(phase = LessonAiPhase.Delayed) }
        }
    }

    companion object {
        private val FINISHED = setOf(LessonAiPhase.Done, LessonAiPhase.Failed, LessonAiPhase.Delayed)
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
