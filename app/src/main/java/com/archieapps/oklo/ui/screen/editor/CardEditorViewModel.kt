package com.archieapps.oklo.ui.screen.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) {
    val dirty: Boolean get() = content != original
}

class CardEditorViewModel(
    private val container: Container,
    initialDeckId: String?,
    private val cardId: String?,
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
                        it.copy(loaded = true, editing = true, deckId = card.deckId, content = card.content, original = card.content)
                    }
                }
            }
        }
    }

    fun change(content: CardContent) = _state.update { it.copy(content = content, error = null) }

    fun chooseDeck(id: String?) = _state.update { it.copy(deckId = id, error = null) }

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
                    container.cards.update(cardId, current.content)
                } else {
                    container.cards.create(deckId, current.content)
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
                        it.copy(saving = false, content = blank, original = blank, savedCount = it.savedCount + 1)
                    } else {
                        it.copy(saving = false, original = it.content, finished = true, savedCount = it.savedCount + 1)
                    }
                }
            }.onFailure { problem ->
                _state.update { it.copy(saving = false, error = problem.message ?: "Não foi possível salvar.") }
            }
        }
    }
}
