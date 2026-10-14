package com.archieapps.oklo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.archieapps.oklo.R
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun OkloTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val palette = okloPalette
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when {
            error != null -> palette.danger
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "fieldBorder",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = if (enabled) palette.ink else palette.faint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) MaterialTheme.colorScheme.onSurface else palette.faint),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .background(palette.sheet, Radii.control)
                        .border(if (focused || error != null) 1.5.dp else 1.dp, borderColor, Radii.control)
                        .padding(start = if (leadingIcon != null) Spacing.md else Spacing.lg, end = if (trailing != null) Spacing.xs else Spacing.lg),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            painterResource(leadingIcon),
                            contentDescription = null,
                            tint = palette.muted,
                            modifier = Modifier.padding(top = if (singleLine) 0.dp else 13.dp).size(20.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm + 2.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical = 13.dp)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = palette.faint)
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        val note = error ?: supporting
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = if (error != null) palette.danger else palette.muted)
        }
    }
}

@Composable
fun SegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = okloPalette
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(top = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (selected) palette.ink else palette.muted,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                    Spacer(Modifier.weight(1f).heightIn(min = Spacing.md - 2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(2.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, Radii.pill),
                    )
                }
            }
        }
        Hairline()
    }
}

@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val palette = okloPalette
    val container = if (selected) palette.ink else palette.sheet
    val content = when {
        !enabled -> palette.faint
        selected -> palette.onInk
        else -> palette.ink
    }
    Row(
        modifier
            .heightIn(min = 40.dp)
            .clip(Radii.pill)
            .background(container)
            .border(1.dp, if (selected) palette.ink else MaterialTheme.colorScheme.outlineVariant, Radii.pill)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.lg - 2.dp, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEachIndexed { index, label ->
            ChoicePill(label, selected = index == selectedIndex, onClick = { onSelect(index) }, enabled = enabled)
        }
    }
}

@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = okloPalette
    Box(
        modifier
            .size(20.dp)
            .border(if (selected) 6.dp else 1.5.dp, if (!enabled) palette.hairline else if (selected) palette.ink else MaterialTheme.colorScheme.outline, CircleShape),
    )
}

@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = okloPalette
    Box(
        modifier
            .size(22.dp)
            .background(if (checked) palette.ink else palette.sheet, Radii.small)
            .border(1.5.dp, if (!enabled) palette.hairline else if (checked) palette.ink else MaterialTheme.colorScheme.outline, Radii.small),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = palette.onInk, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun OkloCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .size(44.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        CheckMark(checked, enabled = enabled)
    }
}

@Composable
fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = Spacing.screenH,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = Spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioMark(selected, enabled = enabled)
        Spacer(Modifier.width(Spacing.md + 2.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else okloPalette.faint)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
            }
        }
    }
}

@Composable
fun OkloSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = okloPalette
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = palette.onInk,
            checkedTrackColor = palette.ink,
            checkedBorderColor = palette.ink,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = palette.sheet,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    horizontalPadding: androidx.compose.ui.unit.Dp = Spacing.screenH,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = horizontalPadding, vertical = Spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else okloPalette.faint)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
            }
        }
        Spacer(Modifier.width(Spacing.md))
        OkloSwitch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun OkloSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val palette = okloPalette
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = palette.ink,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = palette.hairline,
            activeTickColor = palette.onInk,
            inactiveTickColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(Radii.pill)
            .background(okloPalette.hairline),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(Radii.pill)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun SelectableBlock(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = okloPalette
    Box(
        modifier
            .clip(Radii.control)
            .background(palette.sheet)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) palette.ink else palette.hairline, Radii.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        content()
    }
}
