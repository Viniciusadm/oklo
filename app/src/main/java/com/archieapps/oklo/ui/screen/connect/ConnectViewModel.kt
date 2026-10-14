package com.archieapps.oklo.ui.screen.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

class ConnectViewModel(private val container: Container) : ViewModel() {
    val state = MutableStateFlow(ConnectState())

    fun setEmail(value: String) = state.update { it.copy(email = value, error = null) }

    fun setPassword(value: String) = state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = state.value
        if (current.busy) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            state.update { it.copy(error = "Informe e-mail e senha.") }
            return
        }
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = container.auth.login(current.email, current.password)) {
                is ApiResult.Success -> {
                    state.update { it.copy(busy = false, done = true, password = "") }
                    container.sync.requestSync()
                }
                is ApiResult.Failure -> state.update { it.copy(busy = false, error = result.userMessage) }
            }
        }
    }
}
