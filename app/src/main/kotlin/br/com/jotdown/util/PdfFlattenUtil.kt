package br.com.jotdown.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import br.com.jotdown.R
import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.data.entity.DrawingEntity
import br.com.jotdown.ui.screens.reader.Tool
import br.com.jotdown.ui.screens.reader.parseDrawingsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfFlattenUtil {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val APPENDIX_MARGIN = 48f
    private const val APPENDIX_USABLE_WIDTH = 499
    private const val APPENDIX_USABLE_HEIGHT = 746f

    suspend fun flattenPdf(
        context: Context,
        pdfFile: File,
        drawings: List<DrawingEntity>,
        annotations: List<AnnotationEntity>,
        title: String
    ): File? = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) return@withContext null

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val outputDoc = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val screenWidth = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val annotationsByPage = annotations
                .groupBy { it.page }
                .mapValues { (_, list) -> list.sortedBy { it.id } }

            for (i in 0 until pageCount) {
                val pageNumber = i + 1
                val page = renderer.openPage(i)
                val origW = page.width
                val origH = page.height

                // Renderiza em 1.5x para nitidez sem estouro de memória
                val scale = 1.5f
                val bmpW = (origW * scale).toInt().coerceAtLeast(1)
                val bmpH = (origH * scale).toInt().coerceAtLeast(1)

                val bitmap = try {
                    Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    }
                } catch (oom: OutOfMemoryError) {
                    // Fallback para 1x se faltar memória
                    Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    }
                }
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, pageNumber).create()
                val pdfPage = outputDoc.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // 1. Desenha página base do PDF
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, origW.toFloat(), origH.toFloat()), null)
                bitmap.recycle()

                val strokeScale = origW.toFloat() / screenWidth

                // 2. Sobrepõe traços vetoriais em camada offscreen para suportar Tool.ERASER
                val drawing = drawings.find { it.page == pageNumber }
                if (drawing != null && drawing.pathsJson.isNotBlank()) {
                    drawPageInk(canvas, drawing.pathsJson, origW, origH, strokeScale)
                }

                // 3. Sobrepõe marcadores de anotação (post-its)
                val pageAnnotations = annotationsByPage[pageNumber].orEmpty()
                if (pageAnnotations.isNotEmpty()) {
                    drawPageAnnotationBadges(canvas, pageAnnotations, strokeScale)
                }

                outputDoc.finishPage(pdfPage)
            }

            // 4. Anexa páginas A4 com o texto completo das anotações (post-its)
            val notedAnnotations = annotations.filter { it.text.isNotBlank() }
            if (notedAnnotations.isNotEmpty()) {
                appendAnnotationsAppendix(
                    context = context,
                    outputDoc = outputDoc,
                    startPageNumber = pageCount + 1,
                    annotationsByPage = annotationsByPage,
                    notedPages = notedAnnotations.map { it.page }.distinct().sorted()
                )
            }

            val cleanTitle = (if (title.isBlank()) "documento" else title.replace(" ", "_"))
                .replace(Regex("[^A-Za-z0-9_]"), "")
            val outFile = File(context.cacheDir, "${cleanTitle}_anotado.pdf")
            FileOutputStream(outFile).use { outputDoc.writeTo(it) }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { outputDoc.close() } catch (e: Exception) {}
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
        }
    }

    private fun drawPageInk(
        canvas: Canvas,
        pathsJson: String,
        origW: Int,
        origH: Int,
        strokeScale: Float
    ) {
        val paths = parseDrawingsJson(pathsJson)
        if (paths.isEmpty()) return

        val inkScale = 1.5f
        val inkW = (origW * inkScale).toInt().coerceAtLeast(1)
        val inkH = (origH * inkScale).toInt().coerceAtLeast(1)
        var effectiveInkScale = inkScale
        val inkBitmap = try {
            Bitmap.createBitmap(inkW, inkH, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            effectiveInkScale = 1f
            Bitmap.createBitmap(origW.coerceAtLeast(1), origH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }

        try {
            inkBitmap.eraseColor(Color.TRANSPARENT)
            val inkCanvas = Canvas(inkBitmap)
            inkCanvas.scale(effectiveInkScale * strokeScale, effectiveInkScale * strokeScale)

            paths.forEach { path ->
                if (path.points.size < 2) return@forEach

                val strokeWidthPx: Float
                val strokeAlpha: Int
                val isEraser = path.tool == Tool.ERASER
                when (path.tool) {
                    Tool.ERASER -> {
                        strokeWidthPx = 30f * path.widthMultiplier
                        strokeAlpha = 255
                    }
                    Tool.HIGHLIGHTER -> {
                        // SRC_OVER (não MULTIPLY) sobre bitmap transparente preserva a cor para que ERASER apague limpo.
                        strokeWidthPx = 25f * path.widthMultiplier
                        strokeAlpha = (0.35f * 255).toInt()
                    }
                    Tool.PEN -> {
                        strokeWidthPx = 3f * path.widthMultiplier
                        strokeAlpha = (0.9f * 255).toInt()
                    }
                    Tool.PENCIL -> {
                        strokeWidthPx = 4f * path.widthMultiplier
                        strokeAlpha = (0.85f * 255).toInt()
                    }
                    Tool.NONE, Tool.SELECT, Tool.DICTIONARY, Tool.ANNOTATION -> return@forEach
                }

                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = strokeWidthPx
                    if (isEraser) {
                        color = Color.TRANSPARENT
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    } else {
                        color = path.color.toArgb()
                        alpha = strokeAlpha
                    }
                }

                val drawPath = Path().apply {
                    moveTo(path.points[0].x, path.points[0].y)
                    for (pIdx in 1 until path.points.size) {
                        lineTo(path.points[pIdx].x, path.points[pIdx].y)
                    }
                }
                inkCanvas.drawPath(drawPath, paint)
            }

            canvas.drawBitmap(inkBitmap, null, RectF(0f, 0f, origW.toFloat(), origH.toFloat()), null)
        } finally {
            inkBitmap.recycle()
        }
    }

    private fun drawPageAnnotationBadges(
        canvas: Canvas,
        pageAnnotations: List<AnnotationEntity>,
        strokeScale: Float
    ) {
        canvas.save()
        canvas.scale(strokeScale, strokeScale)

        val markerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(245, 158, 11) // Amber #F59E0B
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        pageAnnotations.forEachIndexed { idx, ann ->
            val rect = RectF(ann.x - 16f, ann.y - 16f, ann.x + 16f, ann.y + 16f)
            canvas.drawRoundRect(rect, 4f, 4f, markerPaint)
            canvas.drawText("${idx + 1}", ann.x, ann.y + 5f, textPaint)
        }

        canvas.restore()
    }

    private fun appendAnnotationsAppendix(
        context: Context,
        outputDoc: PdfDocument,
        startPageNumber: Int,
        annotationsByPage: Map<Int, List<AnnotationEntity>>,
        notedPages: List<Int>
    ) {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(17, 24, 39)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 83, 9) // Amber #B45309
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(31, 41, 55)
            textSize = 11f
            typeface = Typeface.DEFAULT
        }
        val dividerPaint = Paint().apply {
            color = Color.rgb(229, 231, 235)
            strokeWidth = 1f
        }

        var nextPageNumber = startPageNumber
        var currentPage: PdfDocument.Page? = null
        var currentCanvas: Canvas? = null
        var cursorY = 0f

        fun startNewPage() {
            currentPage?.let { outputDoc.finishPage(it) }
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, nextPageNumber++).create()
            val page = outputDoc.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            currentPage = page
            currentCanvas = page.canvas
            cursorY = 0f
        }

        fun ensureSpace(heightNeeded: Float) {
            if (currentPage == null) {
                startNewPage()
            } else if (cursorY > 0f && cursorY + heightNeeded > APPENDIX_USABLE_HEIGHT) {
                startNewPage()
            }
        }

        fun drawPaginatedLayout(layout: StaticLayout) {
            if (layout.lineCount == 0) return
            if (currentPage == null) startNewPage()

            var startLine = 0
            while (startLine < layout.lineCount) {
                val sliceTop = layout.getLineTop(startLine).toFloat()
                var endLine = startLine

                while (endLine < layout.lineCount) {
                    val lineBottom = layout.getLineBottom(endLine).toFloat()
                    val relHeight = lineBottom - sliceTop
                    if (cursorY + relHeight <= APPENDIX_USABLE_HEIGHT || (cursorY == 0f && endLine == startLine)) {
                        endLine++
                    } else {
                        break
                    }
                }

                if (endLine == startLine) {
                    startNewPage()
                    continue
                }

                val sliceBottom = layout.getLineBottom(endLine - 1).toFloat()
                val sliceHeight = sliceBottom - sliceTop
                val c = currentCanvas!!
                c.save()
                if (startLine > 0 || endLine < layout.lineCount) {
                    c.clipRect(
                        0f,
                        APPENDIX_MARGIN + cursorY,
                        A4_WIDTH.toFloat(),
                        APPENDIX_MARGIN + cursorY + sliceHeight
                    )
                }
                c.translate(APPENDIX_MARGIN, APPENDIX_MARGIN + cursorY - sliceTop)
                layout.draw(c)
                c.restore()

                cursorY += sliceHeight
                startLine = endLine

                if (startLine < layout.lineCount) {
                    startNewPage()
                }
            }
        }

        val titleLayout = buildStaticLayout(
            text = context.getString(R.string.reader_annotations_postits),
            paint = titlePaint
        )
        drawPaginatedLayout(titleLayout)
        cursorY += 6f
        currentCanvas?.drawLine(
            APPENDIX_MARGIN,
            APPENDIX_MARGIN + cursorY,
            APPENDIX_MARGIN + APPENDIX_USABLE_WIDTH.toFloat(),
            APPENDIX_MARGIN + cursorY,
            dividerPaint
        )
        cursorY += 12f

        var isFirstPageSection = true
        notedPages.forEach { page ->
            val pageNotes = annotationsByPage[page].orEmpty().mapIndexedNotNull { idx, ann ->
                if (ann.text.isNotBlank()) (idx + 1) to ann.text.trim() else null
            }
            if (pageNotes.isEmpty()) return@forEach

            val headingLayout = buildStaticLayout(
                text = context.getString(R.string.reader_page_n, page),
                paint = headingPaint
            )
            val noteLayouts = pageNotes.map { (badgeNum, text) ->
                buildStaticLayout(text = "[$badgeNum] $text", paint = bodyPaint)
            }

            val firstLineHeight = if (noteLayouts.first().lineCount > 0) {
                noteLayouts.first().getLineBottom(0).toFloat()
            } else 0f
            val minSectionHeight = headingLayout.height.toFloat() + 6f + firstLineHeight

            if (!isFirstPageSection) {
                cursorY += 12f
            }
            isFirstPageSection = false

            ensureSpace(minSectionHeight)
            drawPaginatedLayout(headingLayout)
            cursorY += 6f

            noteLayouts.forEachIndexed { idx, noteLayout ->
                drawPaginatedLayout(noteLayout)
                if (idx < noteLayouts.lastIndex) {
                    cursorY += 8f
                }
            }
        }

        currentPage?.let { outputDoc.finishPage(it) }
    }

    private fun buildStaticLayout(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, APPENDIX_USABLE_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1.15f)
            .setIncludePad(false)
            .build()
}
