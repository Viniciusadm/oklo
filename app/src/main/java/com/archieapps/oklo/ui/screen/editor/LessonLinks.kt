package com.archieapps.oklo.ui.screen.editor

import com.archieapps.ribalta.lesson.compileLesson
import com.archieapps.ribalta.lesson.serialize
import com.archieapps.ribalta.lesson.targets
import java.text.Normalizer

data class LinkOption(val target: String, val label: String, val pageTitle: String?)

fun interface LessonTargetSource {
    fun options(lessonSource: String): List<LinkOption>

    companion object {
        private const val WIDTH_PX = 1080

        val Ribalta = LessonTargetSource { source ->
            runCatching {
                compileLesson(source, WIDTH_PX).document.targets().map { LinkOption(it.target.serialize(), it.label, it.pageTitle) }
            }.getOrDefault(emptyList())
        }
    }
}

sealed interface LinkStatus {
    data object None : LinkStatus

    data class Linked(val option: LinkOption) : LinkStatus

    data class Broken(val raw: String) : LinkStatus
}

data class LessonLinkUi(
    val hasLesson: Boolean = false,
    val options: List<LinkOption> = emptyList(),
    val status: LinkStatus = LinkStatus.None,
)

object LessonLinks {
    fun build(lessonSource: String?, current: String?, targets: LessonTargetSource): LessonLinkUi {
        if (lessonSource == null) return LessonLinkUi()
        val options = targets.options(lessonSource)
        return LessonLinkUi(hasLesson = true, options = options, status = statusOf(current, options))
    }

    fun statusOf(current: String?, options: List<LinkOption>): LinkStatus {
        val raw = current?.takeIf { it.isNotBlank() } ?: return LinkStatus.None
        val match = options.firstOrNull { it.target == raw }
        return if (match != null) LinkStatus.Linked(match) else LinkStatus.Broken(raw)
    }

    fun filter(options: List<LinkOption>, query: String): List<LinkOption> {
        val needle = fold(query)
        if (needle.isEmpty()) return options
        return options.filter { fold(it.label).contains(needle) || fold(it.pageTitle.orEmpty()).contains(needle) }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
