package com.archieapps.oklo.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.net.AiStatusBody
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.SyncStatusBody
import com.archieapps.oklo.core.session.Session
import com.archieapps.oklo.data.model.StudySettings
import com.archieapps.oklo.data.prefs.Appearance
import com.archieapps.oklo.data.prefs.SyncMeta
import com.archieapps.oklo.data.prefs.ThemeMode
import com.archieapps.oklo.data.sync.SyncReport
import com.archieapps.oklo.data.sync.SyncUiState
import com.archieapps.oklo.ui.components.CodePromptController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Remote<out T> {
    data object Idle : Remote<Nothing>
    data object Loading : Remote<Nothing>
    data class Ready<T>(val value: T) : Remote<T>
    data class Failed(val message: String) : Remote<Nothing>
}

class SettingsViewModel(private val container: Container) : ViewModel() {
    val appearance: StateFlow<Appearance?> = container.preferences.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<StudySettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val session: StateFlow<Session?> = container.sessionStore.session
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val syncMeta: StateFlow<SyncMeta?> = container.preferences.syncMeta
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val syncState: StateFlow<SyncUiState> = container.sync.state
    val pending: StateFlow<Int?> = combine(
        container.database.decks().observeDirtyCount(),
        container.database.cards().observeDirtyCount(),
        container.database.reviews().observeDirtyCount(),
        container.database.lessons().observeDirtyCount(),
        container.database.settings().observeDirtyCount(),
    ) { decks, cards, reviews, lessons, settings -> decks + cards + reviews + lessons + settings }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val aiStatus = MutableStateFlow<Remote<AiStatusBody>>(Remote.Idle)
    val serverStatus = MutableStateFlow<Remote<SyncStatusBody>>(Remote.Idle)
    val message = MutableStateFlow<String?>(null)
    val codePrompt = CodePromptController(container.codeGate)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.preferences.setTheme(mode) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { container.preferences.setTextScale(scale) }
    }

    fun update(transform: (StudySettings) -> StudySettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun loadAiStatus() {
        viewModelScope.launch {
            if (!container.sessionStore.load().signedIn) {
                aiStatus.value = Remote.Failed("Entre na conta para consultar a IA.")
                return@launch
            }
            aiStatus.value = Remote.Loading
            aiStatus.value = when (val result = container.ai.status()) {
                is ApiResult.Success -> Remote.Ready(result.data)
                is ApiResult.Failure -> Remote.Failed(result.userMessage)
            }
        }
    }

    fun loadServerStatus() {
        viewModelScope.launch {
            if (!container.sessionStore.load().signedIn) return@launch
            serverStatus.value = Remote.Loading
            serverStatus.value = when (val result = container.network.errors.call { container.network.api.status() }) {
                is ApiResult.Success -> Remote.Ready(result.data)
                is ApiResult.Failure -> Remote.Failed(result.userMessage)
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            message.value = when (val report = container.sync.syncNow()) {
                is SyncReport.Done -> buildString {
                    append("Sincronizado: ${report.pushed} enviados, ${report.applied} recebidos.")
                    if (report.rejected > 0) append(" ${report.rejected} recusados pelo servidor.")
                }
                is SyncReport.Failed -> report.failure.userMessage
            }
            loadServerStatus()
        }
    }

    fun restore() {
        viewModelScope.launch {
            when (val report = container.sync.restore()) {
                is SyncReport.Done -> {
                    message.value = "Dados restaurados do servidor: ${report.applied} registros."
                    loadServerStatus()
                }
                is SyncReport.Failed -> {
                    if (report.failure == ApiResult.Failure.CodeMissing) {
                        codePrompt.open { restore() }
                    } else {
                        message.value = "Restauração não concluída: ${report.failure.userMessage} Seus dados locais não foram alterados."
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            container.auth.logout()
            serverStatus.value = Remote.Idle
            aiStatus.value = Remote.Idle
            message.value = "Você saiu da conta. Os dados continuam neste aparelho."
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
