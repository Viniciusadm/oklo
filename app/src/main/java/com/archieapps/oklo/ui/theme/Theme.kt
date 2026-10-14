package com.archieapps.oklo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.archieapps.oklo.data.prefs.ThemeMode

@Immutable
data class OkloPalette(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val muted: Color,
    val faint: Color,
    val hairline: Color,
    val ink: Color,
    val onInk: Color,
    val sheet: Color,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFFA6491F),
    onPrimary = Color(0xFFFFFBF6),
    primaryContainer = Color(0xFFF3DFD3),
    onPrimaryContainer = Color(0xFF5A2410),
    secondary = Color(0xFF1F1B16),
    onSecondary = Color(0xFFFBF8F2),
    secondaryContainer = Color(0xFFEAE2D4),
    onSecondaryContainer = Color(0xFF1F1B16),
    tertiary = Color(0xFF3F6E43),
    onTertiary = Color(0xFFFBF8F2),
    background = Color(0xFFF4EFE6),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFF4EFE6),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFECE5D8),
    onSurfaceVariant = Color(0xFF5E574C),
    surfaceTint = Color(0xFFF4EFE6),
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFF8F4EC),
    surfaceContainer = Color(0xFFFBF8F2),
    surfaceContainerHigh = Color(0xFFFBF8F2),
    surfaceContainerHighest = Color(0xFFE8E1D3),
    inverseSurface = Color(0xFF1F1B16),
    inverseOnSurface = Color(0xFFF4EFE6),
    inversePrimary = Color(0xFFE58A5F),
    outline = Color(0xFFCFC5B4),
    outlineVariant = Color(0xFFE2DACB),
    scrim = Color(0xFF1F1B16),
    error = Color(0xFFA63D2F),
    onError = Color(0xFFFFFBF6),
    errorContainer = Color(0xFFF5DEDA),
    onErrorContainer = Color(0xFF5E1A12),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE58A5F),
    onPrimary = Color(0xFF3A1606),
    primaryContainer = Color(0xFF5A2A14),
    onPrimaryContainer = Color(0xFFF8DCCB),
    secondary = Color(0xFFEDE6D9),
    onSecondary = Color(0xFF171511),
    secondaryContainer = Color(0xFF2E2922),
    onSecondaryContainer = Color(0xFFEDE6D9),
    tertiary = Color(0xFF93C495),
    onTertiary = Color(0xFF14240F),
    background = Color(0xFF171511),
    onBackground = Color(0xFFEDE6D9),
    surface = Color(0xFF171511),
    onSurface = Color(0xFFEDE6D9),
    surfaceVariant = Color(0xFF2A2620),
    onSurfaceVariant = Color(0xFFBDB3A3),
    surfaceTint = Color(0xFF171511),
    surfaceContainerLowest = Color(0xFF121010),
    surfaceContainerLow = Color(0xFF1C1915),
    surfaceContainer = Color(0xFF201D18),
    surfaceContainerHigh = Color(0xFF201D18),
    surfaceContainerHighest = Color(0xFF322D26),
    inverseSurface = Color(0xFFEDE6D9),
    inverseOnSurface = Color(0xFF171511),
    inversePrimary = Color(0xFFA6491F),
    outline = Color(0xFF554D42),
    outlineVariant = Color(0xFF353029),
    scrim = Color(0xFF000000),
    error = Color(0xFFE8907F),
    onError = Color(0xFF3E120B),
    errorContainer = Color(0xFF4E201A),
    onErrorContainer = Color(0xFFF7D9D3),
)

private val LightPalette = OkloPalette(
    success = Color(0xFF3F6E43),
    successContainer = Color(0xFFDFE9D8),
    warning = Color(0xFF8A5D0E),
    warningContainer = Color(0xFFF4E6C8),
    danger = Color(0xFFA63D2F),
    dangerContainer = Color(0xFFF5DEDA),
    muted = Color(0xFF6B6358),
    faint = Color(0xFF8A8175),
    hairline = Color(0xFFE2DACB),
    ink = Color(0xFF1F1B16),
    onInk = Color(0xFFFBF8F2),
    sheet = Color(0xFFFBF8F2),
)

private val DarkPalette = OkloPalette(
    success = Color(0xFF93C495),
    successContainer = Color(0xFF23331F),
    warning = Color(0xFFE0B45C),
    warningContainer = Color(0xFF3A2C12),
    danger = Color(0xFFE8907F),
    dangerContainer = Color(0xFF4A1F19),
    muted = Color(0xFFBDB3A3),
    faint = Color(0xFF8E8577),
    hairline = Color(0xFF353029),
    ink = Color(0xFFEDE6D9),
    onInk = Color(0xFF171511),
    sheet = Color(0xFF201D18),
)

val LocalOkloPalette = staticCompositionLocalOf { LightPalette }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val okloPalette: OkloPalette
    @Composable @ReadOnlyComposable get() = LocalOkloPalette.current

@Composable
fun OkloTheme(
    mode: ThemeMode = ThemeMode.System,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * textScale)
    CompositionLocalProvider(
        LocalOkloPalette provides if (dark) DarkPalette else LightPalette,
        LocalDensity provides scaled,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            shapes = AppShapes,
            typography = OkloTypography,
            content = content,
        )
    }
}
