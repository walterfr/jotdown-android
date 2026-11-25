package br.com.jotdown.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class PaperTemplate { BLANK, LINED, GRID, DOTTED, CORNELL }

data class TemplateLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val colorRgb: Triple<Int, Int, Int>,
    val width: Float
)

data class TemplateDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    val colorRgb: Triple<Int, Int, Int>
)

data class TemplateGeometry(
    val lines: List<TemplateLine> = emptyList(),
    val dots: List<TemplateDot> = emptyList()
)

/**
 * Pure paper template geometry generator and PDFBox-Android atomic page inserter.
 */
object PdfPageTemplateUtil {

    private const val DEFAULT_A4_WIDTH = 595f
    private const val DEFAULT_A4_HEIGHT = 842f

    fun computeTemplatePageSize(
        refWidth: Float,
        refHeight: Float,
        refRotation: Int
    ): Pair<Float, Float> {
        if (refWidth <= 0f || refHeight <= 0f) {
            return DEFAULT_A4_WIDTH to DEFAULT_A4_HEIGHT
        }
        val rot = ((refRotation % 360) + 360) % 360
        return if (rot == 90 || rot == 270) {
            refHeight to refWidth
        } else {
            refWidth to refHeight
        }
    }

    fun buildTemplateGeometry(
        template: PaperTemplate,
        width: Float,
        height: Float
    ): TemplateGeometry {
        if (width <= 0f || height <= 0f || template == PaperTemplate.BLANK) {
            return TemplateGeometry()
        }

        val lines = mutableListOf<TemplateLine>()
        val dots = mutableListOf<TemplateDot>()

        when (template) {
            PaperTemplate.BLANK -> Unit

            PaperTemplate.LINED -> {
                val ruleColor = Triple(209, 213, 219)
                val marginColor = Triple(252, 165, 165)
                val topMargin = minOf(54f, height * 0.1f)
                val bottomMargin = minOf(48f, height * 0.08f)
                val step = 24f
                var y = height - topMargin
                while (y >= bottomMargin) {
                    lines.add(TemplateLine(0f, y, width, y, ruleColor, 0.6f))
                    y -= step
                }
                val marginX = minOf(72f, width * 0.15f)
                if (marginX < width) {
                    lines.add(TemplateLine(marginX, 0f, marginX, height, marginColor, 1.2f))
                }
            }

            PaperTemplate.GRID -> {
                val gridColor = Triple(220, 224, 230)
                val step = 20f
                var x = step
                while (x < width) {
                    lines.add(TemplateLine(x, 0f, x, height, gridColor, 0.5f))
                    x += step
                }
                var y = step
                while (y < height) {
                    lines.add(TemplateLine(0f, y, width, y, gridColor, 0.5f))
                    y += step
                }
            }

            PaperTemplate.DOTTED -> {
                val dotColor = Triple(180, 188, 198)
                val step = 20f
                var y = step
                while (y < height - 4f) {
                    var x = step
                    while (x < width - 4f) {
                        dots.add(TemplateDot(x, y, 0.9f, dotColor))
                        x += step
                    }
                    y += step
                }
            }

            PaperTemplate.CORNELL -> {
                val borderColor = Triple(148, 163, 184)
                val ruleColor = Triple(209, 213, 219)
                val sidePad = minOf(36f, width * 0.08f)
                val rightX = (width - sidePad).coerceAtLeast(sidePad + 1f)
                val headerY = (height - minOf(72f, height * 0.12f)).coerceAtLeast(height * 0.55f)
                val summaryY = minOf(120f, height * 0.18f).coerceAtMost(headerY - 24f)
                val cueX = maxOf(sidePad + 40f, minOf(width * 0.30f, rightX - 40f))

                // Header horizontal divider
                lines.add(TemplateLine(sidePad, headerY, rightX, headerY, borderColor, 1.1f))
                // Summary horizontal divider
                lines.add(TemplateLine(sidePad, summaryY, rightX, summaryY, borderColor, 1.1f))
                // Cue column vertical divider
                lines.add(TemplateLine(cueX, summaryY, cueX, headerY, borderColor, 1.1f))

                // Notes area ruled lines
                val step = 24f
                var y = headerY - step
                while (y > summaryY + 8f) {
                    lines.add(TemplateLine(cueX + 8f, y, rightX, y, ruleColor, 0.55f))
                    y -= step
                }

                // Summary area ruled lines
                var sy = summaryY - step
                while (sy >= sidePad) {
                    lines.add(TemplateLine(sidePad, sy, rightX, sy, ruleColor, 0.55f))
                    sy -= step
                }
            }
        }

        return TemplateGeometry(lines = lines, dots = dots)
    }

