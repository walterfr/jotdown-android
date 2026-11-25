package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenBoxPresetUtilTest {

    @Test
    fun defaultPresets_returnsExpectedFourDefaults() {
        val defaults = PenBoxPresetUtil.defaultPresets()
        assertEquals(4, defaults.size)
        assertEquals("p1", defaults[0].id)
        assertEquals("PEN", defaults[0].toolName)
        assertEquals(0xFF000000.toInt(), defaults[0].colorArgb)
        assertEquals(0.7f, defaults[0].widthMultiplier, 1e-4f)
        assertEquals("HIGHLIGHTER", defaults[3].toolName)
    }

    @Test
    fun addPreset_validatesToolClampsWidthSkipsDupesAndCapsAtMax8() {
        var list = PenBoxPresetUtil.defaultPresets()

        // Disallowed tool ignored
        val unchangedInvalid = PenBoxPresetUtil.addPreset(list, "ERASER", 0xFF000000.toInt(), 1.0f) { "x" }
        assertEquals(list, unchangedInvalid)

        // Exact duplicate skipped (width diff < 0.05)
        val unchangedDup = PenBoxPresetUtil.addPreset(list, "PEN", 0xFF000000.toInt(), 0.72f) { "x" }
        assertEquals(list, unchangedDup)

        // Width clamped to 0.2..4.0
        list = PenBoxPresetUtil.addPreset(list, "PENCIL", 0xFF10B981.toInt(), 9.0f) { "p5" }
        assertEquals(5, list.size)
        assertEquals(4.0f, list.last().widthMultiplier, 1e-4f)

        // Fill up to 8 and add 9th -> drops oldest
        list = PenBoxPresetUtil.addPreset(list, "PEN", 0xFF111111.toInt(), 0.5f) { "p6" }
        list = PenBoxPresetUtil.addPreset(list, "PEN", 0xFF222222.toInt(), 1.0f) { "p7" }
        list = PenBoxPresetUtil.addPreset(list, "PEN", 0xFF333333.toInt(), 1.5f) { "p8" }
        assertEquals(PenBoxPresetUtil.MAX_PRESETS, list.size)

        list = PenBoxPresetUtil.addPreset(list, "HIGHLIGHTER", 0xFF444444.toInt(), 2.0f) { "p9" }
        assertEquals(PenBoxPresetUtil.MAX_PRESETS, list.size)
        assertEquals("p2", list.first().id)
        assertEquals("p9", list.last().id)
    }

    @Test
    fun removePreset_removesMatchingIdOnly() {
        val defaults = PenBoxPresetUtil.defaultPresets()
        val updated = PenBoxPresetUtil.removePreset(defaults, "p2")
        assertEquals(3, updated.size)
        assertTrue(updated.none { it.id == "p2" })
    }

    @Test
    fun serializeAndParsePresets_roundTripsAndHandlesCorruptInput() {
        val original = listOf(
            PenBoxPresetUtil.PenPreset("a1", "PEN", 0xFF123456.toInt(), 1.25f),
            PenBoxPresetUtil.PenPreset("a2", "PENCIL", 0xFFABCDEF.toInt(), 0.5f),
            PenBoxPresetUtil.PenPreset("a3", "HIGHLIGHTER", 0xFFFDE047.toInt(), 2.0f)
        )
        val json = PenBoxPresetUtil.serializePresets(original)
        val parsed = PenBoxPresetUtil.parsePresets(json)

        assertEquals(3, parsed.size)
        assertEquals("a1", parsed[0].id)
        assertEquals("PEN", parsed[0].toolName)
        assertEquals(0xFF123456.toInt(), parsed[0].colorArgb)
        assertEquals(1.25f, parsed[0].widthMultiplier, 0.01f)

        assertEquals(PenBoxPresetUtil.defaultPresets(), PenBoxPresetUtil.parsePresets(null))
        assertEquals(PenBoxPresetUtil.defaultPresets(), PenBoxPresetUtil.parsePresets("   "))
        assertEquals(PenBoxPresetUtil.defaultPresets(), PenBoxPresetUtil.parsePresets("{corrupt"))
    }
}
