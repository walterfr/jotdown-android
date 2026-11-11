package br.com.jotdown.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.toArgb
import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.data.entity.DrawingEntity
import br.com.jotdown.ui.screens.reader.Tool
import br.com.jotdown.ui.screens.reader.parseDrawingsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfFlattenUtil {

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
                        eraseColor(android.graphics.Color.WHITE)
                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    }
                } catch (oom: OutOfMemoryError) {
                    // Fallback para 1x se faltar memória
                    Bitmap.createBitmap(origW, origH, Bitmap.Config.ARGB_8888).apply {
                        eraseColor(android.graphics.Color.WHITE)
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

                // 2. Sobrepõe traços vetoriais da caneta / marca-texto
                val strokeScale = origW.toFloat() / screenWidth
                canvas.save()
                canvas.scale(strokeScale, strokeScale)

                val drawing = drawings.find { it.page == pageNumber }
                if (drawing != null && drawing.pathsJson.isNotBlank()) {
                    val paths = parseDrawingsJson(drawing.pathsJson)
                    paths.forEach { path ->
                        if (path.points.size >= 2) {
                            val baseWidth = when (path.tool) {
                                Tool.PEN -> 3f
                                Tool.PENCIL -> 4f
                                Tool.HIGHLIGHTER -> 25f
                                else -> 2f
                            } * path.widthMultiplier

                            val paint = Paint().apply {
                                isAntiAlias = true
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                                color = path.color.toArgb()
                                strokeWidth = baseWidth
                                if (path.tool == Tool.HIGHLIGHTER) {
                                    alpha = (0.35f * 255).toInt()
                                    xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                                } else {
                                    alpha = (0.9f * 255).toInt()
                                }
                            }

                            val drawPath = android.graphics.Path()
                            drawPath.moveTo(path.points[0].x, path.points[0].y)
                            for (pIdx in 1 until path.points.size) {
                                drawPath.lineTo(path.points[pIdx].x, path.points[pIdx].y)
                            }
                            canvas.drawPath(drawPath, paint)
                        }
                    }
                }

                // 3. Sobrepõe marcadores de anotação (post-its)
                val pageAnnotations = annotations.filter { it.page == pageNumber }
                if (pageAnnotations.isNotEmpty()) {
                    val markerPaint = Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.rgb(245, 158, 11) // Amber #F59E0B
                        style = Paint.Style.FILL
                    }
                    val textPaint = Paint().apply {
                        isAntiAlias = true
                        color = android.graphics.Color.WHITE
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                    }

                    pageAnnotations.forEachIndexed { idx, ann ->
                        val rect = RectF(ann.x - 14f, ann.y - 14f, ann.x + 14f, ann.y + 14f)
                        canvas.drawRoundRect(rect, 4f, 4f, markerPaint)
                        canvas.drawText("${idx + 1}", ann.x, ann.y + 4f, textPaint)
                    }
                }

                canvas.restore()
                outputDoc.finishPage(pdfPage)
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
}
