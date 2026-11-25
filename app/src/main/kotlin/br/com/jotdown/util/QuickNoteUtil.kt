package br.com.jotdown.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import br.com.jotdown.R
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Shared notebook PDF generator used by LibraryViewModel and Android 14+ ROLE_NOTES (ACTION_CREATE_NOTE).
 */
object QuickNoteUtil {

    suspend fun createNotebook(
        context: Context,
        repository: DocumentRepository,
        title: String,
        template: String = "Pautado",
        folderId: Long? = null
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val pdfDir = File(context.filesDir, "pdfs").also { it.mkdirs() }
        val pdfFile = File(pdfDir, "$docId.pdf")
        val pdfDocument = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val bgColor = if (template == "Fichamento" || template == "Branco") "#FFFFFF" else "#FAFAFA"
            canvas.drawColor(Color.parseColor(bgColor))

            val paint = Paint()
            when (template) {
                "Pautado" -> {
                    paint.color = Color.parseColor("#D1D5DB")
                    paint.strokeWidth = 1f
                    var y = 80f
                    while (y < 842f) {
                        canvas.drawLine(0f, y, 595f, y, paint)
                        y += 30f
                    }
                    paint.color = Color.parseColor("#FCA5A5")
                    paint.strokeWidth = 2f
                    canvas.drawLine(80f, 0f, 80f, 842f, paint)
                }
                "Quadriculado" -> {
                    paint.color = Color.parseColor("#E5E7EB")
                    paint.strokeWidth = 1f
                    var step = 20f
                    while (step < 842f) {
                        canvas.drawLine(0f, step, 595f, step, paint)
                        step += 20f
                    }
                    step = 20f
                    while (step < 595f) {
                        canvas.drawLine(step, 0f, step, 842f, paint)
                        step += 20f
                    }
                }
                "Fichamento" -> {
                    paint.color = Color.parseColor("#374151")
                    paint.textSize = 14f
                    paint.isAntiAlias = true
                    canvas.drawText(context.getString(R.string.template_notes_header), 210f, 60f, paint)
                    paint.strokeWidth = 2f
                    canvas.drawLine(50f, 80f, 545f, 80f, paint)

                    paint.textSize = 10f
                    paint.color = Color.parseColor("#9CA3AF")
                    canvas.drawText(context.getString(R.string.template_abnt_reference), 50f, 110f, paint)
                    paint.strokeWidth = 1f
                    canvas.drawLine(50f, 130f, 545f, 130f, paint)

                    canvas.drawText(context.getString(R.string.template_main_ideas), 50f, 160f, paint)
                    canvas.drawLine(50f, 180f, 545f, 180f, paint)
                    canvas.drawLine(50f, 210f, 545f, 210f, paint)
                    canvas.drawLine(50f, 240f, 545f, 240f, paint)

                    canvas.drawText(context.getString(R.string.template_quotes), 50f, 290f, paint)
                    canvas.drawLine(50f, 310f, 545f, 310f, paint)
                }
            }

            pdfDocument.finishPage(page)
            pdfFile.outputStream().use { out -> pdfDocument.writeTo(out) }

            val coverDir = File(context.filesDir, "covers").also { it.mkdirs() }
            val coverFile = File(coverDir, "$docId.jpg")
            PdfCoverUtil.generateCover(pdfFile, coverFile)

            val doc = DocumentEntity(
                id = docId,
                fileName = "$title.pdf",
                title = title,
                dateAdded = System.currentTimeMillis(),
                folderId = folderId,
                pdfFilePath = pdfFile.absolutePath,
                docType = "nota"
            )
            repository.saveDocument(doc)
            doc
        } catch (e: Exception) {
            Log.e("QuickNoteUtil", "Error creating notebook: ${e.message}")
            runCatching { pdfFile.delete() }
            null
        } finally {
            runCatching { pdfDocument.close() }
        }
    }
}
