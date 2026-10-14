package com.archieapps.oklo.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.R
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.ui.common.longDate
import com.archieapps.oklo.ui.components.DeckRow
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.Eyebrow
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.IconAction
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SectionCard
import com.archieapps.oklo.ui.components.SectionTitle
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
    val state = ui ?: return Loading()

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Oklo") {
            IconAction(R.drawable.ic_settings, "Configurações", { navigator.to(Routes.SETTINGS) })
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.screenBottom),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            if (!state.hasDecks) {
                EmptyState(
                    title = "Sua biblioteca está vazia",
                    message = "Crie um deck e adicione cards manualmente, ou cole um texto para a IA gerar cards para você.",
                    icon = R.drawable.ic_library,
                ) {
                    PrimaryButton("Criar um deck", onClick = { navigator.tab(Routes.LIBRARY) })
                    SecondaryButton("Criar cards com IA", onClick = { navigator.to(Routes.aiCreate()) }, icon = R.drawable.ic_sparkle, modifier = Modifier.fillMaxWidth())
                }
                return@Column
            }

            Today(state, onStudy = { navigator.to(Routes.study()) }, onLibrary = { navigator.tab(Routes.LIBRARY) })

            if (state.continueDecks.isNotEmpty()) {
                Column {
                    SectionTitle("Continuar estudando", Modifier.padding(horizontal = Spacing.screenH).padding(bottom = Spacing.xs))
                    Hairline(Modifier.padding(horizontal = Spacing.screenH))
                    state.continueDecks.forEach { node ->
                        DeckRow(
                            node = node,
                            subtitle = continueSubtitle(node),
                            onClick = { navigator.to(Routes.deck(node.id)) },
                            chevron = true,
                        )
                    }
                }
            }

            AiCallout(Modifier.padding(horizontal = Spacing.screenH)) { navigator.to(Routes.aiCreate()) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Today(state: HomeUi, onStudy: () -> Unit, onLibrary: () -> Unit) {
    val palette = okloPalette
    val available = state.summary.reviews + state.summary.news + (state.summary.learning - state.learningLaterCount)
    Column(Modifier.padding(horizontal = Spacing.screenH), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Eyebrow(longDate(state.today))
        Text(
            text = if (state.summary.total > 0) plural(state.summary.total, "card", "cards") + "\npara hoje" else "Tudo em dia\npor hoje",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CountLabel(state.summary.reviews, "revisões", MaterialTheme.colorScheme.primary)
            CountLabel(state.summary.news, "novos", palette.success)
            CountLabel(state.summary.learning, "em aprendizado", palette.warning)
        }
        Spacer(Modifier.height(Spacing.xs))
        if (available > 0) {
            PrimaryButton("Começar a estudar", onClick = onStudy, icon = R.drawable.ic_play)
            if (state.learningLaterCount > 0) {
                Text(
                    "${plural(state.learningLaterCount, "card em aprendizado volta", "cards em aprendizado voltam")} mais tarde hoje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
        } else {
            Text(
                if (!state.hasCards) {
                    "Seus decks ainda não têm cards. Abra um deck para adicionar cards."
                } else if (state.learningLaterCount > 0) {
                    "Nada para agora. ${plural(state.learningLaterCount, "card em aprendizado volta", "cards em aprendizado voltam")} mais tarde hoje."
                } else {
                    "Nenhuma revisão pendente. Você está em dia — volte amanhã."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
            )
            SecondaryButton("Abrir a biblioteca", onClick = onLibrary, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CountLabel(value: Int, label: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Spacer(Modifier.width(Spacing.sm - 2.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted)
    }
}

private fun continueSubtitle(node: DeckNode): String {
    val pathTail = if (node.path.size > 1) node.path.dropLast(1).joinToString(" / ") + " · " else ""
    val what = when {
        node.own.pending > 0 -> plural(node.own.pending, "card para revisar", "cards para revisar")
        else -> plural(node.own.newCount, "card novo", "cards novos")
    }
    return pathTail + what
}

@Composable
private fun AiCallout(modifier: Modifier = Modifier, onClick: () -> Unit) {
    SectionCard(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Criar cards com IA", style = MaterialTheme.typography.titleLarge)
                Text("Transforme qualquer texto em flashcards.", style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = okloPalette.faint, modifier = Modifier.size(20.dp))
        }
    }
}
