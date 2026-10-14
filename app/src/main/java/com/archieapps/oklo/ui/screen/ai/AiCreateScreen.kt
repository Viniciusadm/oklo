package com.archieapps.oklo.ui.screen.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.R
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.data.repo.AiRepository
import com.archieapps.oklo.data.repo.DraftDifficulty
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.ui.common.DeckDialog
import com.archieapps.oklo.ui.common.DeckDialogs
import com.archieapps.oklo.ui.common.deckOptions
import com.archieapps.oklo.ui.components.ChoicePill
import com.archieapps.oklo.ui.components.ChoiceRow
import com.archieapps.oklo.ui.components.CodePromptHost
import com.archieapps.oklo.ui.components.DeckPickerDialog
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.Eyebrow
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.InlineProgress
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.OkloTextField
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.QuietButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SectionCard
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiCreateScreen(navigator: AppNavigator, deckId: String?) {
    val viewModel = appViewModel(key = "ai-create") { AiCreateViewModel(it, deckId) }
    val form by viewModel.form.collectAsStateWithLifecycle()
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val pending by viewModel.pendingBatch.collectAsStateWithLifecycle()
    val model by viewModel.model.collectAsStateWithLifecycle()
    val lessonOption by viewModel.lessonOption.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var pickDeck by remember { mutableStateOf(false) }
    var deckDialog by remember { mutableStateOf<DeckDialog?>(null) }
    var showOptions by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigateToReview.collect { navigator.to(Routes.AI_REVIEW) }
    }

    val tree = roots ?: return Loading()
    val ready = readiness ?: return Loading()
    val manualTarget = { navigator.to(Routes.newCard(form.deckId)) }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = "Criar com IA", subtitle = model.takeIf { it.isNotEmpty() }?.let { "Modelo: $it" }, onBack = navigator::back)

        if (!ready.signedIn || !ready.online) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.screenH)
                    .padding(bottom = Spacing.screenBottom),
            ) {
                EmptyState(
                    title = if (!ready.signedIn) "Entre para usar a IA" else "Sem conexão",
                    message = if (!ready.signedIn) {
                        "A geração com IA acontece no servidor e precisa de login. O resto do app funciona sem conta: você pode criar cards manualmente agora."
                    } else {
                        "A geração com IA precisa de internet. Seus decks e estudos continuam funcionando offline."
                    },
                    icon = if (!ready.signedIn) R.drawable.ic_sparkle else R.drawable.ic_cloud_off,
                ) {
                    if (!ready.signedIn) {
                        PrimaryButton("Entrar", onClick = { navigator.to(Routes.CONNECT) })
                    } else {
                        PrimaryButton("Tentar de novo", onClick = viewModel::refreshReadiness)
                    }
                    SecondaryButton("Adicionar manualmente", onClick = manualTarget, icon = R.drawable.ic_plus, modifier = Modifier.fillMaxWidth())
                }
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH)
                .padding(top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (ready.statusChecked && ready.status?.configured == false) {
                Notice(
                    "A IA não está configurada no servidor. Peça para configurar a chave ou adicione cards manualmente.",
                    tone = NoticeTone.Warning,
                    title = "IA indisponível",
                ) {
                    QuietButton("Adicionar manualmente", onClick = manualTarget)
                }
            }
            pending?.let { batch ->
                Notice(
                    "Há ${plural(batch.items.size, "card gerado", "cards gerados")} aguardando revisão.",
                    tone = NoticeTone.Info,
                    title = "Revisão pendente",
                ) {
                    QuietButton("Revisar agora", onClick = { navigator.to(Routes.AI_REVIEW) })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Eyebrow("Deck de destino")
                SectionCard(Modifier.clickable(role = Role.Button) { pickDeck = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            form.deckId?.let { DeckTree.find(tree, it)?.pathLabel } ?: "Escolher deck",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painterResource(R.drawable.ic_chevron_down),
                            contentDescription = "Trocar deck de destino",
                            tint = okloPalette.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (tree.isEmpty()) {
                    SecondaryButton("Criar um deck", onClick = { deckDialog = DeckDialog.Create(null, null) }, icon = R.drawable.ic_plus)
                }
            }

            val length = form.text.length
            val tooLong = length > AiRepository.MAX_TEXT
            val counter = "$length de ${AiRepository.MAX_TEXT} caracteres"
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Texto de origem", Modifier.weight(1f))
                    QuietButton(
                        "Colar texto",
                        onClick = {
                            scope.launch {
                                val pasted = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                                if (!pasted.isNullOrEmpty()) viewModel.setText(if (form.text.isBlank()) pasted else form.text + "\n" + pasted)
                            }
                        },
                        enabled = !generating,
                        icon = R.drawable.ic_paste,
                    )
                }
                OkloTextField(
                    value = form.text,
                    onValueChange = viewModel::setText,
                    placeholder = "Cole um artigo, trecho de livro, documentação ou suas anotações...",
                    minLines = 8,
                    supporting = counter,
                    error = counter.takeIf { tooLong || error?.kind == AiErrorKind.Validation },
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (form.text.isBlank() && error == null) {
                    Text("O campo está vazio: cole ou digite o conteúdo que vira cards.", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
                }
            }
            if (tooLong && error?.kind != AiErrorKind.TooLong) {
                Notice("O texto passa do limite de ${AiRepository.MAX_TEXT} caracteres. Divida em partes menores: o app não corta o texto para não gerar cards incompletos.", tone = NoticeTone.Warning)
            }

            SectionCard(Modifier.clickable(role = Role.Button) { showOptions = !showOptions }, spacing = Spacing.lg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Configuração de geração", style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOf(
                                "Quantidade: " + (form.maxCards?.let { "até $it" } ?: "automática"),
                                "Dificuldade: " + form.difficulty.label.lowercase(),
                                "Tipos: " + if (form.types.isEmpty()) "IA decide" else form.types.joinToString { it.label.lowercase() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = okloPalette.muted,
                        )
                    }
                    Icon(
                        painterResource(if (showOptions) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down),
                        contentDescription = if (showOptions) "Esconder opções" else "Mostrar opções",
                        tint = okloPalette.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (showOptions) {
                    Hairline()
                    OptionGroup("Quantidade", hint = "Deixe a IA decidir ou defina um limite.") {
                        val limits = listOf(5, 10, 20, AiRepository.MAX_CARDS)
                        ChoiceRow(
                            options = listOf("Automática") + limits.map { "Até $it" },
                            selectedIndex = form.maxCards?.let { limits.indexOf(it) + 1 } ?: 0,
                            onSelect = { index -> viewModel.setMaxCards(if (index == 0) null else limits[index - 1]) },
                        )
                    }
                    OptionGroup("Dificuldade") {
                        val levels = DraftDifficulty.entries
                        ChoiceRow(
                            options = levels.map { it.label },
                            selectedIndex = levels.indexOf(form.difficulty),
                            onSelect = { viewModel.setDifficulty(levels[it]) },
                        )
                    }
                    OptionGroup("Tipos de card") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            ChoicePill("IA decide", selected = form.types.isEmpty(), onClick = viewModel::clearTypes)
                            CardType.entries.forEach { type ->
                                ChoicePill(type.label, selected = type in form.types, onClick = { viewModel.toggleType(type) })
                            }
                        }
                    }
                }
            }

            notice?.let { Notice(it, tone = NoticeTone.Info) }
            error?.let { problem ->
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Notice(
                        problem.message,
                        tone = if (problem.kind == AiErrorKind.RateLimited) NoticeTone.Warning else NoticeTone.Error,
                        title = errorTitle(problem.kind),
                    ) {
                        Row {
                            if (problem.retryable) QuietButton("Tentar novamente", onClick = viewModel::generate)
                            if (problem.kind == AiErrorKind.NotConfigured) QuietButton("Adicionar manualmente", onClick = manualTarget)
                            if (problem.kind == AiErrorKind.Unauthorized) QuietButton("Entrar", onClick = { navigator.to(Routes.CONNECT) })
                        }
                    }
                }
            }
        }

        Hairline()
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenH)
                .padding(top = Spacing.md, bottom = Spacing.lg),
        ) {
            if (generating) {
                SectionCard(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, padding = PaddingValues(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InlineProgress("Analisando o texto e criando seus cards", Modifier.weight(1f))
                        QuietButton("Cancelar", onClick = viewModel::cancel)
                    }
                }
            } else {
                GenerateLessonOption(lessonOption, onChange = viewModel::setGenerateLesson)
                PrimaryButton("Gerar cards", onClick = viewModel::generate, icon = R.drawable.ic_sparkle, enabled = !tooLongNow(form.text.length))
            }
        }
    }

    if (pickDeck) {
        DeckPickerDialog(
            title = "Deck de destino",
            options = deckOptions(tree),
            selectedId = form.deckId,
            onPick = viewModel::chooseDeck,
            onDismiss = { pickDeck = false },
            emptyMessage = "Você ainda não tem decks. Crie um primeiro.",
        )
    }
    DeckDialogs(deckDialog, tree, viewModel.operations, onDismiss = { deckDialog = null })
    CodePromptHost(viewModel.codePrompt)
}

@Composable
private fun OptionGroup(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Eyebrow(title)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
        }
        content()
    }
}

private fun tooLongNow(length: Int): Boolean = length > AiRepository.MAX_TEXT

private fun errorTitle(kind: AiErrorKind): String = when (kind) {
    AiErrorKind.Validation -> "Confira os dados"
    AiErrorKind.TooLong -> "Texto longo demais"
    AiErrorKind.NotConfigured -> "IA não configurada"
    AiErrorKind.Unreachable -> "Falha de conexão com a IA"
    AiErrorKind.InvalidResponse -> "Resposta inválida da IA"
    AiErrorKind.RateLimited -> "Limite de gerações"
    AiErrorKind.Network -> "Sem conexão"
    AiErrorKind.Unauthorized -> "Sessão expirada"
    AiErrorKind.Code -> "Código do dia"
    AiErrorKind.Other -> "Não foi possível gerar"
}
