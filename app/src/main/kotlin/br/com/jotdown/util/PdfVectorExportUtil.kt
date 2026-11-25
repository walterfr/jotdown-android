package br.com.jotdown.util

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.data.entity.DrawingEntity
import br.com.jotdown.data.entity.HighlightEntity
import br.com.jotdown.ui.screens.reader.parseDrawingsJson
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Exports a vector PDF preserving the original PDF text/vectors, adding real PDF ink vectors,
 * TextMarkup annotations (highlight/underline/strikeout), sticky note annotations, and optional
 * expanded annotation margins.
 */
object PdfVectorExportUtil {

    data class PdfBox(val llx: Float, val lly: Float, val urx: Float, val ury: Float) {
        val width: Float get() = urx - llx
        val height: Float get() = ury - lly
    }

    data class VectorStroke(
        val tool: String,
        val colorArgb: Int,
        val widthMultiplier: Float,
        val points: List<Triple<Float, Float, Float>>
    )

    fun displayDimensions(cropBox: PdfBox, rotation: Int): Pair<Float, Float> {
        val rot = ((rotation % 360) + 360) % 360
        return if (rot == 90 || rot == 270) {
            cropBox.height to cropBox.width
        } else {
            cropBox.width to cropBox.height
        }
    }

    fun displayToPdfUserSpace(
        dx: Float,
        dy: Float,
        cropBox: PdfBox,
        rotation: Int
    ): Pair<Float, Float> {
        val rot = ((rotation % 360) + 360) % 360
        return when (rot) {
            90 -> (cropBox.llx + dy) to (cropBox.lly + dx)
            180 -> (cropBox.urx - dx) to (cropBox.lly + dy)
            270 -> (cropBox.urx - dy) to (cropBox.ury - dx)
            else -> (cropBox.llx + dx) to (cropBox.ury - dy)
        }
    }

    fun expandBoxForPadding(
        box: PdfBox,
        rotation: Int,
        extraRightPts: Float,
        extraBottomPts: Float
    ): PdfBox {
        val rot = ((rotation % 360) + 360) % 360
        return when (rot) {
            90 -> PdfBox(box.llx, box.lly, box.urx + extraBottomPts, box.ury + extraRightPts)
            180 -> PdfBox(box.llx - extraRightPts, box.lly, box.urx, box.ury + extraBottomPts)
            270 -> PdfBox(box.llx - extraBottomPts, box.lly - extraRightPts, box.urx, box.ury)
            else -> PdfBox(box.llx, box.lly - extraBottomPts, box.urx + extraRightPts, box.ury)
        }
    }

    fun normRectToQuadPoints(rect: NormRect, cropBox: PdfBox, rotation: Int): FloatArray {
        val (dispW, dispH) = displayDimensions(cropBox, rotation)
        val left = rect.left * dispW
        val right = rect.right * dispW
        val top = rect.top * dispH
        val bottom = rect.bottom * dispH

        val tl = displayToPdfUserSpace(left, top, cropBox, rotation)
        val tr = displayToPdfUserSpace(right, top, cropBox, rotation)
        val bl = displayToPdfUserSpace(left, bottom, cropBox, rotation)
        val br = displayToPdfUserSpace(right, bottom, cropBox, rotation)

        return floatArrayOf(
            tl.first, tl.second,
            tr.first, tr.second,
            bl.first, bl.second,
            br.first, br.second
        )
    }

