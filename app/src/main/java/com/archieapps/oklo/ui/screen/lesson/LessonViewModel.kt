package com.archieapps.oklo.ui.screen.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.data.model.Lesson
import com.archieapps.oklo.data.prefs.AppPreferences
import com.archieapps.oklo.data.repo.LessonRepository
import com.archieapps.oklo.data.repo.LessonValidator
import com.archieapps.ribalta.lesson.LessonTarget
import com.archieapps.ribalta.lesson.parse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LessonMode {
    Gate,
    Read,
    Target,
    ;

    companion object {
        fun of(raw: String?): LessonMode = when (raw) {
            "gate" -> Gate
            "target" -> Target
            else -> Read
        }
    }
}

sealed interface LessonUi {
    data object Loading : LessonUi

    data object Missing : LessonUi

    data class Ready(
        val lesson: Lesson,
        val mode: LessonMode,
        val invalid: Boolean,
        val target: String?,
        val reachedEnd: Boolean,
    ) : LessonUi {
        val canComplete: Boolean get() = mode == LessonMode.Gate && (invalid || reachedEnd)
    }
}

class LessonViewModel(
    private val lessons: LessonRepository,
    private val preferences: AppPreferences,
    private val deckId: String,
    private val mode: LessonMode,
    rawTarget: String?,
    private val validator: LessonValidator = LessonValidator.Ribalta,
) : ViewModel() {
    private val loaded = MutableStateFlow<LessonUi>(LessonUi.Loading)
    private val reachedEnd = MutableStateFlow(false)
    private val target = rawTarget?.takeIf { it.isNotEmpty() && LessonTarget.parse(it) != null }

    val ui: StateFlow<LessonUi> = combine(loaded, reachedEnd) { state, end ->
        if (state is LessonUi.Ready) state.copy(reachedEnd = end) else state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LessonUi.Loading)

    init {
        viewModelScope.launch {
            val lesson = lessons.get(deckId)
            loaded.value = if (lesson == null) {
                LessonUi.Missing
            } else {
                LessonUi.Ready(lesson, mode, !validator.isValid(lesson.source), target, reachedEnd = false)
            }
        }
    }

    fun onReachedEnd(value: Boolean) {
        reachedEnd.value = value
    }

    suspend fun complete(): Boolean {
        val state = ui.value as? LessonUi.Ready ?: return false
        if (!state.canComplete) return false
        preferences.markLessonSeen(deckId, state.lesson.version)
        return true
    }
}
