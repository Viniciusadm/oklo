package com.archieapps.oklo.ui.screen.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.LocalContainer
import com.archieapps.oklo.R
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.data.repo.DraftItem
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.ui.common.answerText
import com.archieapps.oklo.ui.common.deckOptions
import com.archieapps.oklo.ui.components.ActionSheet
import com.archieapps.oklo.ui.components.CardContentEditor
import com.archieapps.oklo.ui.components.CenteredEmptyState
import com.archieapps.oklo.ui.components.CheckMark
import com.archieapps.oklo.ui.components.CodePromptController
import com.archieapps.oklo.ui.components.CodePromptHost
import com.archieapps.oklo.ui.components.ConfirmDialog
import com.archieapps.oklo.ui.components.DeckPickerDialog
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.Eyebrow
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.IconAction
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.QuietButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SectionCard
import com.archieapps.oklo.ui.components.SheetAction
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun AiReviewScreen(navigator: AppNavigator) {
    val viewModel = appViewModel(key = "ai-review") { AiReviewViewModel(it) }
    val batch by viewModel.batch.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val lessonOption by viewModel.lessonOption.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val codePrompt = remember { CodePromptController(container.codeGate) }
    SideEffect { viewModel.openCodePrompt = { retry -> codePrompt.open(retry) } }
    val codePromptOpen by codePrompt.state.collectAsStateWithLifecycle()
    CodePromptHost(codePrompt)
    var menuFor by remember { mutableStateOf<DraftItem?>(null) }
    var editing by remember { mutableStateOf<DraftItem?>(null) }
    var pickDeck by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    summary?.let { done ->
        CenteredEmptyState(
            title = "${plural(done.added, "card adicionado", "cards adicionados")}",
            message = buildString {
                append("Os cards foram salvos em “${done.deckPath}” como novos e já podem ser estudados.")
                if (done.skipped > 0) append(" ${plural(done.skipped, "card não selecionado foi descartado", "cards não selecionados foram descartados")}.")
                if (done.lessonStarted) append(" A aula está sendo gerada; acompanhe no deck.")
                done.lessonMessage?.takeIf { codePromptOpen == null }?.let { append(" ").append(it) }
                append(if (done.synced) " O backup está sendo sincronizado." else " Eles ficam só neste aparelho até você entrar na conta.")
            },
            icon = R.drawable.ic_check,
            modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
        ) {
            PrimaryButton("Abrir o deck", onClick = { navigator.replace(Routes.deck(done.deckId)) })
            SecondaryButton("Gerar mais cards", onClick = navigator::back, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Voltar ao início", onClick = navigator::home, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    val current = batch
    if (current == null) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "Revisar cards", onBack = navigator::back)
            EmptyState(
                title = "Nenhum card para revisar",
                message = "Gere cards a partir de um texto para revisá-los aqui.",
                icon = R.drawable.ic_sparkle,
            ) {
                SecondaryButton("Voltar", onClick = navigator::back, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    BackHandler { navigator.back() }
    val selected = current.items.count { it.selected }
    val path = DeckTree.find(roots, current.deckId)?.pathLabel ?: "Deck não encontrado"

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Revisar cards", subtitle = path, onBack = navigator::back) {
            QuietButton("Descartar", onClick = { confirmDiscard = true }, destructive = true)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screenH, end = Spacing.screenH, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.weight(1f),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionCard(Modifier.clickable(role = Role.Button) { pickDeck = true }) {
                        Eyebrow("Deck de destino")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(path, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(
                                painterResource(R.drawable.ic_chevron_down),
                                contentDescription = "Trocar deck de destino",
                                tint = okloPalette.muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (current.discarded > 0 || current.warnings.isNotEmpty()) {
                        Notice(
                            title = "Sucesso parcial",
                            message = buildString {
                                if (current.discarded > 0) append("A IA gerou ${plural(current.discarded, "card que foi descartado", "cards que foram descartados")} por não passar na validação.")
                                current.warnings.forEach { append("\n• ").append(it) }
                            }.trim(),
                            tone = NoticeTone.Warning,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${plural(current.items.size, "card", "cards")} · $selected ${if (selected == 1) "selecionado" else "selecionados"} para adicionar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = okloPalette.muted,
                            modifier = Modifier.weight(1f),
                        )
                        val all = selected == current.items.size && current.items.isNotEmpty()
                        QuietButton(if (all) "Desmarcar todos" else "Selecionar todos", onClick = { viewModel.selectAll(!all) }, accent = true)
                    }
                }
            }
            if (current.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Todos os cards foram removidos",
                        message = "Volte e gere de novo, ou descarte esta geração.",
                        icon = R.drawable.ic_layers,
                    )
                }
            }
            items(current.items, key = { it.key }) { item ->
                DraftCard(
                    item = item,
                    index = current.items.indexOf(item),
                    onToggle = { viewModel.toggle(item.key) },
                    onEdit = { editing = item },
                    onMenu = { menuFor = item },
                )
            }
        }
        Hairline()
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenH)
                .padding(top = Spacing.md, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            error?.let { Notice(it, tone = NoticeTone.Error) }
            GenerateLessonOption(lessonOption, onChange = viewModel::setGenerateLesson)
            PrimaryButton(
                if (selected == 0) "Selecione cards para adicionar" else "Adicionar ${plural(selected, "card", "cards")} ao deck",
                onClick = viewModel::addSelected,
                enabled = selected > 0,
                loading = saving,
            )
        }
    }

    menuFor?.let { item ->
        ActionSheet(
            title = item.content.type.label,
            subtitle = item.content.front.take(80),
            onDismiss = { menuFor = null },
            actions = listOf(
                SheetAction("Editar", { editing = item }, icon = R.drawable.ic_edit),
                SheetAction("Duplicar", { viewModel.duplicate(item.key) }, icon = R.drawable.ic_copy),
                SheetAction("Remover", { viewModel.remove(item.key) }, icon = R.drawable.ic_trash, destructive = true),
            ),
        )
    }

    editing?.let { item ->
        DraftEditorSheet(
            initial = item.content,
            onSave = { content -> viewModel.replace(item.key, content).also { if (it == null) editing = null } },
            onDismiss = { editing = null },
        )
    }

    if (pickDeck) {
        DeckPickerDialog(
            title = "Deck de destino",
            options = deckOptions(roots),
            selectedId = current.deckId,
            onPick = viewModel::changeDeck,
            onDismiss = { pickDeck = false },
        )
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Descartar os cards gerados?",
            message = "Nenhum card desta geração será adicionado. O texto de origem continua na tela anterior.",
            confirmLabel = "Descartar",
            destructive = true,
            onConfirm = {
                viewModel.discard()
                navigator.back()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun DraftCard(
    item: DraftItem,
    index: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMenu: () -> Unit,
) {
    val content = item.content
    val problem = content.validationError()
    SectionCard(padding = PaddingValues(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.md), spacing = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("${index + 1}. ${content.type.label}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
            IconAction(R.drawable.ic_more, contentDescription = "Ações do card ${index + 1}", onClick = onMenu, tint = okloPalette.muted)
        }
        Column(Modifier.padding(end = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(content.front, style = MaterialTheme.typography.titleMedium)
            when (content.type) {
                CardType.MultipleChoice -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    content.choices.orEmpty().forEachIndexed { i, choice ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(width = 22.dp, height = 20.dp), contentAlignment = Alignment.CenterStart) {
                                if (choice.correct) {
                                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = okloPalette.success, modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                "${'A' + i}) ${choice.text}" + if (choice.correct) " (correta)" else "",
                                style = if (choice.correct) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                color = if (choice.correct) okloPalette.success else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                CardType.TrueFalse -> Text("Resposta esperada: ${answerText(content)}.", style = MaterialTheme.typography.bodyMedium)
                else -> Text(content.back.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted)
            }
            content.explanation?.let {
                Text("Explicação: $it", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
            }
            if (problem != null) {
                Text("Precisa de ajuste: $problem", style = MaterialTheme.typography.bodySmall, color = okloPalette.danger)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            QuietButton("Editar card", onClick = onEdit, icon = R.drawable.ic_edit)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .heightIn(min = 44.dp)
                    .toggleable(value = item.selected, role = Role.Checkbox, onValueChange = { onToggle() })
                    .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckMark(checked = item.selected)
                Spacer(Modifier.width(Spacing.sm))
                Text(if (item.selected) "Selecionado" else "Não selecionado", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftEditorSheet(
    initial: CardContent,
    onSave: (CardContent) -> String?,
    onDismiss: () -> Unit,
) {
    var content by remember { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    val requestClose = { if (content != initial) confirmClose = true else onDismiss() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { target ->
        if (target == SheetValue.Hidden && content != initial) {
            confirmClose = true
            false
        } else {
            true
        }
    })
    ModalBottomSheet(
        onDismissRequest = requestClose,
        sheetState = sheet,
        containerColor = okloPalette.sheet,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        shape = Radii.sheet,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = Spacing.md, bottom = Spacing.sm)
                    .size(width = 36.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, Radii.pill),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
        ) {
            Text("Editar card", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.screenH).padding(top = Spacing.xs, bottom = Spacing.md))
            Hairline(Modifier.padding(horizontal = Spacing.screenH))
            Column(
                Modifier.padding(horizontal = Spacing.screenH).padding(top = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                CardContentEditor(content = content, onChange = {
                    content = it
                    problem = null
                })
                problem?.let { Notice(it, tone = NoticeTone.Error) }
                PrimaryButton("Salvar", onClick = { problem = onSave(content) }, icon = R.drawable.ic_check)
                SecondaryButton("Cancelar", onClick = requestClose, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirmClose) {
        ConfirmDialog(
            title = "Descartar alterações?",
            message = "As mudanças neste card ainda não foram salvas.",
            confirmLabel = "Descartar",
            dismissLabel = "Continuar editando",
            destructive = true,
            onConfirm = onDismiss,
            onDismiss = { confirmClose = false },
        )
    }
}
