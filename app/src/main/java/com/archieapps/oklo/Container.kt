package com.archieapps.oklo

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import com.archieapps.oklo.core.net.Connectivity
import com.archieapps.oklo.core.net.NetworkFactory
import com.archieapps.oklo.core.session.CodeGate
import com.archieapps.oklo.core.session.CodeVault
import com.archieapps.oklo.core.session.SessionStore
import com.archieapps.oklo.data.db.OkloDatabase
import com.archieapps.oklo.data.prefs.AppPreferences
import com.archieapps.oklo.data.repo.AiDraftStore
import com.archieapps.oklo.data.repo.AiRepository
import com.archieapps.oklo.data.repo.AuthRepository
import com.archieapps.oklo.data.repo.CardRepository
import com.archieapps.oklo.data.repo.DeckRepository
import com.archieapps.oklo.data.repo.LessonRepository
import com.archieapps.oklo.data.repo.SettingsRepository
import com.archieapps.oklo.data.repo.StudyRepository
import com.archieapps.oklo.data.sync.RetrofitSyncRemote
import com.archieapps.oklo.data.sync.RoomSyncStore
import com.archieapps.oklo.data.sync.SyncCoordinator
import com.archieapps.oklo.data.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class Container(application: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = OkloDatabase.build(application)
    val preferences = AppPreferences(application)
    val sessionStore = SessionStore(application)
    val codeVault = CodeVault()
    val codeGate = CodeGate(codeVault) { sessionStore.setBlocked(it) }
    val connectivity = Connectivity(application)
    val network = NetworkFactory(BuildConfig.BASE_URL, sessionStore, codeVault)

    val decks = DeckRepository(database)
    val cards = CardRepository(database)
    val settings = SettingsRepository(database)
    val lessons = LessonRepository(database.lessons(), preferences)
    val study = StudyRepository(database, settings)
    val auth = AuthRepository(network.api, network.errors, sessionStore, codeVault)
    val ai = AiRepository(network.api, network.gatedApi, network.errors, codeGate, network.json)
    val aiDraft = AiDraftStore()

    val syncEngine = SyncEngine(
        store = RoomSyncStore(database, preferences),
        remote = RetrofitSyncRemote(network.api, network.gatedApi, network.errors, codeGate),
    )
    val sync = SyncCoordinator(syncEngine, sessionStore, preferences, appScope)

    init {
        appScope.launch {
            sessionStore.session.collect { codeVault.seedServerBlocked(it.blocked) }
        }
    }
}

val LocalContainer = staticCompositionLocalOf<Container> {
    error("Container não fornecido")
}
