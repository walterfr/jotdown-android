package br.com.jotdown.util

import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.entity.HighlightEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Exporta as anotações e fichamentos de um documento para Markdown,
 * pronto para importar no Obsidian, Notion, Logseq, etc.
 */
object ExportUtil {

    fun toMarkdown(
        doc: DocumentEntity,
        annotations: List<AnnotationEntity>,
        highlights: List<HighlightEntity>,
    ): String = buildString {
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())

        // ── Cabeçalho ───────────────────────────────────────────────────────
        appendLine("# ${doc.title.ifBlank { doc.fileName }}")
        appendLine()

        // ── Metadados ────────────────────────────────────────────────────────
        appendLine("## Metadados")
        appendLine()
        if (doc.authorLastName.isNotBlank())
            appendLine("- **Autor:** ${doc.authorLastName}${if (doc.authorFirstName.isNotBlank()) ", ${doc.authorFirstName}" else ""}")
        if (doc.year.isNotBlank())
            appendLine("- **Ano:** ${doc.year}")
        if (doc.publisher.isNotBlank())
            appendLine("- **Editora:** ${doc.publisher}")
        if (doc.city.isNotBlank())
            appendLine("- **Cidade:** ${doc.city}")
        if (doc.labels.isNotBlank())
            appendLine("- **Rótulos:** ${doc.labels}")
        appendLine("- **Exportado:** $date via Jotdown")
        appendLine()

        // ── Citação ABNT ─────────────────────────────────────────────────────
        val abnt = AbntUtil.format(doc)
        if (abnt.isNotBlank()) {
            appendLine("## Referência ABNT")
            appendLine()
            appendLine("> $abnt")
            appendLine()
        }

        // ── Fichamentos (highlights) ─────────────────────────────────────────
        val validHighlights = highlights.filter { it.text.isNotBlank() }.sortedBy { it.page }
        if (validHighlights.isNotEmpty()) {
            appendLine("## 📌 Fichamentos")
            appendLine()
            var lastPage = -1
            validHighlights.forEach { h ->
                if (h.page != lastPage) {
                    appendLine("### Página ${h.page}")
                    appendLine()
                    lastPage = h.page
                }
                appendLine("> ${h.text.trim()}")
                appendLine()
            }
        }

        // ── Anotações (post-its) categorizadas por cor (I8) ────────────────
        val validAnnotations = annotations.filter { it.text.isNotBlank() }
        if (validAnnotations.isNotEmpty()) {
            appendLine("## \uD83D\uDCDD Anotações (Categorizadas)")
            appendLine()
            
            // Define categorias com base nas cores usadas no ReaderScreen
            val categories = mapOf(
                Color.Red.toArgb().toLong() to "Crítico / Revisar",
                Color(0xFFEAB308).toArgb().toLong() to "Dúvidas / Detalhes",
                Color.Blue.toArgb().toLong() to "Referências / Autores",
                Color(0xFF22C55E).toArgb().toLong() to "Citações Importantes"
            )
            
            // Agrupa por cor
            val grouped = validAnnotations.groupBy { it.color.toLongOrNull() ?: androidx.compose.ui.graphics.Color.Black.toArgb().toLong() }
            
            grouped.forEach { (colorArgb, list) ->
                val categoryName = categories[colorArgb] ?: "Geral / Outros"
                appendLine("### $categoryName")
                list.sortedBy { it.page }.forEach { a ->
                    appendLine("- **p. ${a.page}** — ${a.text.trim()}")
                }
                appendLine()
            }
        }
    }

    /** Nome de arquivo sugerido para o export */
    fun suggestedFileName(doc: DocumentEntity): String {
        val author = doc.authorLastName.take(20).replace(" ", "_").ifBlank { "Jotdown" }
        val title  = doc.title.take(30).replace(Regex("[^A-Za-z0-9À-ÿ ]"), "").replace(" ", "_")
        val year   = doc.year.ifBlank { "" }
        return "${author}_${title}${if (year.isNotBlank()) "_$year" else ""}.md"
    }
}
