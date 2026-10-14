package com.archieapps.oklo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archieapps.oklo.R
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.ui.theme.NumeralStyle
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    divider: Boolean = false,
    startPadding: Dp = Spacing.screenH,
    endPadding: Dp = Spacing.screenH,
    @DrawableRes icon: Int? = null,
    leading: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = okloPalette
    val titleColor = when {
        !enabled -> palette.faint
        destructive -> palette.danger
        else -> MaterialTheme.colorScheme.onSurface
    }
    val clickModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            clickModifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(start = startPadding, end = endPadding, top = Spacing.md - 2.dp, bottom = Spacing.md - 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> {
                    leading()
                    Spacer(Modifier.width(Spacing.md + 2.dp))
                }
                icon != null -> {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = if (destructive) palette.danger else palette.muted,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(Spacing.lg))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(Spacing.sm))
                trailing()
            }
            if (chevron) {
                Spacer(Modifier.width(Spacing.xs))
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = palette.faint, modifier = Modifier.size(20.dp))
            }
        }
        if (divider) Hairline(Modifier.padding(start = startPadding, end = endPadding))
    }
}

@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.surface,
        color = okloPalette.sheet,
        border = BorderStroke(1.dp, okloPalette.hairline),
    ) {
        Column(Modifier.padding(vertical = Spacing.xs), content = content)
    }
}

@Composable
fun DeckRow(
    node: DeckNode,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    divider: Boolean = true,
    insetH: Dp = Spacing.screenH,
    onLongClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    ListRow(
        title = node.deck.name,
        subtitle = subtitle,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        divider = divider,
        startPadding = insetH + (depth * 20).dp,
        endPadding = if (trailing != null) Spacing.xs else insetH,
        leading = { DeckIcon(node.hasChildren) },
        chevron = chevron,
        trailing = {
            val pending = node.aggregate.pending
            if (pending > 0) PendingCount(pending)
            trailing?.invoke(this)
        },
    )
}

@Composable
fun PendingCount(value: Int, modifier: Modifier = Modifier) {
    Text(
        value.toString(),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = Spacing.xs),
    )
}

@Composable
fun Metric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = okloPalette.ink,
    dot: Color? = null,
) {
    Column(
        modifier
            .background(okloPalette.sheet, Radii.control)
            .border(1.dp, okloPalette.hairline, Radii.control)
            .padding(horizontal = Spacing.md + 2.dp, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = NumeralStyle, color = color, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(6.dp).background(dot, Radii.pill))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.bodySmall, color = okloPalette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
