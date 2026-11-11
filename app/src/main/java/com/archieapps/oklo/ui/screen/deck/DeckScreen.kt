package com.archieapps.oklo.ui.screen.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.R
import com.archieapps.oklo.core.fsrs.IntervalFormat
import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.ui.common.DeckDialog
import com.archieapps.oklo.ui.common.DeckDialogs
import com.archieapps.oklo.ui.common.answerText
import com.archieapps.oklo.ui.common.backLabel
import com.archieapps.oklo.ui.common.deckOptions
import com.archieapps.oklo.ui.common.frontLabel
import com.archieapps.oklo.ui.common.shortDateTime
import com.archieapps.oklo.ui.common.stateLabel
import com.archieapps.oklo.ui.components.ActionSheet
import com.archieapps.oklo.ui.components.ConfirmDialog
import com.archieapps.oklo.ui.components.DeckPickerDialog
import com.archieapps.oklo.ui.components.DeckRow
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.Eyebrow
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.IconAction
import com.archieapps.oklo.ui.components.InlineProgress
import com.archieapps.oklo.ui.components.ListRow
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.OkloSheet
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.QuietButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SegmentedTabs
import com.archieapps.oklo.ui.components.SheetAction
import com.archieapps.oklo.ui.components.Tag
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.screen.lesson.LessonAiSheet
import com.archieapps.oklo.ui.screen.lesson.lessonTitle
import com.archieapps.oklo.ui.screen.library.deckSubtitle
import com.archieapps.oklo.ui.screen.settings.SettingsSection
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette
import kotlinx.coroutines.launch

