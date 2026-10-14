package com.archieapps.oklo.ui.screen.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.R
import com.archieapps.oklo.core.fsrs.IntervalFormat
import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.ui.common.clock
import com.archieapps.oklo.ui.common.frontLabel
import com.archieapps.oklo.ui.components.ActionSheet
import com.archieapps.oklo.ui.components.CenteredEmptyState
import com.archieapps.oklo.ui.components.ConfirmDialog
import com.archieapps.oklo.ui.components.Eyebrow
import com.archieapps.oklo.ui.components.IconAction
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.Metric
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.ProgressLine
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SheetAction
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.LessonRouteMode
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.CardFrontStyle
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun StudyScreen(navigator: AppNavigator, deckId: String?) {
    val viewModel = appViewModel(key = "study-$deckId") { StudyViewModel(it, deckId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val exit = {
        if (ui is StudyUi.Question && viewModel.hasProgress) {
            confirmExit = true
        } else {
            viewModel.finish()
            navigator.back()
        }
    }
    BackHandler(enabled = ui is StudyUi.Question) { exit() }

    when (val state = ui) {
        StudyUi.Loading -> Loading()
        is StudyUi.NothingDue -> NothingDue(state, onBack = navigator::back)
        is StudyUi.Done -> Completion(
            state = state,
            onHome = {
                viewModel.finish()
                navigator.home()
            },
            onContinue = viewModel::continueStudying,
        )
        is StudyUi.Question -> QuestionScreen(
            state = state,
            onExit = exit,
            onMenu = { menu = true },
            onReveal = viewModel::reveal,
            onTrueFalse = viewModel::respondTrueFalse,
            onChoice = viewModel::respondChoice,
            onRate = viewModel::rate,
            onViewInLesson = {
                state.card.lessonTarget?.let { target ->
                    navigator.to(Routes.lesson(state.card.deckId, LessonRouteMode.Target, target))
                }
            },
        )
    }

    if (menu) {
        ActionSheet(
            title = "Opções da sessão",
            onDismiss = { menu = false },
            actions = listOf(
                SheetAction("Suspender este card", viewModel::suspendCurrent, icon = R.drawable.ic_pause, hint = "Ele sai dos estudos até ser reativado no deck"),
                SheetAction("Encerrar sessão", { exit() }, icon = R.drawable.ic_close),
            ),
        )
    }

    if (confirmExit) {
        ConfirmDialog(
            title = "Sair da sessão?",
            message = "As avaliações feitas até aqui já estão salvas. O card atual volta para a fila da próxima sessão.",
            confirmLabel = "Sair",
            dismissLabel = "Continuar estudando",
            onConfirm = {
                viewModel.finish()
                navigator.back()
            },
            onDismiss = { confirmExit = false },
        )
    }
}

@Composable
private fun QuestionScreen(
    state: StudyUi.Question,
    onExit: () -> Unit,
    onMenu: () -> Unit,
    onReveal: () -> Unit,
    onTrueFalse: (Boolean) -> Unit,
    onChoice: (Int) -> Unit,
    onRate: (Rating) -> Unit,
    onViewInLesson: () -> Unit,
) {
    val content = state.card.content
    val palette = okloPalette
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            IconAction(R.drawable.ic_close, "Sair da sessão", onExit)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.deckName ?: "Todos os decks", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${state.position} de ${state.total} cards", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
            IconAction(R.drawable.ic_more, "Opções da sessão", onMenu)
        }
        ProgressLine(
            progress = ((state.position - 1).toFloat() / state.total.coerceAtLeast(1)).coerceIn(0f, 1f),
            modifier = Modifier
                .padding(horizontal = Spacing.screenH, vertical = Spacing.xs)
                .semantics { contentDescription = "Posição na sessão: ${state.position} de ${state.total}" },
        )
        key(state.card.id) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screenH, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Flashcard(state)

                    when (content.type) {
                        CardType.TrueFalse -> TrueFalseOptions(state, onTrueFalse)
                        CardType.MultipleChoice -> ChoiceOptions(state, onChoice)
                        else -> Unit
                    }

                    if (state.revealed) {
                        state.answeredCorrect?.let { correct ->
                            Notice(
                                tone = if (correct) NoticeTone.Success else NoticeTone.Error,
                                title = if (correct) "Você acertou" else "Resposta incorreta",
                                message = if (correct) {
                                    "Acertar é diferente de lembrar com facilidade. Avalie abaixo como foi lembrar."
                                } else {
                                    "Sugerimos “De novo”, mas a avaliação da lembrança é sua."
                                },
                            )
                        }
                        content.explanation?.let { explanation ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Eyebrow("Explicação")
                                Text(explanation, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (state.canViewInLesson) {
                            SecondaryButton("Ver na aula", onClick = onViewInLesson, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = Spacing.lg)) {
            when {
                !state.revealed && (content.type == CardType.Basic || content.type == CardType.Qa) ->
                    PrimaryButton("Mostrar resposta", onClick = onReveal, icon = R.drawable.ic_visibility)

                !state.revealed -> Text(
                    if (content.type == CardType.TrueFalse) "Escolha verdadeiro ou falso." else "Escolha uma alternativa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                )

                else -> RatingBar(state, onRate)
            }
        }
    }
}

@Composable
private fun Flashcard(state: StudyUi.Question) {
    val content = state.card.content
    val palette = okloPalette
    val hasBack = content.type == CardType.Basic || content.type == CardType.Qa
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (hasBack) 240.dp else 160.dp)
            .shadow(6.dp, Radii.surface, ambientColor = palette.ink.copy(alpha = 0.10f), spotColor = palette.ink.copy(alpha = 0.10f))
            .background(palette.sheet, Radii.surface)
            .border(1.dp, palette.hairline, Radii.surface)
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl + 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Eyebrow(frontLabel(content.type))
        Text(
            content.front,
            style = CardFrontStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        if (hasBack) {
            AnimatedVisibility(
                visible = state.revealed,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Spacer(Modifier.height(Spacing.xs))
                    Box(Modifier.width(32.dp).height(2.dp).background(MaterialTheme.colorScheme.primary, Radii.pill))
                    Eyebrow(if (content.type == CardType.Basic) "Verso" else "Resposta")
                    Text(
                        content.back.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 520.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrueFalseOptions(state: StudyUi.Question, onPick: (Boolean) -> Unit) {
    val expected = state.card.content.answer
    val chosen = (state.response as? Response.TrueFalse)?.chosen
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        listOf(true to "Verdadeiro", false to "Falso").forEach { (value, label) ->
            OptionBlock(
                text = label,
                icon = if (value) R.drawable.ic_check else R.drawable.ic_close,
                revealed = state.revealed,
                isCorrect = expected == value,
                isChosen = chosen == value,
                onClick = { onPick(value) },
                modifier = Modifier.weight(1f),
                centered = true,
            )
        }
    }
}

@Composable
private fun ChoiceOptions(state: StudyUi.Question, onPick: (Int) -> Unit) {
    val chosen = (state.response as? Response.Choice)?.index
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
        val choices = state.card.content.choices.orEmpty()
        val order = state.choiceOrder.takeIf { it.size == choices.size } ?: choices.indices.toList()
        order.forEachIndexed { position, index ->
            val choice = choices[index]
            OptionBlock(
                text = choice.text,
                letter = "${'A' + position}",
                revealed = state.revealed,
                isCorrect = choice.correct,
                isChosen = chosen == index,
                onClick = { onPick(index) },
            )
        }
    }
}

@Composable
private fun OptionBlock(
    text: String,
    revealed: Boolean,
    isCorrect: Boolean,
    isChosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
    letter: String? = null,
    icon: Int? = null,
) {
    val palette = okloPalette
    val (container, border, badge) = when {
        !revealed -> Triple(palette.sheet, palette.hairline, null)
        isCorrect -> Triple(palette.successContainer, palette.success, if (isChosen) "Correta · sua resposta" else "Correta")
        isChosen -> Triple(palette.dangerContainer, palette.danger, "Sua resposta")
        else -> Triple(palette.sheet, palette.hairline, null)
    }
    val accent = if (badge != null) border else palette.muted
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(Radii.control)
            .background(container)
            .border(if (badge != null) 1.5.dp else 1.dp, border, Radii.control)
            .clickable(enabled = !revealed, role = Role.Button, onClick = onClick)
            .semantics { if (badge != null) stateDescription = badge }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        when {
            letter != null -> {
                Box(
                    Modifier.size(28.dp).border(1.dp, if (badge != null) border else MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = MaterialTheme.typography.titleSmall, color = accent)
                }
                Spacer(Modifier.width(Spacing.md))
            }
            icon != null -> {
                Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
            }
        }
        Column(
            if (centered) Modifier else Modifier.weight(1f),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
            if (badge != null) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = border)
            }
        }
    }
}

private val RatingLabels = listOf(
    Rating.Again to "De novo",
    Rating.Hard to "Difícil",
    Rating.Good to "Bom",
    Rating.Easy to "Fácil",
)

@Composable
private fun RatingBar(state: StudyUi.Question, onRate: (Rating) -> Unit) {
    val palette = okloPalette
    val suggestAgain = state.answeredCorrect == false
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
        Eyebrow("Como foi lembrar?", modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RatingLabels.forEach { (rating, label) ->
                val (color, container) = when (rating) {
                    Rating.Again -> palette.danger to palette.dangerContainer
                    Rating.Hard -> palette.warning to palette.warningContainer
                    Rating.Good -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
                    Rating.Easy -> palette.success to palette.successContainer
                }
                val suggested = suggestAgain && rating == Rating.Again
                val interval = state.intervals[rating]?.let { IntervalFormat.short(it) }
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 68.dp)
                        .clip(Radii.control)
                        .background(container)
                        .border(if (suggested) 2.dp else 0.dp, if (suggested) color else Color.Transparent, Radii.control)
                        .clickable(enabled = !state.busy, role = Role.Button) { onRate(rating) }
                        .semantics {
                            contentDescription = buildString {
                                append(label)
                                if (interval != null) append(", próxima revisão em $interval")
                                if (suggested) append(", sugerido")
                            }
                        }
                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm + 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(label, color = color, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    if (interval != null) {
                        Text(interval, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
                    }
                    if (suggested) {
                        Text("sugerido", style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun NothingDue(state: StudyUi.NothingDue, onBack: () -> Unit) {
    CenteredEmptyState(
        title = "Nada para estudar agora",
        message = reasons(state.learningLater, state.reviewsCapped, state.newsCapped)
            ?: "Nenhuma revisão pendente${state.deckName?.let { " em “$it”" } ?: ""}. Aproveite o descanso: a biblioteca continua disponível.",
        icon = R.drawable.ic_school,
        modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = Spacing.screenH),
    ) {
        SecondaryButton("Voltar", onClick = onBack, modifier = Modifier.fillMaxWidth())
    }
}

private fun reasons(later: List<Long>, reviewsCapped: Boolean, newsCapped: Boolean): String? {
    val parts = mutableListOf<String>()
    if (later.isNotEmpty()) {
        parts += "${plural(later.size, "card em aprendizado volta", "cards em aprendizado voltam")} mais tarde hoje (o próximo às ${clock(later.min())})."
    }
    if (reviewsCapped) parts += "O limite diário de revisões foi atingido."
    if (newsCapped) parts += "O limite diário de cards novos foi atingido."
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

@Composable
private fun Completion(state: StudyUi.Done, onHome: () -> Unit, onContinue: () -> Unit) {
    val palette = okloPalette
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenH, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Box(
            Modifier.size(56.dp).background(palette.successContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = palette.success, modifier = Modifier.size(28.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Eyebrow("Fim da sessão")
            Text("Sessão concluída.", style = MaterialTheme.typography.displaySmall)
            Text(
                "Você terminou os cards previstos para esta sessão${state.deckName?.let { " de “$it”" } ?: ""}.",
                style = MaterialTheme.typography.bodyLarge,
                color = palette.muted,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Metric(state.totals.cardsSeen.toString(), "Cards vistos", Modifier.weight(1f))
            Metric(state.totals.reviews.toString(), "Avaliações", Modifier.weight(1f))
            Metric(IntervalFormat.duration(state.totals.timeMs), "Tempo total", Modifier.weight(1f))
        }
        reasons(state.learningLater, state.reviewsCapped, state.newsCapped)?.let {
            Notice(it, tone = NoticeTone.Info, title = "Ainda há cards pendentes")
        }
        Text(
            "Concluir a sessão não significa que o conteúdo foi dominado: o algoritmo traz cada card de volta na hora certa.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.faint,
        )
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton("Voltar ao início", onClick = onHome)
        if (state.canContinue) {
            SecondaryButton("Continuar estudando", onClick = onContinue, modifier = Modifier.fillMaxWidth())
        }
    }
}
