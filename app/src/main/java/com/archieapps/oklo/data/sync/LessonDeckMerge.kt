package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.LessonDto
import com.archieapps.oklo.data.db.LessonEntity
import com.archieapps.oklo.data.db.toEntity

object LessonDeckMerge {
    fun resolve(otherLocal: LessonEntity?, incoming: LessonDto): LessonEntity {
        if (otherLocal == null || !otherLocal.dirty) return incoming.toEntity(dirty = false)
        return incoming.toEntity(dirty = true).copy(
            source = otherLocal.source,
            version = maxOf(otherLocal.version, incoming.version) + 1,
            origin = otherLocal.origin,
            ribaltaVersion = otherLocal.ribaltaVersion,
            deletedAt = null,
            updatedAt = maxOf(otherLocal.updatedAt, incoming.updatedAt) + 1,
        )
    }
}
