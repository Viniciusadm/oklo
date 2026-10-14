package com.archieapps.oklo.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

data class PendingLessonJob(val deckId: String, val jobId: String)

interface PendingLessonJobs {
    suspend fun pendingLessonJobs(): List<PendingLessonJob>

    suspend fun addPendingLessonJob(job: PendingLessonJob)

    suspend fun removePendingLessonJob(jobId: String)
}

class AppPreferences(private val store: DataStore<Preferences>) : PendingLessonJobs {
    constructor(context: Context) : this(context.appDataStore)

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

    fun seenLessonVersion(deckId: String): Flow<Int?> = store.data.map { it[seenLessonKey(deckId)] }

    suspend fun seenLessonVersionNow(deckId: String): Int? = seenLessonVersion(deckId).first()

    suspend fun markLessonSeen(deckId: String, version: Int) {
        store.edit { it[seenLessonKey(deckId)] = version }
    }

    override suspend fun pendingLessonJobs(): List<PendingLessonJob> =
        store.data.first()[PENDING_LESSON_JOBS].orEmpty().mapNotNull { decodePending(it) }.sortedBy { it.deckId }

    override suspend fun addPendingLessonJob(job: PendingLessonJob) {
        store.edit { prefs ->
            val others = prefs[PENDING_LESSON_JOBS].orEmpty().filter { decodePending(it)?.deckId != job.deckId }
            prefs[PENDING_LESSON_JOBS] = others.toSet() + "${job.deckId}$PENDING_SEPARATOR${job.jobId}"
        }
    }

    override suspend fun removePendingLessonJob(jobId: String) {
        store.edit { prefs ->
            prefs[PENDING_LESSON_JOBS] = prefs[PENDING_LESSON_JOBS].orEmpty().filter { decodePending(it)?.jobId != jobId }.toSet()
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

        private val PENDING_LESSON_JOBS = stringSetPreferencesKey("pending_lesson_jobs")
        private const val PENDING_SEPARATOR = "|"

        private fun decodePending(raw: String): PendingLessonJob? {
            val parts = raw.split(PENDING_SEPARATOR)
            return if (parts.size == 2) PendingLessonJob(parts[0], parts[1]) else null
        }

        private fun seenLessonKey(deckId: String) = intPreferencesKey("seen_lesson_$deckId")
    }
}
