package com.archieapps.oklo.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.archieapps.oklo.R
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

val ScreenPadding = PaddingValues(horizontal = Spacing.screenH)

@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    large: Boolean = false,
    divider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        if (large) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Spacing.screenH - 4.dp, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) BackAction(onBack)
                Spacer(Modifier.weight(1f))
                actions()
            }
            Column(Modifier.padding(horizontal = Spacing.screenH).padding(bottom = Spacing.md)) {
                if (subtitle != null) Eyebrow(subtitle)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .padding(start = if (onBack == null) Spacing.screenH else Spacing.xs, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) BackAction(onBack)
                Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = okloPalette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
            }
        }
        if (divider) Hairline()
    }
}

@Composable
private fun BackAction(onBack: () -> Unit) {
    IconAction(R.drawable.ic_arrow_back, "Voltar", onBack)
}

@Composable
fun IconAction(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) tint else okloPalette.faint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, startIndent: Dp = 0.dp) {
    HorizontalDivider(modifier.padding(start = startIndent), thickness = 1.dp, color = okloPalette.hairline)
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    spacing: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Radii.surface,
        color = okloPalette.sheet,
        border = BorderStroke(1.dp, okloPalette.hairline),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = okloPalette.muted) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun ButtonIcon(@DrawableRes icon: Int?, vector: ImageVector?, size: Dp) {
    when {
        icon != null -> Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(size))
        vector != null -> Icon(vector, contentDescription = null, modifier = Modifier.size(size))
        else -> return
    }
    Spacer(Modifier.width(Spacing.sm))
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes icon: Int? = null,
    vector: ImageVector? = null,
) {
    val palette = okloPalette
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = Radii.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.ink,
            contentColor = palette.onInk,
            disabledContainerColor = palette.hairline,
            disabledContentColor = palette.faint,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.faint)
            Spacer(Modifier.width(Spacing.md))
        } else {
            ButtonIcon(icon, vector, 20.dp)
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    @DrawableRes icon: Int? = null,
    vector: ImageVector? = null,
) {
    val content = if (destructive) okloPalette.danger else okloPalette.ink
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = Radii.pill,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else okloPalette.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content, disabledContentColor = okloPalette.faint),
        contentPadding = PaddingValues(horizontal = Spacing.xl - 4.dp, vertical = Spacing.md - 2.dp),
    ) {
        ButtonIcon(icon, vector, 18.dp)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    accent: Boolean = false,
    @DrawableRes icon: Int? = null,
) {
    val content = when {
        destructive -> okloPalette.danger
        accent -> MaterialTheme.colorScheme.primary
        else -> okloPalette.ink
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = Radii.pill,
        colors = ButtonDefaults.textButtonColors(contentColor = content, disabledContentColor = okloPalette.faint),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        ButtonIcon(icon, null, 18.dp)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = R.drawable.ic_layers,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(okloPalette.sheet, CircleShape)
                    .border(1.dp, okloPalette.hairline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            actions()
        }
    }
}

@Composable
fun CenteredEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = R.drawable.ic_layers,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(title, message, icon = icon, actions = actions)
    }
}

enum class NoticeTone { Info, Success, Warning, Error }

@Composable
fun Notice(
    message: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Info,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val palette = okloPalette
    val (container, content, label) = when (tone) {
        NoticeTone.Info -> Triple(MaterialTheme.colorScheme.secondaryContainer, palette.ink, "Informação")
        NoticeTone.Success -> Triple(palette.successContainer, palette.success, "Sucesso")
        NoticeTone.Warning -> Triple(palette.warningContainer, palette.warning, "Atenção")
        NoticeTone.Error -> Triple(palette.dangerContainer, palette.danger, "Erro")
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(container, Radii.control)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md + 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = title ?: label, style = MaterialTheme.typography.titleSmall, color = content)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (action != null) {
            Spacer(Modifier.height(2.dp))
            action()
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, trackColor = okloPalette.hairline)
    }
}

@Composable
fun InlineProgress(label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, trackColor = okloPalette.hairline)
        Spacer(Modifier.width(Spacing.sm + 2.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = okloPalette.muted) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.35f), Radii.pill)
            .padding(horizontal = Spacing.sm + 2.dp, vertical = 3.dp),
    )
}

@Composable
fun DeckIcon(hasChildren: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, Radii.control),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (hasChildren) R.drawable.ic_folder else R.drawable.ic_layers),
            contentDescription = if (hasChildren) "Deck com subdecks" else "Deck",
            tint = okloPalette.ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

fun plural(count: Int, singular: String, pluralForm: String): String =
    "$count ${if (count == 1) singular else pluralForm}"
