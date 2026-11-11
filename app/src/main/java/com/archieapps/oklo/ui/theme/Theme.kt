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
    val newCount: Color,
    val learnCount: Color,
    val reviewCount: Color,
    val again: Color,
    val hard: Color,
    val good: Color,
    val easy: Color,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F6FDE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7FB),
    onPrimaryContainer = Color(0xFF0B2D6B),
    secondary = Color(0xFF4A5260),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFF1F4),
    onSecondaryContainer = Color(0xFF1B1D21),
    tertiary = Color(0xFF2E8B57),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1D21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1D21),
    surfaceVariant = Color(0xFFEFF1F4),
    onSurfaceVariant = Color(0xFF5F6672),
    surfaceTint = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F9),
    surfaceContainer = Color(0xFFF6F7F9),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE6E9EE),
    inverseSurface = Color(0xFF1B1D21),
    inverseOnSurface = Color(0xFFF6F7F9),
    inversePrimary = Color(0xFF7EA6F2),
    outline = Color(0xFFD5D9E0),
    outlineVariant = Color(0xFFE6E9EE),
    scrim = Color(0xFF000000),
    error = Color(0xFFC8402F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3DF),
    onErrorContainer = Color(0xFF5E1A12),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF3A73DB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1F3A6E),
    onPrimaryContainer = Color(0xFFDCE7FB),
    secondary = Color(0xFFB4BBC6),
    onSecondary = Color(0xFF16181C),
    secondaryContainer = Color(0xFF24282E),
    onSecondaryContainer = Color(0xFFE6E8EC),
    tertiary = Color(0xFF7BC896),
    onTertiary = Color(0xFF0E2A18),
    background = Color(0xFF16181C),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF16181C),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF24282E),
    onSurfaceVariant = Color(0xFF9AA2AE),
    surfaceTint = Color(0xFF16181C),
    surfaceContainerLowest = Color(0xFF121418),
    surfaceContainerLow = Color(0xFF1E2126),
    surfaceContainer = Color(0xFF1E2126),
    surfaceContainerHigh = Color(0xFF24282E),
    surfaceContainerHighest = Color(0xFF2D3239),
    inverseSurface = Color(0xFFE6E8EC),
    inverseOnSurface = Color(0xFF16181C),
    inversePrimary = Color(0xFF2F6FDE),
    outline = Color(0xFF3A4049),
    outlineVariant = Color(0xFF2D3239),
    scrim = Color(0xFF000000),
    error = Color(0xFFF08A7A),
    onError = Color(0xFF3E120B),
    errorContainer = Color(0xFF4A1F19),
    onErrorContainer = Color(0xFFFBE3DF),
)

private val LightPalette = OkloPalette(
    success = Color(0xFF2E8B57),
    successContainer = Color(0xFFE1F2E7),
    warning = Color(0xFF9A6A00),
    warningContainer = Color(0xFFFBF0D5),
    danger = Color(0xFFC8402F),
    dangerContainer = Color(0xFFFBE3DF),
    muted = Color(0xFF5F6672),
    faint = Color(0xFF969DA8),
    hairline = Color(0xFFE6E9EE),
    ink = Color(0xFF1B1D21),
    onInk = Color(0xFFFFFFFF),
    sheet = Color(0xFFF6F7F9),
    newCount = Color(0xFF2F6FDE),
    learnCount = Color(0xFFC8402F),
    reviewCount = Color(0xFF2E8B57),
    again = Color(0xFFC8402F),
    hard = Color(0xFF6B7280),
    good = Color(0xFF2E8B57),
    easy = Color(0xFF2F6FDE),
)

private val DarkPalette = OkloPalette(
    success = Color(0xFF7BC896),
    successContainer = Color(0xFF1B3324),
    warning = Color(0xFFE6B95C),
    warningContainer = Color(0xFF3A2E12),
    danger = Color(0xFFF08A7A),
    dangerContainer = Color(0xFF4A1F19),
    muted = Color(0xFF9AA2AE),
    faint = Color(0xFF6B7380),
    hairline = Color(0xFF2D3239),
    ink = Color(0xFFE6E8EC),
    onInk = Color(0xFF16181C),
    sheet = Color(0xFF1E2126),
    newCount = Color(0xFF7EA6F2),
    learnCount = Color(0xFFF08A7A),
    reviewCount = Color(0xFF7BC896),
    again = Color(0xFFF08A7A),
    hard = Color(0xFFB4BBC6),
    good = Color(0xFF7BC896),
    easy = Color(0xFF7EA6F2),
)

val LocalOkloPalette = staticCompositionLocalOf { LightPalette }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
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
