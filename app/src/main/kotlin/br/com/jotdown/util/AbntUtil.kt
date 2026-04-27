package br.com.jotdown.util

import br.com.jotdown.data.entity.DocumentEntity

/**
 * Formata citações no padrão ABNT NBR 6023:2018.
 *
 * Suporta os tipos de documento cadastrados no Jotdown:
 * livro, artigo, tese, site e genérico.
 */
object AbntUtil {

    fun format(doc: DocumentEntity): String = when (doc.docType) {
        "artigo" -> formatArtigo(doc)
        "tese"   -> formatTese(doc)
        "site"   -> formatSite(doc)
        else     -> formatLivro(doc)
    }

    /** SOBRENOME, Nome. **Título**: subtítulo. ed. Cidade: Editora, Ano. */
    private fun formatLivro(doc: DocumentEntity): String = buildString {
        appendAuthor(doc)
        appendTitleBold(doc)
        if (doc.edition.isNotBlank()) append(" ${doc.edition}. ed.")
        if (doc.city.isNotBlank()) append(" ${doc.city}:")
        if (doc.publisher.isNotBlank()) append(" ${doc.publisher},")
        if (doc.year.isNotBlank()) append(" ${doc.year}")
        append(".")
    }

    /** SOBRENOME, Nome. Título do artigo. **Periódico**, local, v. X, p. XX-XX, ano. */
    private fun formatArtigo(doc: DocumentEntity): String = buildString {
        appendAuthor(doc)
        append(doc.title.trim())
        if (doc.subtitle.isNotBlank()) append(": ${doc.subtitle.trim()}")
        append(".")
        if (doc.journal.isNotBlank()) append(" **${doc.journal.trim()}**,")
        if (doc.city.isNotBlank()) append(" ${doc.city.trim()},")
        if (doc.volume.isNotBlank()) append(" v. ${doc.volume.trim()},")
        if (doc.pages.isNotBlank()) append(" p. ${doc.pages.trim()},")
        if (doc.year.isNotBlank()) append(" ${doc.year.trim()}")
        append(".")
    }

    /** SOBRENOME, Nome. **Título**. Ano. N f. Tese (Doutorado/Mestrado). Instituição, Cidade, Ano. */
    private fun formatTese(doc: DocumentEntity): String = buildString {
        appendAuthor(doc)
        appendTitleBold(doc)
        if (doc.year.isNotBlank()) append(" ${doc.year}.")
        if (doc.pages.isNotBlank()) append(" ${doc.pages} f.")
        append(" Tese.")
        if (doc.publisher.isNotBlank()) append(" ${doc.publisher},")
        if (doc.city.isNotBlank()) append(" ${doc.city},")
        if (doc.year.isNotBlank()) append(" ${doc.year}")
        append(".")
    }

    /** SOBRENOME, Nome. **Título**. Disponível em: URL. Acesso em: data. */
    private fun formatSite(doc: DocumentEntity): String = buildString {
        appendAuthor(doc)
        appendTitleBold(doc)
        if (doc.url.isNotBlank()) append(" Disponível em: ${doc.url.trim()}.")
        if (doc.accessDate.isNotBlank()) append(" Acesso em: ${doc.accessDate.trim()}.")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun StringBuilder.appendAuthor(doc: DocumentEntity) {
        if (doc.authorLastName.isNotBlank()) {
            append(doc.authorLastName.uppercase().trim())
            if (doc.authorFirstName.isNotBlank()) append(", ${doc.authorFirstName.trim()}")
            append(". ")
        }
    }

    private fun StringBuilder.appendTitleBold(doc: DocumentEntity) {
        append("**${doc.title.trim()}**")
        if (doc.subtitle.isNotBlank()) append(": ${doc.subtitle.trim()}")
        append(".")
    }
}
