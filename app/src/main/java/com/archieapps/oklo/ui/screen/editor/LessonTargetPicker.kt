package com.archieapps.oklo.ui.screen.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archieapps.oklo.R
import com.archieapps.oklo.ui.components.OkloSheet
import com.archieapps.oklo.ui.components.OkloTextField
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun LessonTargetPicker(
    options: List<LinkOption>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(options, query) { LessonLinks.filter(options, query) }
    OkloSheet(onDismiss = onDismiss, title = "Trecho da aula") {
        OkloTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar página ou trecho",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH),
        )
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            item(key = "__none__") {
                PickerRow(label = "Sem vínculo", detail = null, selected = selected == null) {
                    onPick(null)
                    onDismiss()
                }
            }
            items(visible, key = { it.target }) { option ->
                PickerRow(label = option.label, detail = option.pageTitle?.takeIf { it != option.label }, selected = option.target == selected) {
                    onPick(option.target)
                    onDismiss()
                }
            }
            if (visible.isEmpty()) {
                item(key = "__empty__") {
                    Text(
                        "Nenhum trecho encontrado.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = okloPalette.muted,
                        modifier = Modifier.padding(horizontal = Spacing.screenH, vertical = Spacing.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (selected) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = "Selecionado", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}
