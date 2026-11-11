package com.archieapps.oklo.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.R
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.ui.common.DeckDialog
import com.archieapps.oklo.ui.common.DeckDialogs
import com.archieapps.oklo.ui.components.ActionSheet
import com.archieapps.oklo.ui.components.CountColumnWidth
import com.archieapps.oklo.ui.components.DueCounts
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.IconAction
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SheetAction
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun HomeScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { HomeViewModel(it) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val message by viewModel.operations.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DeckDialog?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<DeckNode?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.operations.consumeMessage()
        }
    }

    val state = ui ?: return Loading()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Oklo") {
                IconAction(R.drawable.ic_settings, "Configurações", { navigator.to(Routes.SETTINGS) })
            }
            if (state.rows.isEmpty()) {
                EmptyState(
                    title = "Nenhum deck ainda",
                    message = "Crie um deck para começar a estudar.",
                ) {
                    PrimaryButton("Criar deck", onClick = { dialog = DeckDialog.Create(null, null) }, icon = R.drawable.ic_plus)
                    SecondaryButton("Gerar com IA", onClick = { navigator.to(Routes.aiCreate()) }, icon = R.drawable.ic_sparkle)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    item { CountsHeader() }
                    items(state.rows, key = { it.node.id }) { row ->
                        HomeDeckLine(
                            row = row,
                            onOpen = { navigator.to(Routes.deck(row.node.id)) },
                            onToggle = { viewModel.toggle(row.node.id) },
                            onMenu = { menuFor = row.node },
                        )
                    }
                    item {
                        TodayFooter(
                            total = state.summary.total,
                            learningLater = state.learningLaterCount,
                            onStudy = { navigator.to(Routes.study()) },
                        )
                    }
                }
            }
        }
        if (state.rows.isNotEmpty()) {
            FloatingActionButton(
                onClick = { addMenu = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg),
            ) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = "Adicionar")
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(horizontal = Spacing.sm, vertical = Spacing.md))
    }

    if (addMenu) {
        ActionSheet(
            title = "Adicionar",
            onDismiss = { addMenu = false },
            actions = listOf(
                SheetAction("Card", { navigator.to(Routes.newCard(null)) }, icon = R.drawable.ic_edit),
                SheetAction("Cards com IA", { navigator.to(Routes.aiCreate()) }, icon = R.drawable.ic_sparkle),
                SheetAction("Deck", { dialog = DeckDialog.Create(null, null) }, icon = R.drawable.ic_folder),
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
                SheetAction("Adicionar card", { navigator.to(Routes.newCard(node.id)) }, icon = R.drawable.ic_plus),
                SheetAction("Gerar cards com IA", { navigator.to(Routes.aiCreate(node.id)) }, icon = R.drawable.ic_sparkle),
                SheetAction("Criar subdeck", { dialog = DeckDialog.Create(node.id, node.deck.name) }, icon = R.drawable.ic_folder),
                SheetAction("Estatísticas", { navigator.to(Routes.stats(node.id)) }, icon = R.drawable.ic_bar_chart),
            ),
        )
    }

    DeckDialogs(dialog, state.roots, viewModel.operations, onDismiss = { dialog = null })
}

@Composable
private fun CountsHeader() {
    val palette = okloPalette
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screenH, end = Spacing.screenH, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Deck", style = MaterialTheme.typography.labelMedium, color = palette.muted, modifier = Modifier.weight(1f))
            listOf("Novos", "Aprend.", "Revisar").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(CountColumnWidth),
                )
            }
        }
        Hairline()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeDeckLine(
    row: HomeDeckRow,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onMenu: () -> Unit,
) {
    val node = row.node
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onMenu)
                .heightIn(min = 48.dp)
                .padding(start = Spacing.sm + (node.depth * 16).dp, end = Spacing.screenH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (node.hasChildren) {
                    Icon(
                        painterResource(if (row.expanded) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right),
                        contentDescription = if (row.expanded) "Recolher" else "Expandir",
                        tint = okloPalette.muted,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable(role = Role.Button, onClick = onToggle)
                            .padding(4.dp),
                    )
                }
            }
            Text(
                node.deck.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.xs))
            DueCounts(row.newCount, row.learning, row.review)
        }
        Hairline(Modifier.padding(start = Spacing.screenH))
    }
}

@Composable
private fun TodayFooter(total: Int, learningLater: Int, onStudy: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (total > 0) {
            PrimaryButton("Estudar tudo", onClick = onStudy, icon = R.drawable.ic_play, modifier = Modifier.fillMaxWidth())
            Text(
                "${plural(total, "card", "cards")} para hoje",
                style = MaterialTheme.typography.bodySmall,
                color = okloPalette.muted,
            )
        } else {
            Text("Tudo em dia por hoje.", style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted)
            if (learningLater > 0) {
                Text(
                    "${plural(learningLater, "card volta", "cards voltam")} mais tarde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = okloPalette.muted,
                )
            }
        }
    }
}
