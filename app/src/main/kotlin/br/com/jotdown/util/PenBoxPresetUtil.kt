package br.com.jotdown.util

import java.util.Locale
import kotlin.math.abs

/**
 * Pure Kotlin PenBox preset manager (no Android/org.json dependencies so unit tests run on JVM).
 */
object PenBoxPresetUtil {

    const val MAX_PRESETS = 8
    val ALLOWED_TOOLS = setOf("PEN", "PENCIL", "HIGHLIGHTER")

    data class PenPreset(
        val id: String,
        val toolName: String,
        val colorArgb: Int,
        val widthMultiplier: Float
    )

    fun defaultPresets(): List<PenPreset> = listOf(
        PenPreset(id = "p1", toolName = "PEN", colorArgb = 0xFF000000.toInt(), widthMultiplier = 0.7f),
        PenPreset(id = "p2", toolName = "PEN", colorArgb = 0xFF2563EB.toInt(), widthMultiplier = 1.0f),
        PenPreset(id = "p3", toolName = "PEN", colorArgb = 0xFFEF4444.toInt(), widthMultiplier = 1.0f),
        PenPreset(id = "p4", toolName = "HIGHLIGHTER", colorArgb = 0xFFFDE047.toInt(), widthMultiplier = 1.2f)
    )

    fun addPreset(
        existing: List<PenPreset>,
        toolName: String,
        colorArgb: Int,
        widthMultiplier: Float,
        idGenerator: () -> String = { "p_${System.nanoTime()}" }
    ): List<PenPreset> {
        if (toolName !in ALLOWED_TOOLS) return existing
        val clampedWidth = widthMultiplier.coerceIn(0.2f, 4.0f)
        val duplicate = existing.any {
            it.toolName == toolName &&
                it.colorArgb == colorArgb &&
                abs(it.widthMultiplier - clampedWidth) < 0.05f
        }
        if (duplicate) return existing

        val trimmed = if (existing.size >= MAX_PRESETS) {
            existing.drop(existing.size - MAX_PRESETS + 1)
        } else {
            existing
        }
        return trimmed + PenPreset(
            id = idGenerator(),
            toolName = toolName,
            colorArgb = colorArgb,
            widthMultiplier = clampedWidth
        )
    }

    fun removePreset(existing: List<PenPreset>, id: String): List<PenPreset> =
        existing.filterNot { it.id == id }

    fun serializePresets(presets: List<PenPreset>): String {
        val sb = StringBuilder("[")
        presets.forEachIndexed { index, preset ->
            if (index > 0) sb.append(',')
            sb.append('{')
            sb.append("\"id\":\"").append(escapeJson(preset.id)).append("\",")
            sb.append("\"tool\":\"").append(escapeJson(preset.toolName)).append("\",")
            sb.append("\"color\":").append(preset.colorArgb).append(',')
            sb.append("\"wm\":").append(String.format(Locale.US, "%.2f", preset.widthMultiplier))
            sb.append('}')
        }
        sb.append(']')
        return sb.toString()
    }

    fun parsePresets(json: String?): List<PenPreset> {
        if (json.isNullOrBlank()) return defaultPresets()
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return defaultPresets()

        val objectRegex = Regex("\\{([^}]*)\\}")
        val idRegex = Regex("\"id\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val toolRegex = Regex("\"tool\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val colorRegex = Regex("\"color\"\\s*:\\s*(-?\\d+)")
        val wmRegex = Regex("\"wm\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")

        val parsed = mutableListOf<PenPreset>()
        for (match in objectRegex.findAll(trimmed)) {
            val body = match.groupValues[1]
            val id = idRegex.find(body)?.groupValues?.get(1)?.let(::unescapeJson) ?: continue
            val tool = toolRegex.find(body)?.groupValues?.get(1)?.let(::unescapeJson) ?: continue
            val color = colorRegex.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val wm = wmRegex.find(body)?.groupValues?.get(1)?.toFloatOrNull() ?: continue
            if (id.isBlank() || tool !in ALLOWED_TOOLS) continue
            parsed.add(
                PenPreset(
                    id = id,
                    toolName = tool,
                    colorArgb = color,
                    widthMultiplier = wm.coerceIn(0.2f, 4.0f)
                )
            )
            if (parsed.size >= MAX_PRESETS) break
        }
        return if (parsed.isEmpty()) defaultPresets() else parsed
    }

    private fun escapeJson(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescapeJson(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(next)
                }
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }
}
