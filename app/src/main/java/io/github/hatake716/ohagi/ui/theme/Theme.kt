package io.github.hatake716.ohagi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hatake716.ohagi.data.ThemeMode

// おはぎ由来のパレット: 小豆・きなこ・白米
val Azuki = Color(0xFFC96F7B)
val AzukiDeep = Color(0xFF8A3B4A)
val Kinako = Color(0xFFD9B382)
val Kome = Color(0xFFF5EFE6)
val Ink = Color(0xFF17121A)
val InkSoft = Color(0xFF241D2B)

/** 壁紙の上に重ねる半透明パネル色 */
val PanelScrim = Color(0xCC17121A)
val PanelScrimLight = Color(0x991F1826)
val TileBorder = Color(0x33FFFFFF)

private val OhagiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5FF),
    onPrimary = Color(0xFF002E55),
    primaryContainer = Color(0xFF174570),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Kinako,
    onSecondary = Ink,
    background = Color.Transparent,
    onBackground = Color(0xFFF7F7FA),
    surface = InkSoft,
    onSurface = Color(0xFFF7F7FA),
    surfaceVariant = InkSoft,
    onSurfaceVariant = Color(0xFFCDC3D2),
    surfaceContainer = InkSoft,
    surfaceContainerHigh = Color(0xFF2C2434),
    surfaceContainerHighest = Color(0xFF352C3E),
    surfaceContainerLow = Ink,
    surfaceContainerLowest = Ink,
    outline = Color(0x66FFFFFF),
    error = Color(0xFFFFB4AB),
)

private val OhagiLightColorScheme = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E8FF),
    onPrimaryContainer = Color(0xFF002E55),
    secondary = Color(0xFF516447),
    onSecondary = Color.White,
    background = Color.Transparent,
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF8F8FA),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF52525A),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFECECF1),
    surfaceContainerHighest = Color(0xFFE5E5EB),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainerLowest = Color.White,
    outline = Color(0xFF75757D),
    error = Color(0xFFB42318),
)

/** Shared colors for glass surfaces and custom Canvas drawing outside Material widgets. */
@Immutable
data class OhagiAppearanceColors(
    val isDark: Boolean,
    val content: Color,
    val secondaryContent: Color,
    val backdrop: Color,
    val glassBase: Color,
    val separator: Color,
    val controlFill: Color,
    val pressedFill: Color,
    val accent: Color,
    val destructive: Color,
)

internal val DarkAppearanceColors = OhagiAppearanceColors(
    isDark = true,
    content = Color(0xFFF7F7FA),
    secondaryContent = Color(0xFFE2E2E8),
    backdrop = Ink,
    glassBase = Color(0xFF16151B),
    separator = Color(0x33FFFFFF),
    controlFill = Color(0x18FFFFFF),
    pressedFill = Color(0x29FFFFFF),
    accent = Color(0xFF64B5FF),
    destructive = Color(0xFFFF6961),
)

internal val LightAppearanceColors = OhagiAppearanceColors(
    isDark = false,
    content = Color(0xFF1C1C1E),
    secondaryContent = Color(0xFF44444C),
    backdrop = Color(0xFFF2F2F7),
    glassBase = Color(0xFFF9FAFC),
    separator = Color(0x291C1C1E),
    controlFill = Color(0x0D1C1C1E),
    pressedFill = Color(0x1F1C1C1E),
    accent = Color(0xFF005AC1),
    destructive = Color(0xFFB42318),
)

val LocalOhagiColors = staticCompositionLocalOf { DarkAppearanceColors }

private val OhagiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun OhagiTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark(isSystemInDarkTheme())
    CompositionLocalProvider(
        LocalOhagiColors provides if (dark) DarkAppearanceColors else LightAppearanceColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) OhagiDarkColorScheme else OhagiLightColorScheme,
            shapes = OhagiShapes,
            content = content,
        )
    }
}
