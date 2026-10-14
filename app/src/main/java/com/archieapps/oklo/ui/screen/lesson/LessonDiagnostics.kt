package com.archieapps.oklo.ui.screen.lesson

import com.archieapps.ribalta.ir.Diagnostic
import com.archieapps.ribalta.lesson.compileLesson

fun interface LessonDiagnostics {
    fun diagnose(source: String): List<Diagnostic>

    companion object {
        private const val DIAGNOSTIC_WIDTH_PX = 1080

        val Ribalta = LessonDiagnostics { source ->
            runCatching { compileLesson(source, DIAGNOSTIC_WIDTH_PX).diagnostics }
                .getOrElse { listOf(Diagnostic("error", "compile-failed", 1, "Não foi possível analisar o texto.")) }
        }
    }
}
