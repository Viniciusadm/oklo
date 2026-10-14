package com.archieapps.oklo.ui.screen.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.repo.DraftBatch
import com.archieapps.oklo.data.repo.DraftItem
import com.archieapps.oklo.data.repo.newId
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddSummary(
    val added: Int,
    val skipped: Int,
    val deckId: String,
    val deckPath: String,
    val synced: Boolean,
)

class AiReviewViewModel(private val container: Container) : ViewModel() {
    val batch: StateFlow<DraftBatch?> = container.aiDraft.batch
    val summary = MutableStateFlow<AddSummary?>(null)
    val error = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)

    val roots: StateFlow<List<DeckNode>> = combine(container.decks.decks, container.settings.settings) { decks, settings ->
        DeckTree.build(decks, emptyList(), LocalDay.window(System.currentTimeMillis()), settings.librarySort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun edit(transform: (DraftBatch) -> DraftBatch) {
        container.aiDraft.batch.update { current -> current?.let(transform) }
        error.value = null
    }

    fun toggle(key: String) = edit { batch ->
        batch.copy(items = batch.items.map { if (it.key == key) it.copy(selected = !it.selected) else it })
    }

    fun selectAll(selected: Boolean) = edit { batch -> batch.copy(items = batch.items.map { it.copy(selected = selected) }) }

    fun remove(key: String) = edit { batch -> batch.copy(items = batch.items.filterNot { it.key == key }) }

    fun duplicate(key: String) = edit { batch ->
        val index = batch.items.indexOfFirst { it.key == key }
        if (index < 0) return@edit batch
        val copy = batch.items[index].copy(key = newId())
        batch.copy(items = batch.items.toMutableList().apply { add(index + 1, copy) })
    }

    fun replace(key: String, content: CardContent): String? {
        content.validationError()?.let { return it }
        edit { batch -> batch.copy(items = batch.items.map { if (it.key == key) it.copy(content = content.normalized()) else it }) }
        return null
    }

    fun changeDeck(deckId: String?) {
        if (deckId == null) return
        edit { it.copy(deckId = deckId) }
    }

    fun addSelected() {
        val current = batch.value ?: return
        if (saving.value) return
        val chosen: List<DraftItem> = current.items.filter { it.selected }
        if (chosen.isEmpty()) {
            error.value = "Selecione pelo menos um card para adicionar."
            return
        }
        val invalid = chosen.indexOfFirst { it.content.validationError() != null }
        if (invalid >= 0) {
            error.value = "O card ${current.items.indexOf(chosen[invalid]) + 1} precisa de ajuste: ${chosen[invalid].content.validationError()}"
            return
        }
        saving.value = true
        viewModelScope.launch {
            runCatching {
                container.decks.get(current.deckId) ?: error("O deck de destino não existe mais. Escolha outro.")
                container.cards.createMany(current.deckId, chosen.map { it.content })
            }.onSuccess { created ->
                val path = DeckTree.find(roots.value, current.deckId)?.pathLabel ?: ""
                container.aiDraft.batch.value = null
                container.aiDraft.form.update { it.copy(text = "") }
                val signedIn = container.sessionStore.load().signedIn
                summary.value = AddSummary(created.size, current.items.size - chosen.size, current.deckId, path, signedIn)
                if (signedIn) container.sync.requestSync()
            }.onFailure { problem ->
                error.value = problem.message ?: "Não foi possível adicionar os cards."
            }
            saving.value = false
        }
    }

    fun discard() {
        container.aiDraft.batch.value = null
    }
}
