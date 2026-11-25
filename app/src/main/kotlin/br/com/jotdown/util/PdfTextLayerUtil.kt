package br.com.jotdown.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class PdfWordBox(val text: String, val rect: NormRect)
data class TextSelectionExtraction(val text: String, val lineRects: List<NormRect>)

/**
 * 100% FOSS PDF text-layer extraction, word-box caching, search match highlighting,
 * and normalized rectangle serialization using PDFBox-Android and pure Kotlin.
 */
object PdfTextLayerUtil {

    private const val MAX_CACHE_PAGES = 24
    private val cacheLock = Any()
    private val wordCache = object : LinkedHashMap<String, List<PdfWordBox>>(MAX_CACHE_PAGES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PdfWordBox>>?): Boolean {
            return size > MAX_CACHE_PAGES
        }
    }

    private val rectBlockRegex = Regex("\\{([^}]*)\\}")
    private val rectFieldRegex = Regex("\"([ltrb])\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)")

    internal fun cropLocalToDisplay(
        ux: Float,
        uy: Float,
        cropW: Float,
        cropH: Float,
        rotation: Int
    ): Pair<Float, Float> {
        val rot = ((rotation % 360) + 360) % 360
        return when (rot) {
            90 -> uy to ux
            180 -> (cropW - ux) to uy
            270 -> (cropH - uy) to (cropW - ux)
            else -> ux to (cropH - uy)
        }
    }

    internal fun glyphQuadToNormRect(
        ux0: Float,
        uy0: Float,
        endX: Float,
        endY: Float,
        scaleX: Float,
        shearY: Float,
        shearX: Float,
        scaleY: Float,
        widthDirAdj: Float,
        heightDir: Float,
        fontSizeInPt: Float,
        cropW: Float,
        cropH: Float,
        rotation: Int
    ): NormRect? {
        val safeCropW = cropW.coerceAtLeast(1f)
        val safeCropH = cropH.coerceAtLeast(1f)
        val rot = ((rotation % 360) + 360) % 360
        val dispW = if (rot == 90 || rot == 270) safeCropH else safeCropW
        val dispH = if (rot == 90 || rot == 270) safeCropW else safeCropH

        var ux1 = endX
        var uy1 = endY
        val baseLen = hypot((ux1 - ux0).toDouble(), (uy1 - uy0).toDouble()).toFloat()
        if (baseLen < 1e-3f) {
            val bvLen = hypot(scaleX.toDouble(), shearY.toDouble()).toFloat()
            if (bvLen > 1e-6f) {
                ux1 = ux0 + (scaleX / bvLen) * widthDirAdj
                uy1 = uy0 + (shearY / bvLen) * widthDirAdj
            }
        }

        val rawUpLen = hypot(shearX.toDouble(), scaleY.toDouble()).toFloat()
        val (upX, upY) = if (rawUpLen >= 1e-4f) {
            (shearX / rawUpLen) to (scaleY / rawUpLen)
        } else {
            val dx = ux1 - ux0
            val dy = uy1 - uy0
            val dLen = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dLen >= 1e-6f) (-dy / dLen) to (dx / dLen) else 0f to 1f
        }

        val h = heightDir.coerceAtLeast(fontSizeInPt * 0.7f).coerceAtLeast(1f)
        val asc = h * 1.05f
        val desc = h * 0.2f

        val c0 = cropLocalToDisplay(ux0 - desc * upX, uy0 - desc * upY, safeCropW, safeCropH, rot)
        val c1 = cropLocalToDisplay(ux1 - desc * upX, uy1 - desc * upY, safeCropW, safeCropH, rot)
        val c2 = cropLocalToDisplay(ux0 + asc * upX, uy0 + asc * upY, safeCropW, safeCropH, rot)
        val c3 = cropLocalToDisplay(ux1 + asc * upX, uy1 + asc * upY, safeCropW, safeCropH, rot)

        val nl = (minOf(c0.first, c1.first, c2.first, c3.first) / dispW).coerceIn(0f, 1f)
        val nr = (maxOf(c0.first, c1.first, c2.first, c3.first) / dispW).coerceIn(0f, 1f)
        val nt = (minOf(c0.second, c1.second, c2.second, c3.second) / dispH).coerceIn(0f, 1f)
        val nb = (maxOf(c0.second, c1.second, c2.second, c3.second) / dispH).coerceIn(0f, 1f)

        return if (nr <= nl || nb <= nt) null else NormRect(nl, nt, nr, nb)
    }

    fun invalidateFile(pdfFile: File) {
        val prefix = "${pdfFile.absolutePath}:"
        synchronized(cacheLock) {
            val iterator = wordCache.keys.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().startsWith(prefix)) {
                    iterator.remove()
                }
            }
        }
    }

    suspend fun getWordBoxes(pdfFile: File, pageNumber: Int): List<PdfWordBox> = withContext(Dispatchers.IO) {
        if (!pdfFile.exists() || pageNumber < 1) return@withContext emptyList()
        val key = "${pdfFile.absolutePath}:${pdfFile.lastModified()}:$pageNumber"
        synchronized(cacheLock) {
            wordCache[key]?.let { return@withContext it }
        }

        val extracted = mutableListOf<PdfWordBox>()
        var pdDoc: PDDocument? = null
        try {
            pdDoc = PDDocument.load(pdfFile)
            if (pageNumber !in 1..pdDoc.numberOfPages) {
                return@withContext emptyList()
            }
            val page = pdDoc.getPage(pageNumber - 1)
            val cropBox = page.cropBox ?: page.mediaBox
            val cropW = cropBox.width.coerceAtLeast(1f)
            val cropH = cropBox.height.coerceAtLeast(1f)
            val rot = ((page.rotation % 360) + 360) % 360

            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                    var wordBuilder = StringBuilder()
                    var minL = Float.POSITIVE_INFINITY
                    var minT = Float.POSITIVE_INFINITY
                    var maxR = Float.NEGATIVE_INFINITY
                    var maxB = Float.NEGATIVE_INFINITY
                    var hasBox = false

                    fun flushWord() {
                        if (wordBuilder.isNotEmpty() && hasBox && maxR > minL && maxB > minT) {
                            extracted.add(
                                PdfWordBox(
                                    text = wordBuilder.toString(),
                                    rect = NormRect(minL, minT, maxR, maxB)
                                )
                            )
                        }
                        wordBuilder = StringBuilder()
                        minL = Float.POSITIVE_INFINITY
                        minT = Float.POSITIVE_INFINITY
                        maxR = Float.NEGATIVE_INFINITY
                        maxB = Float.NEGATIVE_INFINITY
                        hasBox = false
                    }

                    for (tp in textPositions) {
                        val unicode = tp.unicode ?: ""
                        if (unicode.isEmpty() || unicode.all { it.isWhitespace() || it == '\u00A0' }) {
                            flushWord()
                            continue
                        }

                        // LegacyPDFStreamEngine already translates textMatrix/endX/endY to unrotated cropBox-local space.
                        val tm = tp.textMatrix
                        val rect = glyphQuadToNormRect(
                            ux0 = tm.translateX,
                            uy0 = tm.translateY,
                            endX = tp.endX,
                            endY = tp.endY,
                            scaleX = tm.scaleX,
                            shearY = tm.shearY,
                            shearX = tm.shearX,
                            scaleY = tm.scaleY,
                            widthDirAdj = tp.widthDirAdj,
                            heightDir = tp.heightDir,
                            fontSizeInPt = tp.fontSizeInPt,
                            cropW = cropW,
                            cropH = cropH,
                            rotation = rot
                        ) ?: continue

                        wordBuilder.append(unicode)
                        minL = minOf(minL, rect.left)
                        minT = minOf(minT, rect.top)
                        maxR = maxOf(maxR, rect.right)
                        maxB = maxOf(maxB, rect.bottom)
                        hasBox = true
                    }
                    flushWord()
                }
            }.apply {
                sortByPosition = true
                startPage = pageNumber
                endPage = pageNumber
            }

            stripper.getText(pdDoc)
        } catch (_: Exception) {
            return@withContext emptyList()
        } finally {
            try {
                pdDoc?.close()
            } catch (_: Exception) {
            }
        }

        val immutable = extracted.toList()
        synchronized(cacheLock) {
            wordCache[key] = immutable
        }
        immutable
    }

    suspend fun extractInRect(
        pdfFile: File,
        pageNumber: Int,
        selection: NormRect
    ): TextSelectionExtraction = extractInRect(getWordBoxes(pdfFile, pageNumber), selection)

    suspend fun findMatchRectsOnPage(
        pdfFile: File,
        pageNumber: Int,
        query: String
    ): List<NormRect> {
        if (query.isBlank()) return emptyList()
        return findMatchRects(getWordBoxes(pdfFile, pageNumber), query)
    }

    fun extractInRect(words: List<PdfWordBox>, selection: NormRect): TextSelectionExtraction {
        val sl = minOf(selection.left, selection.right).coerceIn(0f, 1f)
        val st = minOf(selection.top, selection.bottom).coerceIn(0f, 1f)
        val sr = maxOf(selection.left, selection.right).coerceIn(0f, 1f)
        val sb = maxOf(selection.top, selection.bottom).coerceIn(0f, 1f)
        if (sr <= sl || sb <= st || words.isEmpty()) {
            return TextSelectionExtraction("", emptyList())
        }

        val matched = words.filter { word ->
            val r = word.rect
            val wArea = (r.right - r.left).coerceAtLeast(0f) * (r.bottom - r.top).coerceAtLeast(0f)
            if (wArea <= 0f) return@filter false
            val cx = (r.left + r.right) * 0.5f
            val cy = (r.top + r.bottom) * 0.5f
            if (cx in sl..sr && cy in st..sb) return@filter true

            val ix0 = maxOf(sl, r.left)
            val iy0 = maxOf(st, r.top)
            val ix1 = minOf(sr, r.right)
            val iy1 = minOf(sb, r.bottom)
            val overlap = (ix1 - ix0).coerceAtLeast(0f) * (iy1 - iy0).coerceAtLeast(0f)
            overlap >= 0.30f * wArea
        }

        if (matched.isEmpty()) {
            return TextSelectionExtraction("", emptyList())
        }

        val sortedByY = matched.sortedBy { (it.rect.top + it.rect.bottom) * 0.5f }
        val lines = mutableListOf<MutableList<PdfWordBox>>()
        for (word in sortedByY) {
            val wCy = (word.rect.top + word.rect.bottom) * 0.5f
            val wH = word.rect.bottom - word.rect.top
            val targetLine = lines.lastOrNull()?.takeIf { line ->
                val lineCy = line.map { (it.rect.top + it.rect.bottom) * 0.5f }.average().toFloat()
                val lineMaxH = line.maxOf { it.rect.bottom - it.rect.top }
                abs(wCy - lineCy) <= maxOf(wH, lineMaxH) * 0.6f
            }
            if (targetLine != null) {
                targetLine.add(word)
            } else {
                lines.add(mutableListOf(word))
            }
        }

        lines.forEach { line -> line.sortBy { it.rect.left } }
        lines.sortBy { line -> line.map { (it.rect.top + it.rect.bottom) * 0.5f }.average() }

        val joinedText = lines.flatten().joinToString(" ") { it.text }
        val lineRects = mergeLineRects(matched.map { it.rect })
        return TextSelectionExtraction(joinedText, lineRects)
    }

    fun mergeLineRects(rects: List<NormRect>): List<NormRect> {
        val valid = rects.filter { it.right > it.left && it.bottom > it.top }
        if (valid.isEmpty()) return emptyList()

        val sortedByY = valid.sortedBy { (it.top + it.bottom) * 0.5f }
        val lines = mutableListOf<MutableList<NormRect>>()
        for (r in sortedByY) {
            val rCy = (r.top + r.bottom) * 0.5f
            val rH = r.bottom - r.top
            val targetLine = lines.lastOrNull()?.takeIf { line ->
                val lineCy = line.map { (it.top + it.bottom) * 0.5f }.average().toFloat()
                val lineMaxH = line.maxOf { it.bottom - it.top }
                abs(rCy - lineCy) <= maxOf(rH, lineMaxH) * 0.6f
            }
            if (targetLine != null) {
                targetLine.add(r)
            } else {
                lines.add(mutableListOf(r))
            }
        }

        val merged = mutableListOf<NormRect>()
        for (line in lines) {
            line.sortBy { it.left }
            var curr = line[0]
            for (i in 1 until line.size) {
                val next = line[i]
                val maxH = maxOf(curr.bottom - curr.top, next.bottom - next.top)
                if (next.left - curr.right <= maxH * 2.5f) {
                    curr = NormRect(
                        left = minOf(curr.left, next.left),
                        top = minOf(curr.top, next.top),
                        right = maxOf(curr.right, next.right),
                        bottom = maxOf(curr.bottom, next.bottom)
                    )
                } else {
                    merged.add(curr)
                    curr = next
                }
            }
            merged.add(curr)
        }

        return merged.sortedWith(compareBy({ it.top }, { it.left }))
    }

    fun findMatchRects(words: List<PdfWordBox>, query: String): List<NormRect> {
        val q = query.trim()
        if (q.isEmpty() || words.isEmpty()) return emptyList()

        data class WordSpan(val start: Int, val end: Int, val rect: NormRect)

        val sb = StringBuilder()
        val spans = ArrayList<WordSpan>(words.size)
        for (w in words) {
            if (w.text.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append(' ')
            val start = sb.length
            sb.append(w.text)
            val end = sb.length
            spans.add(WordSpan(start, end, w.rect))
        }

        val fullText = sb.toString()
        if (fullText.length < q.length) return emptyList()

        val hitRects = mutableListOf<NormRect>()
        var searchFrom = 0
        while (searchFrom <= fullText.length - q.length) {
            val foundIdx = fullText.indexOf(q, searchFrom, ignoreCase = true)
            if (foundIdx == -1) break
            val matchEnd = foundIdx + q.length
            for (span in spans) {
                if (span.start < matchEnd && span.end > foundIdx) {
                    hitRects.add(span.rect)
                }
            }
            searchFrom = foundIdx + q.length.coerceAtLeast(1)
        }
        return mergeLineRects(hitRects)
    }

    fun serializeRects(rects: List<NormRect>): String {
        if (rects.isEmpty()) return ""
        return buildString {
            append('[')
            rects.forEachIndexed { idx, r ->
                if (idx > 0) append(',')
                append("{\"l\":")
                append(String.format(Locale.US, "%.4f", r.left))
                append(",\"t\":")
                append(String.format(Locale.US, "%.4f", r.top))
                append(",\"r\":")
                append(String.format(Locale.US, "%.4f", r.right))
                append(",\"b\":")
                append(String.format(Locale.US, "%.4f", r.bottom))
                append('}')
            }
            append(']')
        }
    }

    fun parseRects(json: String): List<NormRect> {
        if (json.isBlank()) return emptyList()
        val result = mutableListOf<NormRect>()
        for (match in rectBlockRegex.findAll(json)) {
            val body = match.groupValues[1]
            var l: Float? = null
            var t: Float? = null
            var r: Float? = null
            var b: Float? = null
            for (field in rectFieldRegex.findAll(body)) {
                val value = field.groupValues[2].toFloatOrNull() ?: continue
                if (!value.isFinite()) continue
                when (field.groupValues[1]) {
                    "l" -> l = value
                    "t" -> t = value
                    "r" -> r = value
                    "b" -> b = value
                }
            }
            if (l != null && t != null && r != null && b != null && r > l && b > t) {
                result.add(NormRect(l, t, r, b))
            }
        }
        return result
    }
}
