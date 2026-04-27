package br.com.jotdown.util

import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.entity.HighlightEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        // ── Anotações (post-its) ─────────────────────────────────────────────
        val validAnnotations = annotations.filter { it.text.isNotBlank() }.sortedBy { it.page }
        if (validAnnotations.isNotEmpty()) {
            appendLine("## 📝 Anotações")
            appendLine()
            validAnnotations.forEach { a ->
                appendLine("- **p. ${a.page}** — ${a.text.trim()}")
            }
            appendLine()
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
