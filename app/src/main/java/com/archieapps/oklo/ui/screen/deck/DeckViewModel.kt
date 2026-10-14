package com.archieapps.oklo.ui.screen.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.Lesson
import com.archieapps.oklo.data.repo.LessonJobState
import com.archieapps.oklo.data.repo.LessonJobTracker
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.ui.common.DeckOperations
import com.archieapps.oklo.ui.common.minuteTicker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DeckTab { Subdecks, Cards }

sealed interface LessonProgress {
    data object Idle : LessonProgress

    data object Generating : LessonProgress

    data class Failed(val message: String) : LessonProgress
}

const val LESSON_FAILED = "A geração da aula falhou. Tente de novo."

const val LESSON_DELAYED = "A aula está demorando mais que o normal. Tente de novo mais tarde."

fun lessonProgress(tracker: LessonJobTracker, deckId: String): Flow<LessonProgress> =
    tracker.jobState(deckId).map { state ->
        when (state) {
            null, is LessonJobState.Done -> LessonProgress.Idle
            LessonJobState.Polling -> LessonProgress.Generating
            is LessonJobState.Failed -> LessonProgress.Failed(state.error.ifBlank { LESSON_FAILED })
            LessonJobState.Delayed -> LessonProgress.Failed(LESSON_DELAYED)
        }
    }

sealed interface DeckUi {
    data object Missing : DeckUi

    data class Ready(
        val node: DeckNode,
        val roots: List<DeckNode>,
        val cards: List<Card>,
        val now: Long,
    ) : DeckUi
}

class DeckViewModel(private val container: Container, val deckId: String) : ViewModel() {
    val tab = MutableStateFlow<DeckTab?>(null)
    val reorder = MutableStateFlow<List<String>?>(null)
    val operations = DeckOperations(container, viewModelScope)
    private var studyJob: Job? = null
    private val studyEntry = StudyEntry(container.lessons)

    init {
        container.lessonAiFinisher.resumePending()
    }

    val lesson: StateFlow<Lesson?> = container.lessons.observeByDeck(deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val lessonProgress: StateFlow<LessonProgress> = lessonProgress(container.lessonAiFinisher, deckId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonProgress.Idle)

    val ui: StateFlow<DeckUi?> = combine(
        container.decks.decks,
        container.cards.stats,
        combine(container.settings.settings, container.study.deckLastStudied) { settings, studied -> settings to studied },
        container.cards.byDeck(deckId),
        minuteTicker(60_000),
    ) { decks, stats, preferences, cards, now ->
        val (settings, studied) = preferences
        val roots = DeckTree.build(decks, stats, LocalDay.window(now), settings.librarySort, studied)
        val node = DeckTree.find(roots, deckId)
        if (node == null) DeckUi.Missing else DeckUi.Ready(node, roots, cards.distinctBy { it.id }, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clearLessonFailure() = container.lessonAiFinisher.clear(deckId)

    fun study(deckId: String, navigate: (String) -> Unit) {
        if (studyJob?.isActive == true) return
        studyJob = viewModelScope.launch { navigate(studyEntry.route(deckId)) }
    }

    fun selectTab(value: DeckTab) {
        tab.value = value
    }

    fun setSuspended(cardId: String, suspended: Boolean) {
        viewModelScope.launch {
            container.cards.setSuspended(cardId, suspended)
            operations.message.value = if (suspended) "Card suspenso. Ele não aparece mais nos estudos." else "Card reativado."
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch {
            container.cards.delete(cardId)
            operations.message.value = "Card excluído."
        }
    }

    fun moveCard(cardId: String, targetDeckId: String) {
        viewModelScope.launch {
            container.cards.moveToDeck(cardId, targetDeckId)
            operations.message.value = "Card movido."
        }
    }

    fun startReorder(cards: List<Card>) {
        reorder.value = cards.map { it.id }
        tab.value = DeckTab.Cards
    }

    fun shift(cardId: String, delta: Int) {
        val current = reorder.value ?: return
        val index = current.indexOf(cardId)
        val target = index + delta
        if (index < 0 || target !in current.indices) return
        reorder.value = current.toMutableList().apply {
            removeAt(index)
            add(target, cardId)
        }
    }

    fun finishReorder() {
        val order = reorder.value ?: return
        reorder.value = null
        viewModelScope.launch {
            container.cards.reorder(order)
            operations.message.value = "Ordem dos cards salva. Os novos serão apresentados nessa ordem."
        }
    }

    fun cancelReorder() {
        reorder.value = null
    }
}
