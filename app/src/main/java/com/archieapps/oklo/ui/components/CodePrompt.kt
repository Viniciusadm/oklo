package com.archieapps.oklo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.core.session.CodeGate
import com.archieapps.oklo.core.session.CodePad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CodePromptUi(
    val keypad: List<String>,
    val chosen: List<String?>,
    val error: String?,
)

class CodePromptController(private val gate: CodeGate) {
    private val _state = MutableStateFlow<CodePromptUi?>(null)
    val state: StateFlow<CodePromptUi?> = _state.asStateFlow()
    private var pending: (() -> Unit)? = null

    fun open(onProvided: () -> Unit) {
        pending = onProvided
        _state.value = CodePromptUi(CodePad.shuffleKeypad(), List(CodePad.SLOTS) { null }, null)
    }

    fun pick(option: String) {
        _state.update { current ->
            current ?: return@update null
            val index = current.chosen.indexOfFirst { it == null }
            if (index < 0) return@update current
            current.copy(chosen = current.chosen.toMutableList().also { it[index] = option }, error = null)
        }
    }

    fun erase() {
        _state.update { current ->
            current ?: return@update null
            val index = current.chosen.indexOfLast { it != null }
            if (index < 0) return@update current
            current.copy(chosen = current.chosen.toMutableList().also { it[index] = null })
        }
    }

    fun submit() {
        val current = _state.value ?: return
        val code = CodePad.assemble(current.chosen) ?: return
        val problem = gate.provide(code)
        if (problem != null) {
            _state.value = current.copy(error = problem)
            return
        }
        _state.value = null
        val action = pending
        pending = null
        action?.invoke()
    }

    fun dismiss() {
        _state.value = null
        pending = null
    }

    fun hardStopped(): Boolean = !gate.canTypeCode()

    fun remaining(): Int = gate.budget.value.remainingShown
}

@Composable
fun CodePromptHost(controller: CodePromptController) {
    val ui by controller.state.collectAsStateWithLifecycle()
    val current = ui ?: return
    CodeDialog(
        keypad = current.keypad,
        chosen = current.chosen,
        error = current.error,
        hardStopped = controller.hardStopped(),
        remaining = controller.remaining(),
        onPick = controller::pick,
        onErase = controller::erase,
        onSubmit = controller::submit,
        onDismiss = controller::dismiss,
    )
}