@Composable
fun DeckScreen(navigator: AppNavigator, deckId: String) {
    val viewModel = appViewModel(key = "deck-$deckId") { DeckViewModel(it, deckId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val selectedTab by viewModel.tab.collectAsStateWithLifecycle()
    val reorder by viewModel.reorder.collectAsStateWithLifecycle()
    val lesson by viewModel.lesson.collectAsStateWithLifecycle()
    val lessonProgress by viewModel.lessonProgress.collectAsStateWithLifecycle()
    val message by viewModel.operations.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<DeckDialog?>(null) }
    var headerMenu by remember { mutableStateOf(false) }
    var lessonAiOpen by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf<Card?>(null) }
    var preview by remember { mutableStateOf<Card?>(null) }
    var confirmDeleteCard by remember { mutableStateOf<Card?>(null) }
    var moveCard by remember { mutableStateOf<Card?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.operations.consumeMessage()
        }
    }

    val state = when (val current = ui) {
        null -> return Loading()
        DeckUi.Missing -> {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                TopBar(title = "Deck", onBack = navigator::back)
                EmptyState(
                    title = "Este deck não existe mais",
                    message = "Ele pode ter sido excluído aqui ou em outro aparelho.",
                ) {
                    SecondaryButton("Voltar", onClick = navigator::back, modifier = Modifier.fillMaxWidth())
                }
            }
            return
        }
        is DeckUi.Ready -> current
    }
    val node = state.node
    val tab = selectedTab ?: if (node.hasChildren) DeckTab.Subdecks else DeckTab.Cards
    val pathParent = node.path.dropLast(1).joinToString(" / ").takeIf { it.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = node.deck.name,
                subtitle = node.pathLabel.takeIf { node.path.size > 1 },
                onBack = navigator::back,
            ) {
                IconAction(R.drawable.ic_plus, "Adicionar card", { navigator.to(Routes.newCard(node.id)) })
                IconAction(R.drawable.ic_sparkle, "Gerar cards com IA", { navigator.to(Routes.aiCreate(node.id)) })
                IconAction(R.drawable.ic_more, "Mais ações do deck", { headerMenu = true })
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = Spacing.screenBottom),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item(key = "summary") {
                    Summary(
                        node = node,
                        parentPath = pathParent,
                        lessonTitle = lesson?.let { lessonTitle(it.source, node.deck.name) },
                        lessonProgress = lessonProgress,
                        onDismissLessonFailure = viewModel::clearLessonFailure,
                        onLesson = { navigator.to(Routes.lesson(node.id)) },
                        onEditLesson = { navigator.to(Routes.editLesson(node.id)) },
                        onLessonAi = {
                            viewModel.clearLessonFailure()
                            lessonAiOpen = true
                        },
                        onStudy = { viewModel.study(node.id, navigator::to) },
                        modifier = Modifier.padding(horizontal = Spacing.screenH).padding(top = Spacing.sm, bottom = Spacing.lg),
                    )
                }
                if (reorder == null) {
                    item(key = "tabs") {
                        SegmentedTabs(
                            options = listOf("Subdecks (${node.children.size})", "Cards (${state.cards.size})"),
                            selectedIndex = if (tab == DeckTab.Subdecks) 0 else 1,
                            onSelect = { viewModel.selectTab(if (it == 0) DeckTab.Subdecks else DeckTab.Cards) },
                            modifier = Modifier.padding(horizontal = Spacing.screenH),
                        )
                    }
                }
                if (reorder != null) {
                    val order = reorder.orEmpty()
                    val byId = state.cards.associateBy { it.id }
                    item(key = "reorder-header") {
                        Column(
                            Modifier.padding(horizontal = Spacing.screenH).padding(bottom = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(
                                "Reorganizar cards: use as setas. Os cards novos são apresentados nesta ordem.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = okloPalette.muted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                QuietButton("Cancelar", onClick = viewModel::cancelReorder)
                                PrimaryButton("Salvar ordem", onClick = viewModel::finishReorder)
                            }
                        }
                        Hairline()
                    }
                    items(order.mapNotNull { byId[it] }, key = { "r-" + it.id }) { card ->
                        val index = order.indexOf(card.id)
                        ReorderRow(
                            card = card,
                            canUp = index > 0,
                            canDown = index < order.lastIndex,
                            onUp = { viewModel.shift(card.id, -1) },
                            onDown = { viewModel.shift(card.id, 1) },
                        )
                    }
                } else if (tab == DeckTab.Subdecks) {
                    if (node.children.isEmpty()) {
                        item(key = "no-subdecks") {
                            EmptyState(
                                title = "Nenhum subdeck",
                                message = "Organize o conteúdo em níveis, por exemplo: PHP → Laravel.",
                                icon = R.drawable.ic_folder,
                            ) {
                                SecondaryButton("Criar subdeck", onClick = { dialog = DeckDialog.Create(node.id, node.deck.name) }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        items(node.children, key = { "d-" + it.id }) { child ->
                            DeckRow(
                                node = child,
                                subtitle = deckSubtitle(child),
                                onClick = { navigator.to(Routes.deck(child.id)) },
                                chevron = true,
                            )
                        }
                    }
                } else {
                    if (state.cards.isEmpty()) {
                        item(key = "no-cards") {
                            EmptyState(
                                title = "Este deck ainda não tem cards",
                                message = if (node.hasChildren) "Os cards dos subdecks continuam nos subdecks. Adicione cards aqui ou gere com IA." else "Adicione manualmente ou gere a partir de um texto com IA.",
                                icon = R.drawable.ic_layers,
                            ) {
                                PrimaryButton("Adicionar card", onClick = { navigator.to(Routes.newCard(node.id)) }, icon = R.drawable.ic_plus)
                                SecondaryButton("Gerar com IA", onClick = { navigator.to(Routes.aiCreate(node.id)) }, icon = R.drawable.ic_sparkle)
                            }
                        }
                    } else {
                        items(state.cards, key = { "c-" + it.id }) { card ->
                            CardRow(card, state.now, onOpen = { preview = card }, onMenu = { cardMenu = card })
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = Spacing.sm, vertical = Spacing.md))
    }

    if (headerMenu) {
        ActionSheet(
            title = node.deck.name,
            onDismiss = { headerMenu = false },
            actions = listOf(
                SheetAction("Renomear", { dialog = DeckDialog.Rename(node.id, node.deck.name) }, icon = R.drawable.ic_edit),
                SheetAction("Criar subdeck", { dialog = DeckDialog.Create(node.id, node.deck.name) }, icon = R.drawable.ic_folder),
                SheetAction("Reorganizar cards", { viewModel.startReorder(state.cards) }, icon = R.drawable.ic_swap_vert, hint = if (state.cards.size < 2) "Precisa de pelo menos 2 cards" else null),
                SheetAction("Opções de estudo", { navigator.to(Routes.settingsSection(SettingsSection.Study)) }, icon = R.drawable.ic_settings, hint = "Os limites diários valem para todos os decks"),
                SheetAction("Estatísticas do deck", { navigator.to(Routes.stats(node.id)) }, icon = R.drawable.ic_bar_chart),
                SheetAction("Mover", { dialog = DeckDialog.Move(node.id, node.deck.name, node.deck.parentId) }, icon = R.drawable.ic_move),
                SheetAction("Duplicar", { viewModel.operations.duplicate(node.id) }, icon = R.drawable.ic_copy),
                SheetAction(
                    label = "Excluir deck",
                    onClick = { scope.launch { dialog = DeckDialog.Delete(node.id, node.deck.name, viewModel.operations.impact(node.id)) } },
                    icon = R.drawable.ic_trash,
                    destructive = true,
                ),
            ),
        )
    }

    cardMenu?.let { card ->
        ActionSheet(
            title = card.content.front.take(60),
            subtitle = card.content.type.label,
            onDismiss = { cardMenu = null },
            actions = listOf(
                SheetAction("Editar", { navigator.to(Routes.editCard(card.id)) }, icon = R.drawable.ic_edit),
                if (card.suspended) {
                    SheetAction("Reativar", { viewModel.setSuspended(card.id, false) }, icon = R.drawable.ic_play)
                } else {
                    SheetAction("Suspender", { viewModel.setSuspended(card.id, true) }, icon = R.drawable.ic_pause, hint = "Fica fora dos estudos até ser reativado")
                },
                SheetAction("Mover para outro deck", { moveCard = card }, icon = R.drawable.ic_move),
                SheetAction("Excluir", { confirmDeleteCard = card }, icon = R.drawable.ic_trash, destructive = true),
            ),
        )
    }

    preview?.let { card ->
        CardPreviewSheet(
            card = card,
            now = state.now,
            onDismiss = { preview = null },
            onEdit = {
                preview = null
                navigator.to(Routes.editCard(card.id))
            },
            onToggleSuspend = {
                preview = null
                viewModel.setSuspended(card.id, !card.suspended)
            },
            onDelete = {
                preview = null
                confirmDeleteCard = card
            },
        )
    }

    confirmDeleteCard?.let { card ->
        ConfirmDialog(
            title = "Excluir este card?",
            message = "“${card.content.front.take(80)}” será removido do deck. O histórico de estudo é mantido.",
            confirmLabel = "Excluir",
            destructive = true,
            onConfirm = { viewModel.deleteCard(card.id) },
            onDismiss = { confirmDeleteCard = null },
        )
    }

    moveCard?.let { card ->
        DeckPickerDialog(
            title = "Mover card para",
            options = deckOptions(state.roots),
            selectedId = card.deckId,
            onPick = { target -> if (target != null) viewModel.moveCard(card.id, target) },
            onDismiss = { moveCard = null },
        )
    }

    if (lessonAiOpen) {
        LessonAiSheet(
            deckId = node.id,
            hasLesson = lesson != null,
            onDismiss = { lessonAiOpen = false },
            onOpenLesson = { navigator.to(Routes.lesson(node.id, awaitingSync = true)) },
        )
    }

    DeckDialogs(dialog, state.roots, viewModel.operations, onDismiss = { dialog = null }, afterDelete = navigator::back)
}

@Composable
private fun Summary(
    node: DeckNode,
    parentPath: String?,
    lessonTitle: String?,
    lessonProgress: LessonProgress,
    onDismissLessonFailure: () -> Unit,
    onLesson: () -> Unit,
    onEditLesson: () -> Unit,
    onLessonAi: () -> Unit,
    onStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = okloPalette
    val counts = node.aggregate
    var lessonMenu by remember { mutableStateOf(false) }
    val canStudy = counts.pending + counts.newCount > 0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.SpaceEvenly) {
            OverviewCount(counts.newCount, "Novos", palette.newCount)
            OverviewCount(counts.learningDue, "Aprendendo", palette.learnCount)
            OverviewCount(counts.reviewDue, "Revisar", palette.reviewCount)
        }
        PrimaryButton("Estudar", onClick = onStudy, enabled = canStudy, modifier = Modifier.fillMaxWidth())
        val hints = buildList {
            if (!canStudy) add(if (counts.total == 0) "Adicione cards para começar." else "Nada pendente hoje.")
            add(plural(counts.total, "card", "cards") + if (node.hasChildren) " com subdecks" else "")
            if (counts.suspended > 0) add(plural(counts.suspended, "suspenso", "suspensos"))
            if (parentPath != null) add("em $parentPath")
        }
        Text(
            hints.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = if (lessonTitle != null) onLesson else onEditLesson),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text("Aula", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        lessonProgress == LessonProgress.Generating -> "Gerando aula…"
                        lessonTitle != null -> lessonTitle
                        else -> "Nenhuma aula. Toque para escrever."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (lessonProgress == LessonProgress.Generating) {
                InlineProgress("")
            } else {
                IconAction(R.drawable.ic_more, "Opções da aula", { lessonMenu = true })
            }
        }
        (lessonProgress as? LessonProgress.Failed)?.let {
            Notice(it.message, tone = NoticeTone.Error, action = { QuietButton("Dispensar", onClick = onDismissLessonFailure) })
        }
        Hairline()
    }

    if (lessonMenu) {
        ActionSheet(
            title = "Aula",
            onDismiss = { lessonMenu = false },
            actions = buildList {
                if (lessonTitle != null) add(SheetAction("Ver aula", onLesson, icon = R.drawable.ic_visibility))
                add(SheetAction(if (lessonTitle != null) "Editar aula" else "Escrever aula", onEditLesson, icon = R.drawable.ic_edit))
                add(
                    SheetAction(
                        when {
                            lessonProgress is LessonProgress.Failed -> "Tentar gerar de novo"
                            lessonTitle != null -> "Substituir com IA"
                            else -> "Gerar com IA"
                        },
                        onLessonAi,
                        icon = R.drawable.ic_sparkle,
                    ),
                )
            },
        )
    }
}

@Composable
private fun OverviewCount(value: Int, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = if (value > 0) color else okloPalette.faint)
        Text(label, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
    }
}