    fun resolveVectorStrokes(strokes: List<VectorStroke>): List<VectorStroke> {
        val kept = mutableListOf<VectorStroke>()
        for (stroke in strokes) {
            when (stroke.tool) {
                "PEN", "PENCIL", "HIGHLIGHTER", "TAPE" -> {
                    if (stroke.points.isNotEmpty()) {
                        kept.add(stroke)
                    }
                }
                "ERASER" -> {
                    if (stroke.points.isEmpty()) continue
                    val eraserPts = stroke.points.map { GeomPoint(it.first, it.second) }
                    val eraserRadius = 15f * stroke.widthMultiplier
                    kept.removeAll { prev ->
                        val prevHalfWidth = when (prev.tool) {
                            "PEN" -> 1.5f
                            "PENCIL" -> 2f
                            "HIGHLIGHTER" -> 12.5f
                            "TAPE" -> 14f
                            else -> 1f
                        } * prev.widthMultiplier
                        val prevPts = prev.points.map { GeomPoint(it.first, it.second) }
                        StrokeGeometryUtil.doesEraserHitPath(
                            eraserPoints = eraserPts,
                            pathPoints = prevPts,
                            hitRadius = eraserRadius + prevHalfWidth
                        )
                    }
                }
                else -> Unit
            }
        }
        return kept
    }

