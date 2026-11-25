package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingThemeSpecTest {

    @Test
    fun fromId_resolvesAllThemesAndDefaultsToLight() {
        val light = ReadingThemeSpec.fromId("LIGHT")
        assertFalse(light.isDark)
        assertEquals(0xFFE5E5F7L, light.backdropArgb)
        assertNull(light.colorMatrixValues)

        val sepia = ReadingThemeSpec.fromId("sepia")
        assertFalse(sepia.isDark)
        assertEquals(0xFFE6DAC3L, sepia.backdropArgb)
        assertNotNull(sepia.colorMatrixValues)
        assertEquals(20, sepia.colorMatrixValues!!.size)

        val green = ReadingThemeSpec.fromId("GREEN")
        assertFalse(green.isDark)
        assertEquals(0xFFD6E8D6L, green.backdropArgb)
        assertNotNull(green.colorMatrixValues)
        assertEquals(20, green.colorMatrixValues!!.size)

        val dark = ReadingThemeSpec.fromId("DARK")
        assertTrue(dark.isDark)
        assertEquals(0xFF121216L, dark.backdropArgb)
        assertNotNull(dark.colorMatrixValues)
        assertEquals(-1f, dark.colorMatrixValues!![0], 1e-4f)
        assertEquals(255f, dark.colorMatrixValues!![4], 1e-4f)

        assertEquals(ReadingThemeSpec.LIGHT, ReadingThemeSpec.fromId("UNKNOWN"))
    }

    @Test
    fun resolveMarginRgb_matchesThemeOverridesAndCustomHex() {
        assertEquals(Triple(30, 30, 34), ReadingThemeSpec.DARK.resolveMarginRgb("#FFFFFF"))
        assertEquals(Triple(30, 30, 34), ReadingThemeSpec.DARK.resolveMarginRgb("#F4ECD8"))

        assertEquals(Triple(244, 236, 216), ReadingThemeSpec.SEPIA.resolveMarginRgb("#FFFFFF"))
        assertEquals(Triple(241, 245, 249), ReadingThemeSpec.SEPIA.resolveMarginRgb("#F1F5F9"))

        assertEquals(Triple(227, 242, 227), ReadingThemeSpec.GREEN.resolveMarginRgb("#FFFFFF"))
        assertEquals(Triple(244, 236, 216), ReadingThemeSpec.GREEN.resolveMarginRgb("#F4ECD8"))

        assertEquals(Triple(255, 255, 255), ReadingThemeSpec.LIGHT.resolveMarginRgb("#FFFFFF"))
        assertEquals(Triple(244, 236, 216), ReadingThemeSpec.LIGHT.resolveMarginRgb("#F4ECD8"))
    }

    @Test
    fun computeTargetRenderScale_respectsThresholdCapAndPixelBudget() {
        // Off-screen or low zoom -> 1f
        assertEquals(1f, ReadingThemeSpec.computeTargetRenderScale(2.5f, false, 1000, 1400), 1e-4f)
        assertEquals(1f, ReadingThemeSpec.computeTargetRenderScale(1.35f, true, 1000, 1400), 1e-4f)
        assertEquals(1f, ReadingThemeSpec.computeTargetRenderScale(2.0f, true, 0, 1400), 1e-4f)

        // Within budget (1000*1400 = 1.4M; 2.0x -> 5.6M <= 6M)
        assertEquals(2.0f, ReadingThemeSpec.computeTargetRenderScale(2.0f, true, 1000, 1400), 1e-4f)

        // Zoom 4.0x capped at 2.5x, then capped by 6M budget on 1000x1400 (~2.07x)
        val capped = ReadingThemeSpec.computeTargetRenderScale(4.0f, true, 1000, 1400)
        assertTrue(capped in 2.0f..2.08f)
        assertTrue(1000 * capped * 1400 * capped <= 6_000_050f)

        // Small page reaches max 2.5f cap cleanly
        assertEquals(2.5f, ReadingThemeSpec.computeTargetRenderScale(3.5f, true, 600, 800), 1e-4f)

        // Huge page already over 6M budget never downscales below 1f
        assertEquals(1f, ReadingThemeSpec.computeTargetRenderScale(2.0f, true, 3000, 3000), 1e-4f)
    }
}
