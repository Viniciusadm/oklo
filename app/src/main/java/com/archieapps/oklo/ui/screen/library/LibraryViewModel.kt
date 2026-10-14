package com.archieapps.oklo.ui.screen.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.LibrarySort
import com.archieapps.oklo.domain.DeckFilter
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.ui.common.DeckOperations
import com.archieapps.oklo.ui.common.minuteTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUi(
    val roots: List<DeckNode>,
    val rows: List<DeckNode>,
    val searching: Boolean,
    val query: String,
    val filter: DeckFilter,
    val sort: LibrarySort,
    val expanded: Set<String>,
)

private data class LibraryControls(
    val query: String = "",
    val filter: DeckFilter = DeckFilter.All,
    val expanded: Set<String> = emptySet(),
)

class LibraryViewModel(private val container: Container) : ViewModel() {
    private val controls = MutableStateFlow(LibraryControls())

    val ui: StateFlow<LibraryUi?> = combine(
        container.decks.decks,
        container.cards.stats,
        combine(container.settings.settings, container.study.deckLastStudied) { settings, studied -> settings to studied },
        controls,
        minuteTicker(60_000),
    ) { decks, stats, preferences, control, now ->
        val (settings, studied) = preferences
        val roots = DeckTree.build(decks, stats, LocalDay.window(now), settings.librarySort, studied)
        val searching = control.query.isNotBlank() || control.filter != DeckFilter.All
        val rows = if (searching) {
            DeckTree.search(roots, control.query, control.filter, now)
        } else {
            DeckTree.visible(roots, control.expanded)
        }
        LibraryUi(roots, rows.distinctBy { it.id }, searching, control.query, control.filter, settings.librarySort, control.expanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) = controls.update { it.copy(query = value) }

    fun setFilter(value: DeckFilter) = controls.update { it.copy(filter = value) }

    fun toggle(id: String) = controls.update {
        it.copy(expanded = if (id in it.expanded) it.expanded - id else it.expanded + id)
    }

    fun expand(id: String) = controls.update { it.copy(expanded = it.expanded + id) }

    fun setSort(sort: LibrarySort) {
        viewModelScope.launch { container.settings.update { it.copy(librarySort = sort) } }
    }

    val operations = DeckOperations(
        container = container,
        scope = viewModelScope,
        onCreated = { deck -> deck.parentId?.let { expand(it) } },
        onMoved = { parentId -> parentId?.let { expand(it) } },
    )

}