    suspend fun exportVectorPdf(
        context: Context,
        pdfFile: File,
        drawings: List<DrawingEntity>,
        annotations: List<AnnotationEntity>,
        highlights: List<HighlightEntity>,
        title: String,
        pagePaddingMode: String = PagePaddingSpec.MODE_NONE,
        pagePaddingColor: String = PagePaddingSpec.COLOR_WHITE
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null

        val paddingSpec = PagePaddingSpec.fromMode(pagePaddingMode)
        val padRgb = PagePaddingSpec.parseHexRgb(pagePaddingColor)
        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val drawingsByPage = drawings.associateBy { it.page }
        val annotationsByPage = annotations.groupBy { it.page }.mapValues { (_, list) -> list.sortedBy { it.id } }
        val highlightsByPage = highlights.groupBy { it.page }.mapValues { (_, list) -> list.sortedBy { it.id } }

        var pdDoc: PDDocument? = null
        try {
            pdDoc = PDDocument.load(pdfFile)
            val pageCount = pdDoc.numberOfPages

            for (i in 0 until pageCount) {
                val pageNumber = i + 1
                val page = pdDoc.getPage(i)
                val origCropRect = page.cropBox ?: page.mediaBox
                val origBox = PdfBox(
                    llx = origCropRect.lowerLeftX,
                    lly = origCropRect.lowerLeftY,
                    urx = origCropRect.upperRightX,
                    ury = origCropRect.upperRightY
                )
                val rot = ((page.rotation % 360) + 360) % 360
                val (dispW, dispH) = displayDimensions(origBox, rot)
                val pointScale = dispW / screenWidth

                val extraRightPts = dispW * paddingSpec.extraRightFraction
                val extraBottomPts = dispH * paddingSpec.extraBottomFraction
                if (paddingSpec.hasPadding) {
                    val expandedCrop = expandBoxForPadding(origBox, rot, extraRightPts, extraBottomPts)
                    page.cropBox = PDRectangle(
                        expandedCrop.llx,
                        expandedCrop.lly,
                        expandedCrop.width,
                        expandedCrop.height
                    )
                    val origMedia = page.mediaBox
                    val mediaLlx = minOf(origMedia.lowerLeftX, expandedCrop.llx)
                    val mediaLly = minOf(origMedia.lowerLeftY, expandedCrop.lly)
                    val mediaUrx = maxOf(origMedia.upperRightX, expandedCrop.urx)
                    val mediaUry = maxOf(origMedia.upperRightY, expandedCrop.ury)
                    page.mediaBox = PDRectangle(
                        mediaLlx,
                        mediaLly,
                        mediaUrx - mediaLlx,
                        mediaUry - mediaLly
                    )
                }

                val rawDrawing = drawingsByPage[pageNumber]
                val resolvedStrokes = if (rawDrawing != null && rawDrawing.pathsJson.isNotBlank()) {
                    val parsed = parseDrawingsJson(rawDrawing.pathsJson).map { drawn ->
                        VectorStroke(
                            tool = drawn.tool.name,
                            colorArgb = drawn.color.toArgb(),
                            widthMultiplier = drawn.widthMultiplier,
                            points = drawn.points.map { Triple(it.x, it.y, it.pressure) }
                        )
                    }
                    resolveVectorStrokes(parsed)
                } else {
                    emptyList()
                }

                if (paddingSpec.hasPadding || resolvedStrokes.isNotEmpty()) {
                    PDPageContentStream(pdDoc, page, AppendMode.APPEND, true, true).use { cs ->
                        if (paddingSpec.hasPadding) {
                            drawMarginStrips(
                                cs = cs,
                                origBox = origBox,
                                rotation = rot,
                                dispW = dispW,
                                dispH = dispH,
                                extraRightPts = extraRightPts,
                                extraBottomPts = extraBottomPts,
                                rgb = padRgb
                            )
                        }
                        if (resolvedStrokes.isNotEmpty()) {
                            drawVectorStrokes(
                                cs = cs,
                                strokes = resolvedStrokes,
                                origBox = origBox,
                                rotation = rot,
                                pointScale = pointScale
                            )
                        }
                    }
                }

                val pageAnnotations = page.annotations

                highlightsByPage[pageNumber].orEmpty().forEach { h ->
                    val rects = PdfTextLayerUtil.parseRects(h.rectsJson)
                    if (rects.isEmpty()) return@forEach

                    val subtype = when (h.style) {
                        "UNDERLINE" -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
                        "STRIKEOUT" -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
                        else -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                    }
                    val quadPoints = FloatArray(rects.size * 8)
                    var qIdx = 0
                    var minX = Float.POSITIVE_INFINITY
                    var minY = Float.POSITIVE_INFINITY
                    var maxX = Float.NEGATIVE_INFINITY
                    var maxY = Float.NEGATIVE_INFINITY
                    for (r in rects) {
                        val qp = normRectToQuadPoints(r, origBox, rot)
                        for (k in qp.indices step 2) {
                            val px = qp[k]
                            val py = qp[k + 1]
                            quadPoints[qIdx++] = px
                            quadPoints[qIdx++] = py
                            if (px < minX) minX = px
                            if (px > maxX) maxX = px
                            if (py < minY) minY = py
                            if (py > maxY) maxY = py
                        }
                    }

                    val rgb = PagePaddingSpec.parseHexRgb(h.color, Triple(253, 224, 71))
                    val markup = PDAnnotationTextMarkup(subtype).apply {
                        rectangle = PDRectangle(
                            minX,
                            minY,
                            (maxX - minX).coerceAtLeast(1f),
                            (maxY - minY).coerceAtLeast(1f)
                        )
                        setQuadPoints(quadPoints)
                        color = PDColor(
                            floatArrayOf(rgb.first / 255f, rgb.second / 255f, rgb.third / 255f),
                            PDDeviceRGB.INSTANCE
                        )
                        if (h.style == "HIGHLIGHT") {
                            constantOpacity = 0.35f
                        }
                        contents = if (h.note.isNotBlank()) "${h.text}\n\n${h.note}" else h.text
                        titlePopup = "Jotdown"
                        isPrinted = true
                    }
                    pageAnnotations.add(markup)
                }

                annotationsByPage[pageNumber].orEmpty().forEach { ann ->
                    val (px, py) = displayToPdfUserSpace(ann.x * pointScale, ann.y * pointScale, origBox, rot)
                    val noteRgb = PagePaddingSpec.parseHexRgb(ann.color, Triple(245, 158, 11))
                    val textAnnot = PDAnnotationText().apply {
                        rectangle = PDRectangle(px - 10f, py - 10f, 20f, 20f)
                        name = PDAnnotationText.NAME_NOTE
                        color = PDColor(
                            floatArrayOf(noteRgb.first / 255f, noteRgb.second / 255f, noteRgb.third / 255f),
                            PDDeviceRGB.INSTANCE
                        )
                        contents = ann.text
                        titlePopup = "Jotdown"
                        isPrinted = true
                        open = false
                    }
                    pageAnnotations.add(textAnnot)
                }
            }

            val cleanTitle = (if (title.isBlank()) "documento" else title.replace(" ", "_"))
                .replace(Regex("[^A-Za-z0-9_]"), "")
                .ifBlank { "documento" }
            val outFile = File(context.cacheDir, "${cleanTitle}_vetorial.pdf")
            pdDoc.save(outFile)
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                pdDoc?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun drawMarginStrips(
        cs: PDPageContentStream,
        origBox: PdfBox,
        rotation: Int,
        dispW: Float,
        dispH: Float,
        extraRightPts: Float,
        extraBottomPts: Float,
        rgb: Triple<Int, Int, Int>
    ) {
        cs.saveGraphicsState()
        cs.setNonStrokingColor(rgb.first / 255f, rgb.second / 255f, rgb.third / 255f)
        if (extraRightPts > 0f) {
            fillDisplayQuad(
                cs = cs,
                origBox = origBox,
                rotation = rotation,
                x0 = dispW,
                y0 = 0f,
                x1 = dispW + extraRightPts,
                y1 = dispH + extraBottomPts
            )
        }
        if (extraBottomPts > 0f) {
            fillDisplayQuad(
                cs = cs,
                origBox = origBox,
                rotation = rotation,
                x0 = 0f,
                y0 = dispH,
                x1 = dispW,
                y1 = dispH + extraBottomPts
            )
        }
        cs.restoreGraphicsState()
    }

    private fun fillDisplayQuad(
        cs: PDPageContentStream,
        origBox: PdfBox,
        rotation: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float
    ) {
        val p0 = displayToPdfUserSpace(x0, y0, origBox, rotation)
        val p1 = displayToPdfUserSpace(x1, y0, origBox, rotation)
        val p2 = displayToPdfUserSpace(x1, y1, origBox, rotation)
        val p3 = displayToPdfUserSpace(x0, y1, origBox, rotation)
        cs.moveTo(p0.first, p0.second)
        cs.lineTo(p1.first, p1.second)
        cs.lineTo(p2.first, p2.second)
        cs.lineTo(p3.first, p3.second)
        cs.closePath()
        cs.fill()
    }

    private fun drawVectorStrokes(
        cs: PDPageContentStream,
        strokes: List<VectorStroke>,
        origBox: PdfBox,
        rotation: Int,
        pointScale: Float
    ) {
        cs.saveGraphicsState()
        cs.setLineCapStyle(1)
        cs.setLineJoinStyle(1)

        val highlighterGs = PDExtendedGraphicsState().apply {
            strokingAlphaConstant = 0.35f
            blendMode = BlendMode.MULTIPLY
        }
        val penGs = PDExtendedGraphicsState().apply {
            strokingAlphaConstant = 0.9f
            blendMode = BlendMode.NORMAL
        }
        val pencilGs = PDExtendedGraphicsState().apply {
            strokingAlphaConstant = 0.85f
            blendMode = BlendMode.NORMAL
        }
        val tapeGs = PDExtendedGraphicsState().apply {
            strokingAlphaConstant = 1.0f
            blendMode = BlendMode.NORMAL
        }

        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val r = ((stroke.colorArgb shr 16) and 0xFF) / 255f
            val g = ((stroke.colorArgb shr 8) and 0xFF) / 255f
            val b = (stroke.colorArgb and 0xFF) / 255f
            cs.setStrokingColor(r, g, b)

            when (stroke.tool) {
                "HIGHLIGHTER" -> {
                    cs.setLineCapStyle(1)
                    cs.setGraphicsStateParameters(highlighterGs)
                    cs.setLineWidth(25f * stroke.widthMultiplier * pointScale)
                    emitQuadraticCurvePath(cs, stroke.points, origBox, rotation, pointScale)
                }
                "PENCIL" -> {
                    cs.setLineCapStyle(1)
                    cs.setGraphicsStateParameters(pencilGs)
                    cs.setLineWidth(4f * stroke.widthMultiplier * pointScale)
                    emitQuadraticCurvePath(cs, stroke.points, origBox, rotation, pointScale)
                }
                "TAPE" -> {
                    cs.setLineCapStyle(2)
                    cs.setGraphicsStateParameters(tapeGs)
                    cs.setLineWidth(28f * stroke.widthMultiplier * pointScale)
                    emitQuadraticCurvePath(cs, stroke.points, origBox, rotation, pointScale)
                }
                "PEN" -> {
                    cs.setLineCapStyle(1)
                    cs.setGraphicsStateParameters(penGs)
                    if (stroke.points.size == 1) {
                        val pt = stroke.points[0]
                        val pFact = (0.5f + 1.5f * pt.third).coerceIn(0.5f, 2.5f)
                        cs.setLineWidth(3f * stroke.widthMultiplier * pFact * pointScale)
                        val mapped = displayToPdfUserSpace(pt.first * pointScale, pt.second * pointScale, origBox, rotation)
                        cs.moveTo(mapped.first, mapped.second)
                        cs.lineTo(mapped.first, mapped.second)
                        cs.stroke()
                    } else {
                        val segments = StrokeSmoothingUtil.buildQuadraticSegments(stroke.points)
                        for (seg in segments) {
                            val avgP = (seg.startPressure + seg.endPressure) * 0.5f
                            val pFact = (0.5f + 1.5f * avgP).coerceIn(0.5f, 2.5f)
                            cs.setLineWidth(3f * stroke.widthMultiplier * pFact * pointScale)
                            val (c1, c2) = StrokeSmoothingUtil.quadToCubicControlPoints(
                                seg.start,
                                seg.control,
                                seg.end
                            )
                            val mStart = displayToPdfUserSpace(seg.start.x * pointScale, seg.start.y * pointScale, origBox, rotation)
                            val mc1 = displayToPdfUserSpace(c1.x * pointScale, c1.y * pointScale, origBox, rotation)
                            val mc2 = displayToPdfUserSpace(c2.x * pointScale, c2.y * pointScale, origBox, rotation)
                            val mEnd = displayToPdfUserSpace(seg.end.x * pointScale, seg.end.y * pointScale, origBox, rotation)
                            cs.moveTo(mStart.first, mStart.second)
                            cs.curveTo(mc1.first, mc1.second, mc2.first, mc2.second, mEnd.first, mEnd.second)
                            cs.stroke()
                        }
                    }
                }
            }
        }

        cs.restoreGraphicsState()
    }

    private fun emitQuadraticCurvePath(
        cs: PDPageContentStream,
        points: List<Triple<Float, Float, Float>>,
        origBox: PdfBox,
        rotation: Int,
        pointScale: Float
    ) {
        if (points.size == 1) {
            val first = displayToPdfUserSpace(
                points[0].first * pointScale,
                points[0].second * pointScale,
                origBox,
                rotation
            )
            cs.moveTo(first.first, first.second)
            cs.lineTo(first.first, first.second)
            cs.stroke()
            return
        }
        val segments = StrokeSmoothingUtil.buildQuadraticSegments(points)
        if (segments.isEmpty()) return
        val first = displayToPdfUserSpace(
            segments[0].start.x * pointScale,
            segments[0].start.y * pointScale,
            origBox,
            rotation
        )
        cs.moveTo(first.first, first.second)
        for (seg in segments) {
            val (c1, c2) = StrokeSmoothingUtil.quadToCubicControlPoints(seg.start, seg.control, seg.end)
            val mc1 = displayToPdfUserSpace(c1.x * pointScale, c1.y * pointScale, origBox, rotation)
            val mc2 = displayToPdfUserSpace(c2.x * pointScale, c2.y * pointScale, origBox, rotation)
            val mEnd = displayToPdfUserSpace(seg.end.x * pointScale, seg.end.y * pointScale, origBox, rotation)
            cs.curveTo(mc1.first, mc1.second, mc2.first, mc2.second, mEnd.first, mEnd.second)
        }
        cs.stroke()
    }
}
