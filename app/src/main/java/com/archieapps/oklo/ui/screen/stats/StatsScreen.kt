package com.archieapps.oklo.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.R
import com.archieapps.oklo.core.fsrs.IntervalFormat
import com.archieapps.oklo.domain.DayActivity
import com.archieapps.oklo.domain.Distribution
import com.archieapps.oklo.ui.common.PtBr
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.IconAction
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.Metric
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SectionCard
import com.archieapps.oklo.ui.components.SectionTitle
import com.archieapps.oklo.ui.components.SegmentedTabs
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DayWindows = listOf(7, 30)

@Composable
fun StatsScreen(navigator: AppNavigator, deckId: String?) {
    val viewModel = appViewModel(key = "stats-$deckId") { StatsViewModel(it, deckId) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val state = ui ?: return Loading()
    val summary = state.summary

    Column(Modifier.fillMaxSize()) {
        if (deckId == null) {
            TopBar(title = "Estatísticas", large = true) {
                IconAction(R.drawable.ic_settings, "Configurações", onClick = { navigator.to(Routes.SETTINGS) })
            }
        } else {
            TopBar(title = "Estatísticas do deck", subtitle = state.deckName, onBack = navigator::back)
        }
        if (state.missingDeck) {
            EmptyState(title = "Deck não encontrado", message = "Ele pode ter sido excluído.") {
                SecondaryButton("Voltar", onClick = navigator::back, modifier = Modifier.fillMaxWidth())
            }
            return
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (deckId != null) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = Spacing.screenH)
                .padding(top = Spacing.sm, bottom = Spacing.screenBottom),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SectionTitle("Resumo geral")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Metric(summary.totalCards.toString(), "Cards", Modifier.weight(1f))
                Metric(summary.totalDecks.toString(), if (deckId == null) "Decks" else "Decks (com subdecks)", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Metric(summary.cardsReviewedToday.toString(), "Revisados hoje", Modifier.weight(1f))
                Metric(summary.pending.toString(), "Pendentes hoje", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                Metric(IntervalFormat.duration(summary.timeTodayMs), "Tempo hoje", Modifier.weight(1f))
            }

            SectionTitle("Histórico")
            SegmentedTabs(
                options = DayWindows.map { "$it dias" },
                selectedIndex = DayWindows.indexOf(state.days),
                onSelect = { viewModel.days.value = DayWindows[it] },
            )
            SectionCard {
                val total = summary.activity.sumOf { it.reviews }
                Text(
                    "${plural(total, "avaliação", "avaliações")} · ${IntervalFormat.duration(summary.timeWindowMs)} de estudo",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Você estudou em ${summary.activeDays} de ${state.days} dias. O objetivo é enxergar a consistência, não manter uma sequência perfeita.",
                    style = MaterialTheme.typography.bodySmall,
                    color = okloPalette.muted,
                )
                ActivityChart(summary.activity)
                summary.correctRate?.let {
                    Text(
                        "Lembrança no período: ${(it * 100).roundToInt()}% das avaliações não foram “De novo”.",
                        style = MaterialTheme.typography.bodySmall,
                        color = okloPalette.muted,
                    )
                }
            }

            SectionTitle("Distribuição")
            SectionCard { DistributionView(summary.distribution) }
        }
    }
}

@Composable
private fun ActivityChart(activity: List<DayActivity>) {
    val max = (activity.maxOfOrNull { it.reviews } ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val track = okloPalette.hairline
    val formatter = DateTimeFormatter.ofPattern("d/M", PtBr)
    val description = activity.joinToString("; ") { "${it.date.format(formatter)}: ${it.reviews}" }
    val gap = if (activity.size > 10) 3.dp else Spacing.sm
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clearAndSetSemantics { contentDescription = "Avaliações por dia. $description" },
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            activity.forEach { day ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Radii.small)
                        .background(track),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (day.reviews > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(day.reviews.toFloat() / max)
                                .clip(Radii.small)
                                .background(bar),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            Text(activity.firstOrNull()?.date?.format(formatter).orEmpty(), style = MaterialTheme.typography.labelSmall, color = okloPalette.muted, modifier = Modifier.weight(1f))
            Text("máx. $max/dia", style = MaterialTheme.typography.labelSmall, color = okloPalette.muted)
            Spacer(Modifier.weight(1f))
            Text("hoje", style = MaterialTheme.typography.labelSmall, color = okloPalette.muted)
        }
    }
}

@Composable
private fun DistributionView(distribution: Distribution) {
    val palette = okloPalette
    val entries: List<Triple<String, Int, Color>> = listOf(
        Triple("Novos", distribution.newCount, palette.success),
        Triple("Aprendendo", distribution.learning, palette.warning),
        Triple("Revisão", distribution.review, MaterialTheme.colorScheme.primary),
        Triple("Reaprendendo", distribution.relearning, palette.danger),
        Triple("Suspensos", distribution.suspended, palette.faint),
    )
    val total = distribution.total
    if (total == 0) {
        Text("Ainda não há cards.", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(Radii.pill)
            .background(palette.hairline),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        entries.filter { it.second > 0 }.forEach { (_, count, color) ->
            Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(color))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        entries.forEach { (label, count, color) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, Radii.pill))
                Spacer(Modifier.width(Spacing.md - 2.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("$count · ${(count * 100.0 / total).roundToInt()}%", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
