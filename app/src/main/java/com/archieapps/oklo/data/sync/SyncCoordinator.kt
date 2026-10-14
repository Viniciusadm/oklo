package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.session.SessionStore
import com.archieapps.oklo.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val running: Boolean = false,
    val restoring: Boolean = false,
    val lastReport: SyncReport? = null,
)

class SyncCoordinator(
    private val engine: SyncEngine,
    private val sessionStore: SessionStore,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    fun requestSync() {
        scope.launch { syncIfSignedIn() }
    }

    suspend fun syncIfSignedIn(): SyncReport? {
        if (!sessionStore.load().signedIn) return null
        return run()
    }

    suspend fun syncNow(): SyncReport {
        if (!sessionStore.load().signedIn) {
            return SyncReport.Failed(ApiResult.Failure.NotSignedIn, SyncReport.Stage.Push)
        }
        return run()
    }

    suspend fun restore(): SyncReport {
        if (!sessionStore.load().signedIn) {
            return SyncReport.Failed(ApiResult.Failure.NotSignedIn, SyncReport.Stage.Snapshot)
        }
        _state.update { it.copy(restoring = true) }
        val report = engine.restore()
        _state.update { it.copy(restoring = false, lastReport = report) }
        if (report is SyncReport.Done) preferences.recordSyncSuccess(clock())
        return report
    }

    private suspend fun run(): SyncReport {
        _state.update { it.copy(running = true) }
        val report = engine.sync()
        when (report) {
            is SyncReport.Done -> preferences.recordSyncSuccess(clock())
            is SyncReport.Failed -> preferences.recordSyncError(report.failure.userMessage)
        }
        _state.update { it.copy(running = false, lastReport = report) }
        return report
    }
}
