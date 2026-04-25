package br.com.jotdown.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.entity.HighlightEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtil {

    private fun getReferenceData(doc: DocumentEntity): Triple<String, String, String> {
        val lastName  = doc.authorLastName.uppercase(Locale.getDefault())
        val authorFmt = doc.authorLastName.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
        val fSub = if (doc.subtitle.isNotBlank()) ": ${doc.subtitle}" else ""
        val fEd  = if (doc.edition.isNotBlank()) ". ${doc.edition} ed. " else ""

        val refText = when (doc.docType) {
            "livro"  -> "$lastName, ${doc.authorFirstName}. ${doc.title}$fSub$fEd. ${doc.city}: ${doc.publisher}, ${doc.year}."
            "artigo" -> {
                val vv = if (doc.volume.isNotBlank()) "v. ${doc.volume}, " else ""
                val pp = if (doc.pages.isNotBlank()) "p. ${doc.pages}, " else ""
                "$lastName, ${doc.authorFirstName}. ${doc.title}$fSub. ${doc.journal}, ${doc.city}, $vv$pp${doc.year}."
            }
            else -> "$lastName, ${doc.authorFirstName}. ${doc.title}$fSub. Disponível em: <${doc.url}>. Acesso em: ${doc.accessDate}."
        }
        return Triple(refText, authorFmt, doc.year)
    }

    fun exportMD(
        context: Context,
        doc: DocumentEntity,
        highlights: List<HighlightEntity>
    ) {
        val (ref, authorFmt, year) = getReferenceData(doc)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sb = StringBuilder()

        sb.appendLine("---")
        sb.appendLine("aliases: [\"Fichamento - ${doc.title}\"]")
        sb.appendLine("tags: [fichamento, historia, leitura]")
        sb.appendLine("autor: \"${doc.authorFirstName} ${doc.authorLastName}\"")
        sb.appendLine("ano: \"$year\"")
        sb.appendLine("tipo: \"${doc.docType}\"")
        sb.appendLine("data_fichamento: \"$date\"")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("# ${doc.title}")
        sb.appendLine()
        sb.appendLine("## Referência Bibliográfica")
        sb.appendLine(ref)
        sb.appendLine()
        sb.appendLine("## Destaques e Citações")
        sb.appendLine()

        highlights.forEach { h ->
            sb.appendLine("> \"${h.text}\" ($authorFmt, $year, p. ${h.page})")
            sb.appendLine()
        }

        val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "")
        shareFile(context, sb.toString(), "Fichamento_$safeTitle.md", "text/markdown")
    }

    fun exportPDF(
        context: Context,
        doc: DocumentEntity,
        highlights: List<HighlightEntity>
    ) {
        try {
            val (ref, authorFmt, year) = getReferenceData(doc)
            val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "")

            val pageW = 595f; val pageH = 842f
            val marginL = 48f; val marginR = pageW - 48f
            val lineW = marginR - marginL
            val marginBottom = pageH - 48f

            // Paints
            val pTitle   = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); textSize = 16f; color = android.graphics.Color.rgb(30, 27, 75) }
            val pSection = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD); textSize = 11f; color = android.graphics.Color.rgb(79, 70, 229) }
            val pBody    = Paint().apply { textSize = 10f; color = android.graphics.Color.rgb(30, 41, 59) }
            val pItalic  = Paint().apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC); textSize = 10f; color = android.graphics.Color.rgb(51, 65, 85) }
            val pSmall   = Paint().apply { textSize = 8f; color = android.graphics.Color.rgb(100, 116, 139) }
            val pDiv     = Paint().apply { color = android.graphics.Color.rgb(203, 213, 225); strokeWidth = 0.8f; style = Paint.Style.STROKE }

            // Quebra texto em linhas
            fun wrapText(text: String, paint: Paint, maxW: Float = lineW, indent: Float = 0f): List<String> {
                val words = text.split(" ")
                val result = mutableListOf<String>()
                var line = ""
                for (w in words) {
                    val test = if (line.isEmpty()) w else "$line $w"
                    if (paint.measureText(test) <= maxW - indent) line = test
                    else { if (line.isNotEmpty()) result.add(line); line = w }
                }
                if (line.isNotEmpty()) result.add(line)
                return result
            }

            // Coleta todas as linhas a desenhar como lista (text, paint, lineH, indent, isDivider, isSpace)
            data class Line(
                val text: String = "",
                val paint: Paint? = null,
                val lineH: Float = 0f,
                val indent: Float = 0f,
                val isDivider: Boolean = false
            )
            val drawLines = mutableListOf<Line>()

            fun addText(t: String, p: Paint, h: Float, indent: Float = 0f) {
                wrapText(t, p, lineW, indent).forEach { drawLines.add(Line(it, p, h, indent)) }
            }
            fun addSpace(h: Float) = drawLines.add(Line(lineH = h))
            fun addDivider()      = drawLines.add(Line(isDivider = true, lineH = 6f))

            // Conteúdo
            addText(doc.title, pTitle, 22f)
            addSpace(6f); addDivider(); addSpace(8f)
            addText("REFERÊNCIA ABNT", pSection, 14f)
            addText(ref, pBody, 13f)
            addSpace(8f); addDivider(); addSpace(8f)
            addText("CITAÇÕES DIRETAS (${highlights.size})", pSection, 16f)
            highlights.forEachIndexed { i, h ->
                addSpace(4f)
                addText("[${i + 1}]  p. ${h.page}", pSmall, 12f)
                addText("\"${h.text}\"", pItalic, 13f, indent = 8f)
                addText("($authorFmt, $year, p. ${h.page})", pSmall, 16f, indent = 8f)
            }

            // Renderiza em páginas PDF
            val pdfDoc = PdfDocument()
            var pageNum = 1
            var page    = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageNum).create())
            var canvas  = page.canvas
            var y       = 60f

            for (dl in drawLines) {
                if (y + dl.lineH > marginBottom) {
                    pdfDoc.finishPage(page)
                    pageNum++
                    page   = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageNum).create())
                    canvas = page.canvas
                    y      = 60f
                }
                when {
                    dl.isDivider -> { canvas.drawLine(marginL, y, marginR, y, pDiv); y += dl.lineH }
                    dl.paint != null -> { canvas.drawText(dl.text, marginL + dl.indent, y, dl.paint); y += dl.lineH }
                    else -> { y += dl.lineH }
                }
            }
            pdfDoc.finishPage(page)

            val ctx  = context.applicationContext
            val file = File(ctx.cacheDir, "Fichamento_$safeTitle.pdf")
            file.outputStream().use { pdfDoc.writeTo(it) }
            pdfDoc.close()

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Exportar fichamento PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)

        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Erro ao exportar PDF: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }


    fun exportTXT(
        context: Context,
        doc: DocumentEntity,
        highlights: List<HighlightEntity>
    ) {
        val (ref, authorFmt, year) = getReferenceData(doc)
        val sep = "=".repeat(52)
        val dash = "-".repeat(52)
        val sb = StringBuilder()

        sb.appendLine("FICHAMENTO: ${doc.title}")
        sb.appendLine(sep)
        sb.appendLine()
        sb.appendLine("REFERÊNCIA COMPLETA:")
        sb.appendLine(ref)
        sb.appendLine()
        sb.appendLine(dash)
        sb.appendLine("CITAÇÕES DIRETAS:")
        sb.appendLine()
        highlights.forEach { h ->
            sb.appendLine("\"${h.text}\" ($authorFmt, $year, p. ${h.page})")
            sb.appendLine()
        }

        val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "")
        shareFile(context, sb.toString(), "Fichamento_$safeTitle.txt", "text/plain")
    }

    private fun shareFile(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String
    ) {
        try {
            val ctx  = context.applicationContext
            val file = File(ctx.cacheDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Exportar fichamento").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "Erro ao exportar: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}
