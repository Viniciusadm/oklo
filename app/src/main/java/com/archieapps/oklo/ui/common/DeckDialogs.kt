package com.archieapps.oklo.ui.common

import androidx.compose.runtime.Composable
import com.archieapps.oklo.data.repo.DeleteImpact
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.ui.components.ConfirmDialog
import com.archieapps.oklo.ui.components.DeckPickerDialog
import com.archieapps.oklo.ui.components.TextInputDialog
import com.archieapps.oklo.ui.components.plural

sealed interface DeckDialog {
    data class Create(val parentId: String?, val parentName: String?) : DeckDialog
    data class Rename(val id: String, val name: String) : DeckDialog
    data class Move(val id: String, val name: String, val parentId: String?) : DeckDialog
    data class Delete(val id: String, val name: String, val impact: DeleteImpact) : DeckDialog
}

@Composable
fun DeckDialogs(
    dialog: DeckDialog?,
    roots: List<DeckNode>,
    operations: DeckOperations,
    onDismiss: () -> Unit,
    afterDelete: () -> Unit = {},
) {
    when (dialog) {
        null -> Unit
        is DeckDialog.Create -> TextInputDialog(
            title = if (dialog.parentId == null) "Novo deck" else "Novo subdeck",
            label = "Nome",
            initial = "",
            supporting = dialog.parentName?.let { "Dentro de “$it”." },
            confirmLabel = "Criar",
            onConfirm = { name ->
                operations.validateName(name) ?: run {
                    operations.create(name, dialog.parentId)
                    null
                }
            },
            onDismiss = onDismiss,
        )

        is DeckDialog.Rename -> TextInputDialog(
            title = "Renomear deck",
            label = "Nome",
            initial = dialog.name,
            confirmLabel = "Salvar",
            onConfirm = { name ->
                operations.validateName(name) ?: run {
                    operations.rename(dialog.id, name)
                    null
                }
            },
            onDismiss = onDismiss,
        )

        is DeckDialog.Move -> DeckPickerDialog(
            title = "Mover “${dialog.name}” para",
            options = deckOptions(roots, disabledSubtreeOf = dialog.id, includeRoot = true),
            selectedId = dialog.parentId,
            onPick = { target -> operations.move(dialog.id, target) },
            onDismiss = onDismiss,
        )

        is DeckDialog.Delete -> {
            val impact = dialog.impact
            val details = buildList {
                if (impact.subdecks > 0) add(plural(impact.subdecks, "subdeck", "subdecks"))
                if (impact.cards > 0) add(plural(impact.cards, "card", "cards"))
            }
            val message = if (details.isEmpty()) {
                "O deck está vazio. Esta ação não pode ser desfeita."
            } else {
                "Isso também exclui ${details.joinToString(" e ")}" +
                    (if (impact.subdecks > 0) ", incluindo todos os subdecks e os cards deles" else "") +
                    ". O histórico de estudo é mantido, mas a ação não pode ser desfeita."
            }
            ConfirmDialog(
                title = "Excluir “${dialog.name}”?",
                message = message,
                confirmLabel = if (impact.subdecks > 0) "Excluir deck e subdecks" else "Excluir",
                destructive = true,
                onConfirm = { operations.delete(dialog.id, dialog.name, afterDelete) },
                onDismiss = onDismiss,
            )
        }
    }
}