    suspend fun insertTemplatePage(
        pdfFile: File,
        afterPage: Int,
        template: PaperTemplate
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || !pdfFile.canRead()) return@withContext false
        val parent = pdfFile.parentFile ?: pdfFile.absoluteFile.parentFile
        val tmpFile = File(parent, "${pdfFile.name}.${System.nanoTime()}.tmp")
        var pdDoc: PDDocument? = null
        try {
            pdDoc = PDDocument.load(pdfFile)
            if (pdDoc.isEncrypted) return@withContext false

            val totalPages = pdDoc.numberOfPages
            val clampedAfter = afterPage.coerceIn(0, totalPages)
            val refIndex = when {
                totalPages <= 0 -> -1
                clampedAfter in 1..totalPages -> clampedAfter - 1
                else -> 0
            }
            val (pageW, pageH) = if (refIndex >= 0) {
                val refPage = pdDoc.getPage(refIndex)
                val box = refPage.cropBox ?: refPage.mediaBox
                computeTemplatePageSize(
                    refWidth = box?.width ?: DEFAULT_A4_WIDTH,
                    refHeight = box?.height ?: DEFAULT_A4_HEIGHT,
                    refRotation = refPage.rotation
                )
            } else {
                DEFAULT_A4_WIDTH to DEFAULT_A4_HEIGHT
            }

            val newPage = PDPage(PDRectangle(pageW, pageH))
            val pages = pdDoc.pages
            when {
                totalPages <= 0 || clampedAfter >= totalPages -> pages.add(newPage)
                clampedAfter == 0 -> pages.insertBefore(newPage, pages.get(0))
                else -> pages.insertAfter(newPage, pages.get(clampedAfter - 1))
            }

            val geometry = buildTemplateGeometry(template, pageW, pageH)
            if (geometry.lines.isNotEmpty() || geometry.dots.isNotEmpty()) {
                PDPageContentStream(pdDoc, newPage).use { cs ->
                    cs.setLineCapStyle(1)
                    for (line in geometry.lines) {
                        cs.setStrokingColor(
                            line.colorRgb.first / 255f,
                            line.colorRgb.second / 255f,
                            line.colorRgb.third / 255f
                        )
                        cs.setLineWidth(line.width)
                        cs.moveTo(line.x1, line.y1)
                        cs.lineTo(line.x2, line.y2)
                        cs.stroke()
                    }
                    for (dot in geometry.dots) {
                        cs.setNonStrokingColor(
                            dot.colorRgb.first / 255f,
                            dot.colorRgb.second / 255f,
                            dot.colorRgb.third / 255f
                        )
                        drawCircle(cs, dot.x, dot.y, dot.radius)
                        cs.fill()
                    }
                }
            }

            pdDoc.save(tmpFile)
            pdDoc.close()
            pdDoc = null

            if (!tmpFile.exists() || tmpFile.length() == 0L) {
                tmpFile.delete()
                return@withContext false
            }

            if (!tmpFile.renameTo(pdfFile)) {
                tmpFile.inputStream().use { input ->
                    pdfFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tmpFile.delete()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try {
                pdDoc?.close()
            } catch (_: Exception) {
            }
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
        }
    }

    private fun drawCircle(cs: PDPageContentStream, cx: Float, cy: Float, r: Float) {
        val k = 0.55228475f * r
        cs.moveTo(cx + r, cy)
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        cs.closePath()
    }
}
