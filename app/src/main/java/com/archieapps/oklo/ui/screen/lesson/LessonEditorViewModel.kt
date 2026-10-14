package com.archieapps.oklo.ui.screen.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.data.repo.LessonRepository
import com.archieapps.ribalta.ir.Diagnostic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LessonEditorState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val source: String = "",
    val original: String = "",
    val diagnostics: List<Diagnostic> = emptyList(),
    val diagnosedSource: String? = null,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val dirty: Boolean get() = source != original
    val tooLarge: Boolean get() = source.toByteArray(Charsets.UTF_8).size > LessonEditorViewModel.MAX_SOURCE_BYTES
    val analyzing: Boolean get() = loaded && diagnosedSource != source
    val hasErrors: Boolean get() = diagnostics.any { it.severity == "error" }
    val canSave: Boolean
        get() = loaded && !saving && !finished && !analyzing && !hasErrors && !tooLarge && (dirty || isNew)
}

class LessonEditorViewModel(
    private val lessons: LessonRepository,
    private val requestSync: () -> Unit,
    private val deckId: String,
    private val diagnostics: LessonDiagnostics = LessonDiagnostics.Ribalta,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
) : ViewModel() {
    private val _state = MutableStateFlow(LessonEditorState())
    val state: StateFlow<LessonEditorState> = _state

    private var analysis: Job? = null

    init {
        viewModelScope.launch {
            val existing = lessons.get(deckId)
            val source = existing?.source ?: TEMPLATE
            _state.update { it.copy(loaded = true, isNew = existing == null, source = source, original = source) }
            analyze(source, immediately = true)
        }
    }

    fun onSourceChange(source: String) {
        if (source == _state.value.source) return
        _state.update { it.copy(source = source, saveFailed = false) }
        analyze(source, immediately = false)
    }

    private fun analyze(source: String, immediately: Boolean) {
        analysis?.cancel()
        analysis = viewModelScope.launch {
            if (!immediately) delay(debounceMillis)
            val found = withContext(dispatcher) { diagnostics.diagnose(source) }
            _state.update { it.copy(diagnostics = found, diagnosedSource = source) }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            runCatching { lessons.saveManual(deckId, current.source) }
                .onSuccess {
                    requestSync()
                    _state.update { it.copy(saving = false, finished = true) }
                }
                .onFailure { _state.update { it.copy(saving = false, saveFailed = true) } }
        }
    }

    companion object {
        const val MAX_SOURCE_BYTES = 200_000
        const val DEBOUNCE_MILLIS = 400L
        const val TEMPLATE = "# Título da aula {#titulo}\n\nEscreva aqui o conteúdo da aula.\n"
    }
}