@Composable
private fun CardRow(card: Card, now: Long, onOpen: () -> Unit, onMenu: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(start = Spacing.screenH, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f).padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(card.content.front, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(card.content.type.label)
                    Tag(stateLabel(card, now), color = if (card.suspended) okloPalette.warning else okloPalette.muted)
                }
            }
            IconAction(R.drawable.ic_more, "Ações do card", onMenu)
        }
        Hairline(Modifier.padding(horizontal = Spacing.screenH))
    }
}

@Composable
private fun ReorderRow(card: Card, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    ListRow(
        title = card.content.front,
        divider = true,
        endPadding = Spacing.xs,
        trailing = {
            IconAction(R.drawable.ic_chevron_up, "Mover para cima", onUp, enabled = canUp)
            IconAction(R.drawable.ic_chevron_down, "Mover para baixo", onDown, enabled = canDown)
        },
    )
}

@Composable
private fun CardPreviewSheet(
    card: Card,
    now: Long,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleSuspend: () -> Unit,
    onDelete: () -> Unit,
) {
    val content = card.content
    OkloSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH)
                .padding(bottom = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(content.type.label)
                Tag(stateLabel(card, now))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow(frontLabel(content.type))
                Text(content.front, style = MaterialTheme.typography.titleLarge)
            }
            if (content.type == CardType.MultipleChoice) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    content.choices.orEmpty().forEachIndexed { index, choice ->
                        ChoiceLine(letter = 'A' + index, text = choice.text, correct = choice.correct)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Eyebrow(backLabel(content.type))
                    Text(answerText(content), style = MaterialTheme.typography.bodyLarge)
                }
            }
            content.explanation?.let {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Eyebrow("Explicação")
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Eyebrow("Agendamento")
                Text(
                    buildString {
                        append("${card.state.label} · ${plural(card.reps, "revisão", "revisões")} · ${plural(card.lapses, "lapso", "lapsos")}")
                        card.dueAt?.let { append("\nPróxima: ${shortDateTime(it)}") }
                        if (card.intervalDays > 0) append("\nIntervalo: ${plural(card.intervalDays, "dia", "dias")}")
                        card.easeFactor?.let { append(" · Facilidade: ${it / 10}%") }
                        card.stability?.let { append("\nEstabilidade: ${IntervalFormat.short((it * 86_400_000).toLong())}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = okloPalette.muted,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                QuietButton("Excluir", onClick = onDelete, destructive = true)
                Spacer(Modifier.weight(1f))
                QuietButton(if (card.suspended) "Reativar" else "Suspender", onClick = onToggleSuspend)
                PrimaryButton("Editar", onClick = onEdit)
            }
        }
    }
}

@Composable
private fun ChoiceLine(letter: Char, text: String, correct: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (correct) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = okloPalette.success, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "$letter) $text" + if (correct) " (correta)" else "",
            style = if (correct) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (correct) okloPalette.success else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
