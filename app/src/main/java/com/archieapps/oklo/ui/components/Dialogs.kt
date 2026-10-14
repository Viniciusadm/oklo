package com.archieapps.oklo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.archieapps.oklo.R
import com.archieapps.oklo.core.session.CodePad
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissLabel: String = "Cancelar",
) {
    OkloDialog(
        onDismiss = onDismiss,
        title = title,
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted) },
        confirmButton = {
            QuietButton(
                label = confirmLabel,
                destructive = destructive,
                accent = !destructive,
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            )
        },
        dismissButton = { QuietButton(label = dismissLabel, onClick = onDismiss) },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> String?,
    onDismiss: () -> Unit,
    supporting: String? = null,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    OkloDialog(
        onDismiss = onDismiss,
        title = title,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted)
                }
                OkloTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = label,
                    singleLine = true,
                    error = error,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            QuietButton(label = confirmLabel, accent = true, onClick = {
                val problem = onConfirm(value)
                if (problem == null) onDismiss() else error = problem
            })
        },
        dismissButton = { QuietButton(label = "Cancelar", onClick = onDismiss) },
    )
}

data class SheetAction(
    val label: String,
    val onClick: () -> Unit,
    @param:DrawableRes val icon: Int? = null,
    val vector: ImageVector? = null,
    val destructive: Boolean = false,
    val hint: String? = null,
    val selected: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OkloSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
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
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.md)) {
            if (title != null) {
                Column(Modifier.padding(horizontal = Spacing.screenH).padding(top = Spacing.xs, bottom = Spacing.md)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
                    }
                }
                Hairline(Modifier.padding(horizontal = Spacing.screenH))
                Spacer(Modifier.height(Spacing.xs))
            }
            content()
        }
    }
}

@Composable
fun ActionSheet(
    title: String,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    OkloSheet(onDismiss = onDismiss, title = title, subtitle = subtitle) {
        actions.forEach { action ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .clickable(role = Role.Button) {
                        onDismiss()
                        action.onClick()
                    }
                    .padding(horizontal = Spacing.screenH, vertical = Spacing.sm + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (action.destructive) okloPalette.danger else okloPalette.ink
                when {
                    action.icon != null -> Icon(painterResource(action.icon), null, tint = if (action.destructive) tint else okloPalette.muted, modifier = Modifier.size(22.dp))
                    action.vector != null -> Icon(action.vector, null, tint = if (action.destructive) tint else okloPalette.muted, modifier = Modifier.size(22.dp))
                    else -> Spacer(Modifier.size(22.dp))
                }
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(action.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                    if (action.hint != null) {
                        Text(action.hint, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
                    }
                }
                if (action.selected) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

data class DeckOption(
    val id: String?,
    val label: String,
    val depth: Int,
    val enabled: Boolean = true,
)

@Composable
fun DeckPickerDialog(
    title: String,
    options: List<DeckOption>,
    selectedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    emptyMessage: String = "Nenhum deck disponível.",
) {
    OkloSheet(onDismiss = onDismiss, title = title) {
        if (options.isEmpty()) {
            Text(
                emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = okloPalette.muted,
                modifier = Modifier.padding(horizontal = Spacing.screenH, vertical = Spacing.lg),
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(options, key = { it.id ?: "__root__" }) { option ->
                    val selected = option.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(enabled = option.enabled, role = Role.RadioButton) {
                                onPick(option.id)
                                onDismiss()
                            }
                            .padding(start = Spacing.screenH + (option.depth * 18).dp, end = Spacing.screenH),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(if (option.id == null) R.drawable.ic_library else R.drawable.ic_layers),
                            contentDescription = null,
                            tint = if (option.enabled) okloPalette.muted else okloPalette.hairline,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(Spacing.md + 2.dp))
                        Text(
                            option.label,
                            modifier = Modifier.weight(1f),
                            style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            color = if (option.enabled) MaterialTheme.colorScheme.onSurface else okloPalette.faint,
                        )
                        if (selected) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CodeDialog(
    keypad: List<String>,
    chosen: List<String?>,
    error: String?,
    hardStopped: Boolean,
    remaining: Int,
    onPick: (String) -> Unit,
    onErase: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val typed = chosen.count { it != null }
    val complete = typed == CodePad.SLOTS
    OkloDialog(
        onDismiss = onDismiss,
        title = "Código do dia",
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Esta ação exige o código de acesso de hoje. Ele fica guardado só até o fim do dia. " +
                        "Códigos errados contam para o bloqueio da conta (restam $remaining).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (hardStopped) {
                    Text(
                        "Por segurança, o app parou de aceitar códigos hoje.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = okloPalette.danger,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(MaterialTheme.colorScheme.background, Radii.control)
                        .border(1.dp, okloPalette.hairline, Radii.control),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (typed == 0) "Toque nos pares abaixo" else "•".repeat(typed * 2),
                        style = if (typed == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                        color = if (typed == 0) okloPalette.muted else MaterialTheme.colorScheme.onSurface,
                        letterSpacing = if (typed == 0) 0.sp else 6.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (error != null) {
                    Text(error, color = okloPalette.danger, style = MaterialTheme.typography.bodySmall)
                }
                val keys: List<String?> = keypad + listOf(null)
                keys.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)) {
                        row.forEach { option ->
                            val enabled = !hardStopped && if (option == null) typed > 0 else !complete
                            Box(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .clip(Radii.pill)
                                    .border(1.dp, if (enabled) MaterialTheme.colorScheme.outline else okloPalette.hairline, Radii.pill)
                                    .clickable(enabled = enabled, role = Role.Button) {
                                        if (option == null) onErase() else onPick(option)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = option?.let { CodePad.label(it) } ?: "Apagar",
                                    style = if (option == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurface else okloPalette.faint,
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            QuietButton(label = "Confirmar", onClick = onSubmit, enabled = complete && !hardStopped, accent = true)
        },
        dismissButton = { QuietButton(label = "Cancelar", onClick = onDismiss) },
    )
}

@Composable
fun OkloDialog(
    onDismiss: () -> Unit,
    title: String,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = okloPalette.sheet,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shape = Radii.surface,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
    )
}
