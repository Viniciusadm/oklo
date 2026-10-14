package com.archieapps.oklo.ui.screen.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.R
import com.archieapps.oklo.data.model.LibrarySort
import com.archieapps.oklo.domain.DeckFilter
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.ui.common.DeckDialog
import com.archieapps.oklo.ui.common.DeckDialogs
import com.archieapps.oklo.ui.components.ActionSheet
import com.archieapps.oklo.ui.components.DeckRow
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.IconAction
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.OkloTextField
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SectionTitle
import com.archieapps.oklo.ui.components.SheetAction
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { LibraryViewModel(it) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val message by viewModel.operations.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<DeckDialog?>(null) }
    var menuFor by remember { mutableStateOf<DeckNode?>(null) }
    var addMenu by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.operations.consumeMessage()
        }
    }

    val state = ui ?: return Loading()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Biblioteca", large = true) {
                IconAction(R.drawable.ic_settings, "Configurações", { navigator.to(Routes.SETTINGS) })
            }
            SearchBar(
                query = state.query,
                filter = state.filter,
                sort = state.sort,
                onQuery = viewModel::setQuery,
                onFilter = viewModel::setFilter,
                onSort = viewModel::setSort,
            )
            SectionTitle(
                text = if (state.searching) "Resultados" else "Minha biblioteca",
                modifier = Modifier.padding(start = Spacing.screenH, end = Spacing.xs, top = Spacing.lg, bottom = Spacing.xs),
                action = { IconAction(R.drawable.ic_plus, "Adicionar deck ou criar cards com IA", { addMenu = true }) },
            )

            when {
                state.roots.isEmpty() -> EmptyState(
                    title = "Sua biblioteca está vazia",
                    message = "Crie o primeiro deck ou gere cards com IA a partir de um texto.",
                    icon = R.drawable.ic_library,
                ) {
                    PrimaryButton("Novo deck", onClick = { dialog = DeckDialog.Create(null, null) }, icon = R.drawable.ic_plus)
                    SecondaryButton("Criar cards com IA", onClick = { navigator.to(Routes.aiCreate()) }, icon = R.drawable.ic_sparkle, modifier = Modifier.fillMaxWidth())
                }

                state.rows.isEmpty() -> EmptyState(
                    title = "Nenhum deck encontrado",
                    message = "Tente outro nome ou outro filtro.",
                ) {
                    SecondaryButton(
                        "Limpar busca e filtro",
                        onClick = {
                            viewModel.setQuery("")
                            viewModel.setFilter(DeckFilter.All)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = Spacing.screenBottom),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.rows, key = { it.id }) { node ->
                        LibraryDeckRow(
                            node = node,
                            showPath = state.searching,
                            expanded = node.id in state.expanded,
                            onOpen = { navigator.to(Routes.deck(node.id)) },
                            onToggle = { viewModel.toggle(node.id) },
                            onMenu = { menuFor = node },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = Spacing.sm, vertical = Spacing.md))
    }

    if (addMenu) {
        ActionSheet(
            title = "Adicionar",
            onDismiss = { addMenu = false },
            actions = listOf(
                SheetAction("Novo deck", { dialog = DeckDialog.Create(null, null) }, icon = R.drawable.ic_plus),
                SheetAction("Criar cards com IA", { navigator.to(Routes.aiCreate()) }, icon = R.drawable.ic_sparkle),
            ),
        )
    }

    menuFor?.let { node ->
        ActionSheet(
            title = node.deck.name,
            subtitle = node.pathLabel.takeIf { node.path.size > 1 },
            onDismiss = { menuFor = null },
            actions = listOf(
                SheetAction("Estudar", { navigator.to(Routes.study(node.id)) }, icon = R.drawable.ic_school),
                SheetAction("Criar cards com IA neste deck", { navigator.to(Routes.aiCreate(node.id)) }, icon = R.drawable.ic_sparkle),
                SheetAction("Adicionar card manualmente", { navigator.to(Routes.newCard(node.id)) }, icon = R.drawable.ic_plus),
                SheetAction("Criar subdeck", { dialog = DeckDialog.Create(node.id, node.deck.name) }, icon = R.drawable.ic_folder),
                SheetAction("Renomear", { dialog = DeckDialog.Rename(node.id, node.deck.name) }, icon = R.drawable.ic_edit),
                SheetAction("Mover", { dialog = DeckDialog.Move(node.id, node.deck.name, node.deck.parentId) }, icon = R.drawable.ic_move),
                SheetAction("Duplicar", { viewModel.operations.duplicate(node.id) }, icon = R.drawable.ic_copy),
                SheetAction(
                    label = "Excluir",
                    onClick = {
                        scope.launch {
                            dialog = DeckDialog.Delete(node.id, node.deck.name, viewModel.operations.impact(node.id))
                        }
                    },
                    icon = R.drawable.ic_trash,
                    destructive = true,
                ),
            ),
        )
    }

    DeckDialogs(dialog, state.roots, viewModel.operations, onDismiss = { dialog = null })
}

@Composable
private fun SearchBar(
    query: String,
    filter: DeckFilter,
    sort: LibrarySort,
    onQuery: (String) -> Unit,
    onFilter: (DeckFilter) -> Unit,
    onSort: (LibrarySort) -> Unit,
) {
    var filterMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.screenH, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OkloTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = "Buscar decks...",
            leadingIcon = R.drawable.ic_search,
            trailing = if (query.isNotEmpty()) {
                { IconAction(R.drawable.ic_close, "Limpar busca", { onQuery("") }) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        IconAction(
            icon = R.drawable.ic_filter,
            contentDescription = "Filtrar: ${filter.label}",
            onClick = { filterMenu = true },
            tint = if (filter != DeckFilter.All) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        IconAction(R.drawable.ic_sort, "Ordenar: ${sort.label}", { sortMenu = true })
    }

    if (filterMenu) {
        ActionSheet(
            title = "Filtrar",
            onDismiss = { filterMenu = false },
            actions = DeckFilter.entries.map { option ->
                SheetAction(option.label, { onFilter(option) }, selected = option == filter)
            },
        )
    }

    if (sortMenu) {
        ActionSheet(
            title = "Ordenar",
            onDismiss = { sortMenu = false },
            actions = LibrarySort.entries.map { option ->
                SheetAction("Por " + option.label.lowercase(), { onSort(option) }, selected = option == sort)
            },
        )
    }
}

@Composable
private fun LibraryDeckRow(
    node: DeckNode,
    showPath: Boolean,
    expanded: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
) {
    val subtitle = if (showPath && node.path.size > 1) "${node.pathLabel}\n${deckSubtitle(node)}" else deckSubtitle(node)
    DeckRow(
        node = node,
        subtitle = subtitle,
        onClick = onOpen,
        depth = if (showPath) 0 else node.depth,
        trailing = {
            if (node.hasChildren && !showPath) {
                IconAction(
                    icon = if (expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right,
                    contentDescription = if (expanded) "Recolher subdecks" else "Expandir subdecks",
                    onClick = onToggle,
                )
            }
            IconAction(R.drawable.ic_more, "Ações do deck ${node.deck.name}", onMenu)
        },
    )
}

fun deckSubtitle(node: DeckNode): String {
    val counts = node.aggregate
    val parts = mutableListOf<String>()
    if (node.hasChildren) parts += plural(node.descendantCount, "subdeck", "subdecks")
    parts += plural(counts.total, "card", "cards")
    if (counts.pending > 0) parts += plural(counts.pending, "pendente", "pendentes")
    if (counts.newCount > 0) parts += plural(counts.newCount, "novo", "novos")
    return parts.joinToString(" · ")
}
