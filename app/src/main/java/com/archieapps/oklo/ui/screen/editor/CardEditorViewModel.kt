package com.archieapps.oklo.ui.screen.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val editing: Boolean = false,
    val deckId: String? = null,
    val content: CardContent = CardContent(CardType.Basic, ""),
    val original: CardContent = CardContent(CardType.Basic, ""),
    val error: String? = null,
    val saving: Boolean = false,
    val savedCount: Int = 0,
    val finished: Boolean = false,
    val lessonTarget: String? = null,
    val originalTarget: String? = null,
    val originalDeckId: String? = null,
    val link: LessonLinkUi = LessonLinkUi(),
) {
    val dirty: Boolean get() = content != original || lessonTarget != originalTarget
}

class CardEditorViewModel(
    private val container: Container,
    initialDeckId: String?,
    private val cardId: String?,
    private val targets: LessonTargetSource = LessonTargetSource.Ribalta,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState(deckId = initialDeckId))
    val state: StateFlow<EditorState> = _state

    val roots: StateFlow<List<DeckNode>> = combine(container.decks.decks, container.settings.settings) { decks, settings ->
        DeckTree.build(decks, emptyList(), LocalDay.window(System.currentTimeMillis()), settings.librarySort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (cardId == null) {
                _state.update { it.copy(loaded = true) }
            } else {
                val card = container.cards.get(cardId)
                _state.update {
                    if (card == null) {
                        it.copy(loaded = true, missing = true)
                    } else {
                        it.copy(
                            loaded = true,
                            editing = true,
                            deckId = card.deckId,
                            content = card.content,
                            original = card.content,
                            lessonTarget = card.lessonTarget,
                            originalTarget = card.lessonTarget,
                            originalDeckId = card.deckId,
                        )
                    }
                }
            }
            refreshLink()
        }
    }

    private suspend fun refreshLink() {
        val current = _state.value
        val deckId = current.deckId
        val lessonSource = deckId?.let { container.lessons.get(it)?.source }
        val link = withContext(dispatcher) { LessonLinks.build(lessonSource, current.lessonTarget, targets) }
        _state.update { latest ->
            if (latest.deckId == deckId && latest.lessonTarget == current.lessonTarget) latest.copy(link = link) else latest
        }
    }

    fun chooseLessonTarget(target: String?) {
        _state.update { it.copy(lessonTarget = target, error = null, link = it.link.copy(status = LessonLinks.statusOf(target, it.link.options))) }
    }

    fun change(content: CardContent) = _state.update { it.copy(content = content, error = null) }

    fun chooseDeck(id: String?) {
        if (id == _state.value.deckId) return
        _state.update {
            val target = if (id == it.originalDeckId) it.originalTarget else null
            it.copy(deckId = id, error = null, lessonTarget = target, link = LessonLinkUi())
        }
        viewModelScope.launch { refreshLink() }
    }

    fun save(addAnother: Boolean) {
        val current = _state.value
        if (current.saving) return
        val deckId = current.deckId
        if (deckId == null) {
            _state.update { it.copy(error = "Escolha o deck do card.") }
            return
        }
        current.content.validationError()?.let { problem ->
            _state.update { it.copy(error = problem) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching {
                if (current.editing && cardId != null) {
                    container.cards.update(cardId, current.content, current.lessonTarget)
                } else {
                    container.cards.create(deckId, current.content, current.lessonTarget)
                }
            }.onSuccess {
                _state.update {
                    if (addAnother && !current.editing) {
                        val blank = CardContent(current.content.type, "").let { content ->
                            if (current.content.type == CardType.MultipleChoice) {
                                content.copy(choices = listOf(com.archieapps.oklo.data.model.Choice("", true), com.archieapps.oklo.data.model.Choice("", false)))
                            } else {
                                content
                            }
                        }
                        it.copy(saving = false, content = blank, original = blank, savedCount = it.savedCount + 1, lessonTarget = null, originalTarget = null, link = it.link.copy(status = LinkStatus.None))
                    } else {
                        it.copy(saving = false, original = it.content, originalTarget = it.lessonTarget, finished = true, savedCount = it.savedCount + 1)
                    }
                }
            }.onFailure { problem ->
                _state.update { it.copy(saving = false, error = problem.message ?: "Não foi possível salvar.") }
            }
        }
    }
}
