package br.com.jotdown.util

/**
 * Pure specification for extra annotation margin padding added to PDF pages.
 */
data class PagePaddingSpec(
    val mode: String,
    val extraRightFraction: Float,
    val extraBottomFraction: Float
) {
    val visualScale: Float
        get() = 1f / (1f + extraRightFraction)

    val hasPadding: Boolean
        get() = extraRightFraction > 0f || extraBottomFraction > 0f

    companion object {
        const val MODE_NONE = "NONE"
        const val MODE_RIGHT = "RIGHT"
        const val MODE_BOTTOM = "BOTTOM"
        const val MODE_BOTH = "BOTH"

        const val COLOR_WHITE = "#FFFFFF"
        const val COLOR_SEPIA = "#F4ECD8"
        const val COLOR_GRAY = "#F1F5F9"

        fun fromMode(mode: String): PagePaddingSpec = when (mode) {
            MODE_RIGHT -> PagePaddingSpec(MODE_RIGHT, 0.35f, 0f)
            MODE_BOTTOM -> PagePaddingSpec(MODE_BOTTOM, 0f, 0.25f)
            MODE_BOTH -> PagePaddingSpec(MODE_BOTH, 0.30f, 0.25f)
            else -> PagePaddingSpec(MODE_NONE, 0f, 0f)
        }

        fun parseHexRgb(
            hex: String,
            fallback: Triple<Int, Int, Int> = Triple(255, 255, 255)
        ): Triple<Int, Int, Int> {
            val cleaned = hex.trim().removePrefix("#")
            return try {
                when (cleaned.length) {
                    6 -> Triple(
                        cleaned.substring(0, 2).toInt(16),
                        cleaned.substring(2, 4).toInt(16),
                        cleaned.substring(4, 6).toInt(16)
                    )
                    8 -> Triple(
                        cleaned.substring(2, 4).toInt(16),
                        cleaned.substring(4, 6).toInt(16),
                        cleaned.substring(6, 8).toInt(16)
                    )
                    else -> fallback
                }
            } catch (_: NumberFormatException) {
                fallback
            }
        }
    }
}
