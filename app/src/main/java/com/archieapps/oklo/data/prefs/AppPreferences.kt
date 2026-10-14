package com.archieapps.oklo.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore("oklo-preferences")

enum class ThemeMode(val wire: String, val label: String) {
    System("system", "Automático"),
    Light("light", "Claro"),
    Dark("dark", "Escuro");

    companion object {
        fun of(wire: String?): ThemeMode = entries.firstOrNull { it.wire == wire } ?: System
    }
}

data class Appearance(
    val theme: ThemeMode = ThemeMode.System,
    val textScale: Float = 1f,
)

data class SyncMeta(
    val lastUsn: Long = 0,
    val lastSyncAt: Long? = null,
    val rejectedCount: Int = 0,
    val lastRejectedReason: String? = null,
    val lastError: String? = null,
)

class AppPreferences(context: Context) {
    private val store = context.appDataStore

    val appearance: Flow<Appearance> = store.data.map {
        Appearance(
            theme = ThemeMode.of(it[THEME]),
            textScale = it[TEXT_SCALE] ?: 1f,
        )
    }

    val syncMeta: Flow<SyncMeta> = store.data.map {
        SyncMeta(
            lastUsn = it[LAST_USN] ?: 0,
            lastSyncAt = it[LAST_SYNC_AT],
            rejectedCount = it[REJECTED_COUNT] ?: 0,
            lastRejectedReason = it[LAST_REJECTED_REASON],
            lastError = it[LAST_ERROR],
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.wire }
    }

    suspend fun setTextScale(scale: Float) {
        store.edit { it[TEXT_SCALE] = scale }
    }

    suspend fun deviceId(): String {
        val existing = store.data.first()[DEVICE_ID]
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        store.edit { prefs ->
            if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = created
        }
        return store.data.first()[DEVICE_ID] ?: created
    }

    suspend fun lastUsn(): Long = store.data.first()[LAST_USN] ?: 0

    suspend fun setLastUsn(usn: Long) {
        store.edit { it[LAST_USN] = usn }
    }

    suspend fun recordSyncSuccess(at: Long) {
        store.edit {
            it[LAST_SYNC_AT] = at
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordSyncError(message: String) {
        store.edit { it[LAST_ERROR] = message }
    }

    suspend fun addRejected(count: Int, reason: String?) {
        if (count <= 0) return
        store.edit {
            it[REJECTED_COUNT] = (it[REJECTED_COUNT] ?: 0) + count
            if (reason != null) it[LAST_REJECTED_REASON] = reason
        }
    }

    suspend fun resetSyncState() {
        store.edit {
            it.remove(LAST_USN)
            it.remove(LAST_SYNC_AT)
            it.remove(REJECTED_COUNT)
            it.remove(LAST_REJECTED_REASON)
            it.remove(LAST_ERROR)
        }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val TEXT_SCALE = floatPreferencesKey("text_scale")
        private val DEVICE_ID = stringPreferencesKey("device_id")
        private val LAST_USN = longPreferencesKey("last_usn")
        private val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        private val REJECTED_COUNT = intPreferencesKey("rejected_count")
        private val LAST_REJECTED_REASON = stringPreferencesKey("last_rejected_reason")
        private val LAST_ERROR = stringPreferencesKey("last_sync_error")
    }
}
