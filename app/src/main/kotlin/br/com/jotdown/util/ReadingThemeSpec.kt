package br.com.jotdown.util

import kotlin.math.sqrt

/**
 * Pure-Kotlin reader theme, reading-mode constants, margin tint resolution,
 * and pixel-budgeted Hi-DPI zoom scale calculation.
 */
data class ReadingThemeSpec(
    val id: String,
    val isDark: Boolean,
    val backdropArgb: Long,
    val colorMatrixValues: FloatArray?
) {
    fun resolveMarginRgb(pagePaddingColor: String): Triple<Int, Int, Int> {
        return when {
            id == THEME_DARK -> Triple(30, 30, 34)
            id == THEME_SEPIA && pagePaddingColor.equals(PagePaddingSpec.COLOR_WHITE, ignoreCase = true) ->
                Triple(244, 236, 216)
            id == THEME_GREEN && pagePaddingColor.equals(PagePaddingSpec.COLOR_WHITE, ignoreCase = true) ->
                Triple(227, 242, 227)
            else -> PagePaddingSpec.parseHexRgb(pagePaddingColor)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReadingThemeSpec) return false
        if (id != other.id) return false
        if (isDark != other.isDark) return false
        if (backdropArgb != other.backdropArgb) return false
        if (colorMatrixValues == null && other.colorMatrixValues == null) return true
        if (colorMatrixValues == null || other.colorMatrixValues == null) return false
        return colorMatrixValues.contentEquals(other.colorMatrixValues)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + isDark.hashCode()
        result = 31 * result + backdropArgb.hashCode()
        result = 31 * result + (colorMatrixValues?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val THEME_LIGHT = "LIGHT"
        const val THEME_SEPIA = "SEPIA"
        const val THEME_GREEN = "GREEN"
        const val THEME_DARK = "DARK"

        const val MODE_VERTICAL = "VERTICAL"
        const val MODE_HORIZONTAL = "HORIZONTAL"

        val LIGHT = ReadingThemeSpec(
            id = THEME_LIGHT,
            isDark = false,
            backdropArgb = 0xFFE5E5F7L,
            colorMatrixValues = null
        )

        val SEPIA = ReadingThemeSpec(
            id = THEME_SEPIA,
            isDark = false,
            backdropArgb = 0xFFE6DAC3L,
            colorMatrixValues = floatArrayOf(
                0.96f, 0f, 0f, 0f, 0f,
                0f, 0.92f, 0f, 0f, 0f,
                0f, 0f, 0.83f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val GREEN = ReadingThemeSpec(
            id = THEME_GREEN,
            isDark = false,
            backdropArgb = 0xFFD6E8D6L,
            colorMatrixValues = floatArrayOf(
                0.89f, 0f, 0f, 0f, 0f,
                0f, 0.95f, 0f, 0f, 0f,
                0f, 0f, 0.89f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        val DARK = ReadingThemeSpec(
            id = THEME_DARK,
            isDark = true,
            backdropArgb = 0xFF121216L,
            colorMatrixValues = floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        fun fromId(id: String): ReadingThemeSpec = when (id.uppercase()) {
            THEME_SEPIA -> SEPIA
            THEME_GREEN -> GREEN
            THEME_DARK -> DARK
            else -> LIGHT
        }

        fun computeTargetRenderScale(
            settledZoom: Float,
            isNearCurrentPage: Boolean,
            baseW: Int,
            baseH: Int,
            maxPixels: Int = 6_000_000
        ): Float {
            if (!isNearCurrentPage || settledZoom <= 1.35f || baseW <= 0 || baseH <= 0 || maxPixels <= 0) {
                return 1f
            }
            val desired = settledZoom.coerceIn(1f, 2.5f)
            val budgetScale = sqrt(maxPixels.toDouble() / (baseW.toDouble() * baseH.toDouble())).toFloat()
            return minOf(desired, budgetScale).coerceAtLeast(1f)
        }
    }
}
