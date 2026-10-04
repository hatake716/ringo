package io.github.hatake716.ohagi.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import io.github.hatake716.ohagi.ui.theme.DarkAppearanceColors
import io.github.hatake716.ohagi.ui.theme.LightAppearanceColors
import kotlin.test.Test
import kotlin.test.assertTrue

class ThemeContrastTest {
    @Test
    fun tintedGlassKeepsTextReadableOnExtremeWallpapers() {
        for (palette in listOf(LightAppearanceColors, DarkAppearanceColors)) {
            for (wallpaper in listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue)) {
                val glass = glassColors(GlassTone.Regular, 1f, wallpaper, null, 1f, 0f, palette.isDark)
                val background = glass.fill.compositeOver(wallpaper)
                for (text in listOf(palette.content, palette.secondaryContent)) {
                    val a = text.luminance()
                    val b = background.luminance()
                    val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                    assertTrue(contrast >= 4.5f, "dark=${palette.isDark}, wallpaper=$wallpaper, contrast=$contrast")
                }
            }
        }
    }
}
