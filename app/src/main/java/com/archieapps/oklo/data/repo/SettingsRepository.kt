package com.archieapps.oklo.data.repo

import com.archieapps.oklo.data.db.OkloDatabase
import com.archieapps.oklo.data.db.toEntity
import com.archieapps.oklo.data.db.toModel
import com.archieapps.oklo.data.model.StudySettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(
    private val db: OkloDatabase,
    private val time: TimeSource = TimeSource.System,
) {
    val settings: Flow<StudySettings> = db.settings().observe().map { it?.toModel() ?: StudySettings() }

    suspend fun current(): StudySettings = db.settings().get()?.toModel() ?: StudySettings()

    suspend fun update(transform: (StudySettings) -> StudySettings) {
        val current = current()
        val next = transform(current)
        if (next.copy(updatedAt = current.updatedAt) == current) return
        val stamped = next.copy(updatedAt = Stamp.next(current.updatedAt.takeIf { it > 0 }, time.now()))
        db.settings().upsert(stamped.toEntity(dirty = true))
    }
}
