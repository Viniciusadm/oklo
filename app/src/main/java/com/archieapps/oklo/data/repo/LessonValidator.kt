package com.archieapps.oklo.data.repo

import com.archieapps.ribalta.lesson.compileLesson

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
