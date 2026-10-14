package com.archieapps.oklo.ui.screen.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.archieapps.oklo.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.ui.common.deckOptions
import com.archieapps.oklo.ui.components.CardContentEditor
import com.archieapps.oklo.ui.components.ConfirmDialog
import com.archieapps.oklo.ui.components.DeckPickerDialog
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.Eyebrow
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SectionCard
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun CardEditorScreen(navigator: AppNavigator, deckId: String?, cardId: String?) {
    val viewModel = appViewModel(key = "editor-$deckId-$cardId") { CardEditorViewModel(it, deckId, cardId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var pickDeck by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) navigator.back()
    }

    BackHandler(enabled = state.dirty && !state.finished) { confirmLeave = true }
    val leave = { if (state.dirty) confirmLeave = true else navigator.back() }

    if (!state.loaded) return Loading()
    if (state.missing) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "Editar card", onBack = navigator::back)
            EmptyState(
                title = "Card não encontrado",
                message = "Ele pode ter sido excluído em outro aparelho.",
                icon = R.drawable.ic_layers,
            ) {
                SecondaryButton("Voltar", onClick = navigator::back, modifier = Modifier.fillMaxWidth())
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(
            title = if (state.editing) "Editar card" else "Novo card",
            subtitle = if (state.savedCount > 0 && !state.editing) "${plural(state.savedCount, "card salvo", "cards salvos")} nesta sessão" else null,
            onBack = leave,
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH)
                .padding(top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SectionCard(Modifier.clickable(role = Role.Button) { pickDeck = true }) {
                Eyebrow("Deck")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.deckId?.let { id -> DeckTree.find(roots, id)?.pathLabel } ?: "Escolher deck",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.deckId == null) okloPalette.muted else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_down),
                        contentDescription = "Trocar deck",
                        tint = okloPalette.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            CardContentEditor(content = state.content, onChange = viewModel::change)
            state.error?.let { Notice(it, tone = NoticeTone.Error) }
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
            PrimaryButton(if (state.editing) "Salvar alterações" else "Salvar card", onClick = { viewModel.save(addAnother = false) }, loading = state.saving)
            if (!state.editing) {
                SecondaryButton("Salvar e adicionar outro", onClick = { viewModel.save(addAnother = true) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (pickDeck) {
        DeckPickerDialog(
            title = "Deck do card",
            options = deckOptions(roots),
            selectedId = state.deckId,
            onPick = viewModel::chooseDeck,
            onDismiss = { pickDeck = false },
            emptyMessage = "Crie um deck na biblioteca antes de adicionar cards.",
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Descartar alterações?",
            message = "O que você escreveu neste card ainda não foi salvo.",
            confirmLabel = "Descartar",
            destructive = true,
            dismissLabel = "Continuar editando",
            onConfirm = navigator::back,
            onDismiss = { confirmLeave = false },
        )
    }
}
