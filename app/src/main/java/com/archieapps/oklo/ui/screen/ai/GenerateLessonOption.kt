package com.archieapps.oklo.ui.screen.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.archieapps.oklo.data.repo.LessonOption
import com.archieapps.oklo.ui.components.CheckMark
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun GenerateLessonOption(option: LessonOption, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(value = option.checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(checked = option.checked)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text("Gerar aula junto", style = MaterialTheme.typography.labelLarge)
            if (option.replacesManualLesson) {
                Text("Substitui a aula atual", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
            }
        }
    }
}
