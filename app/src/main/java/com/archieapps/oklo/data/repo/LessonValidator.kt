package com.archieapps.oklo.data.repo

import com.archieapps.oklo.data.prefs.AppPreferences
import com.archieapps.oklo.data.db.LessonDao
import com.archieapps.oklo.data.db.LessonEntity
import com.archieapps.oklo.data.db.toModel
import com.archieapps.oklo.data.model.Lesson
import com.archieapps.ribalta.lesson.compileLesson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun interface LessonValidator {
    fun isValid(source: String): Boolean

    companion object {
        private const val VALIDATION_WIDTH_PX = 1080

        val Ribalta = LessonValidator { source ->
            runCatching { compileLesson(source, VALIDATION_WIDTH_PX) }
                .map { result -> result.diagnostics.none { it.severity == "error" } }
                .getOrDefault(false)
        }
    }
}

class LessonRepository(
    private val dao: LessonDao,
    private val preferences: AppPreferences,
    private val validator: LessonValidator = LessonValidator.Ribalta,
    private val time: TimeSource = TimeSource.System,
    private val ids: () -> String = ::newId,
) {
    fun observeByDeck(deckId: String): Flow<Lesson?> = dao.observeByDeck(deckId).map { it?.toModel() }

    suspend fun get(deckId: String): Lesson? =
        dao.getByDeck(deckId)?.takeIf { it.deletedAt == null }?.toModel()

    suspend fun saveManual(deckId: String, source: String): Lesson {
        val now = time.now()
        val existing = dao.getByDeck(deckId)
        val entity = LessonEntity(
            id = existing?.id ?: ids(),
            deckId = deckId,
            source = source,
            version = (existing?.version ?: 0) + 1,
            ribaltaVersion = RIBALTA_VERSION,
            origin = ORIGIN_MANUAL,
            createdAt = existing?.createdAt ?: now,
            updatedAt = Stamp.next(existing?.updatedAt, now),
            deletedAt = null,
            dirty = true,
        )
        dao.upsert(entity)
        return entity.toModel()
    }

    suspend fun needsGate(deckId: String): Boolean {
        val lesson = get(deckId) ?: return false
        val seen = preferences.seenLessonVersionNow(deckId)
        if (seen != null && lesson.version <= seen) return false
        return validator.isValid(lesson.source)
    }

    companion object {
        const val RIBALTA_VERSION = "0.3.0"
        const val ORIGIN_MANUAL = "manual"
    }
}
