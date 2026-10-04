package io.github.hatake716.ohagi.ui.common

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassTransparencyTest {
    @Test
    fun clearGlassHasNoOpaqueFloorEvenForUnblurredModals() {
        for (dark in listOf(false, true)) {
            for (tone in GlassTone.entries) {
                for (floor in listOf(0f, MODAL_GLASS_MIN_OPACITY_BLURRED, MODAL_GLASS_MIN_OPACITY_OPAQUE)) {
                    val glass = colors(tone, 0f, floor, dark)
                    assertTrue(glass.fill.alpha <= 0.105f, "dark=$dark, tone=$tone, floor=$floor")
                    // The diffusion must not put a white veil back over the clear fill.
                    val combinedOpacity = 1f - (1f - glass.fill.alpha) * (1f - glass.diffusionAlpha)
                    assertTrue(combinedOpacity <= 0.13f)
                }
            }
        }
    }

    @Test
    fun layeredLibraryAndModalStayTransparentAtClearSetting() {
        for (dark in listOf(false, true)) {
            val glass = colors(GlassTone.Regular, 0f, MODAL_GLASS_MIN_OPACITY_OPAQUE, dark)
            for (blurred in listOf(false, true)) {
                // Worst case: a sheet, over a library card, over the page background.
                val transmission = (1f - glass.fill.alpha) * (1f - glass.fill.alpha) *
                    (1f - glass.diffusionAlpha) * (1f - glass.diffusionAlpha) *
                    (1f - wallpaperBackdropOpacity(0f, 1f)) * (1f - modalScrimColor(0f, blurred).alpha)
                assertTrue(transmission > 0.58f, "dark=$dark, blurred=$blurred, transmission=$transmission")
            }
        }
    }

    @Test
    fun sliderProgressivelyTintsGlassAndBackdropInBothThemes() {
        for (dark in listOf(false, true)) {
            for (tone in GlassTone.entries) {
                for (floor in listOf(0f, MODAL_GLASS_MIN_OPACITY_BLURRED, MODAL_GLASS_MIN_OPACITY_OPAQUE)) {
                    val values = (0..20).map { colors(tone, it / 20f, floor, dark).fill.alpha }
                    assertTrue(values.zipWithNext().all { (a, b) -> b > a })
                    assertEquals(values.first(), colors(tone, -1f, floor, dark).fill.alpha)
                    assertEquals(values.last(), colors(tone, 2f, floor, dark).fill.alpha)
                }
            }
        }
        for (depth in listOf(0f, 0.5f, 1f)) {
            val values = (0..20).map { wallpaperBackdropOpacity(it / 20f, depth) }
            assertTrue(values.zipWithNext().all { (a, b) -> b > a })
            assertTrue(values.first() <= 0.16f)
            assertTrue(values.last() >= 0.88f)
        }
    }

    private fun colors(tone: GlassTone, tint: Float, floor: Float, dark: Boolean) =
        glassColors(tone, tint, Color.Magenta, null, 1f, floor, dark)
}
