package br.com.jotdown.util

import br.com.jotdown.data.entity.DocumentEntity

/**
 * Utilitário central de citações do Jotdown:
 * Suporta ABNT NBR 6023 (via [AbntUtil]), APA 7ª Edição e BibTeX (.bib).
 */
object CitationUtil {

    /** Formata no padrão ABNT NBR 6023 */
    fun formatAbnt(doc: DocumentEntity): String = AbntUtil.format(doc)

    /**
     * Formata citação no padrão APA 7ª Edição.
     * Livro: Sobrenome, I. (Ano). *Título* (ed.). Editora.
     * Artigo: Sobrenome, I. (Ano). Título do artigo. *Periódico*, *v*(n), p–p.
     */
    fun formatApa(doc: DocumentEntity): String = buildString {
        appendApaAuthor(doc)
        val year = doc.year.trim().ifBlank { "s.d." }
        append("($year). ")

        when (doc.docType.lowercase()) {
            "artigo", "artigo de periódico" -> {
                append(doc.title.trim())
                if (doc.subtitle.isNotBlank()) append(": ${doc.subtitle.trim()}")
                append(". ")
                if (doc.journal.isNotBlank()) append("*${doc.journal.trim()}*")
                if (doc.volume.isNotBlank()) append(", *${doc.volume.trim()}*")
                if (doc.edition.isNotBlank()) append("(${doc.edition.trim()})")
                if (doc.pages.isNotBlank()) append(", ${doc.pages.trim()}")
                append(".")
            }
            "capítulo de livro" -> {
                append(doc.title.trim())
                append(". In ")
                if (doc.subtitle.isNotBlank()) append("${doc.subtitle.trim()} ")
                if (doc.journal.isNotBlank()) append("*${doc.journal.trim()}* ")
                if (doc.pages.isNotBlank()) append("(pp. ${doc.pages.trim()}). ")
                if (doc.publisher.isNotBlank()) append("${doc.publisher.trim()}.")
            }
            "tese", "trabalho acadêmico" -> {
                append("*${doc.title.trim()}*")
                if (doc.subtitle.isNotBlank()) append(": ${doc.subtitle.trim()}")
                val inst = doc.publisher.ifBlank { doc.journal }.trim()
                val note = if (inst.isNotBlank()) " [Tese acadêmica, $inst]" else " [Tese acadêmica]"
                append(note)
                append(".")
            }
            "site" -> {
                append("*${doc.title.trim()}*")
                if (doc.subtitle.isNotBlank()) append(": ${doc.subtitle.trim()}")
                append(". ")
                if (doc.url.isNotBlank()) append(doc.url.trim())
            }
            else -> { // Livro / Monografia
                append("*${doc.title.trim()}*")
                if (doc.subtitle.isNotBlank()) append(": ${doc.subtitle.trim()}")
                if (doc.edition.isNotBlank()) append(" (${doc.edition.trim()} ed.)")
                append(". ")
                if (doc.publisher.isNotBlank()) append("${doc.publisher.trim()}.")
            }
        }
    }

    /**
     * Gera entrada completa em formato BibTeX (.bib).
     */
    fun toBibtex(doc: DocumentEntity): String = buildString {
        val type = when (doc.docType.lowercase()) {
            "artigo", "artigo de periódico" -> "article"
            "tese", "trabalho acadêmico"   -> "phdthesis"
            "capítulo de livro"            -> "incollection"
            "site"                         -> "misc"
            else                           -> "book"
        }

        val key = generateCiteKey(doc)
        appendLine("@$type{$key,")

        val fields = mutableListOf<Pair<String, String>>()

        // Autores: BibTeX requer "Sobrenome, Nome"
        val authorStr = buildString {
            if (doc.authorLastName.isNotBlank()) {
                append(doc.authorLastName.trim())
                if (doc.authorFirstName.isNotBlank()) append(", ${doc.authorFirstName.trim()}")
            }
        }
        if (authorStr.isNotBlank()) fields.add("author" to "{$authorStr}")

        val fullTitle = buildString {
            append(doc.title.trim())
            if (doc.subtitle.isNotBlank()) append(": ${doc.subtitle.trim()}")
        }
        if (fullTitle.isNotBlank()) fields.add("title" to "{$fullTitle}")

        if (doc.year.isNotBlank()) fields.add("year" to "{${doc.year.trim()}}")

        when (type) {
            "article" -> {
                if (doc.journal.isNotBlank()) fields.add("journal" to "{${doc.journal.trim()}}")
                if (doc.volume.isNotBlank()) fields.add("volume" to "{${doc.volume.trim()}}")
                if (doc.edition.isNotBlank()) fields.add("number" to "{${doc.edition.trim()}}")
                if (doc.pages.isNotBlank()) fields.add("pages" to "{${doc.pages.trim()}}")
            }
            "phdthesis" -> {
                val school = doc.publisher.ifBlank { doc.journal }.trim()
                if (school.isNotBlank()) fields.add("school" to "{$school}")
                if (doc.city.isNotBlank()) fields.add("address" to "{${doc.city.trim()}}")
            }
            "incollection" -> {
                if (doc.journal.isNotBlank()) fields.add("booktitle" to "{${doc.journal.trim()}}")
                if (doc.publisher.isNotBlank()) fields.add("publisher" to "{${doc.publisher.trim()}}")
                if (doc.city.isNotBlank()) fields.add("address" to "{${doc.city.trim()}}")
                if (doc.pages.isNotBlank()) fields.add("pages" to "{${doc.pages.trim()}}")
            }
            "misc" -> {
                if (doc.url.isNotBlank()) fields.add("howpublished" to "{\\url{${doc.url.trim()}}}")
                if (doc.accessDate.isNotBlank()) fields.add("note" to "{Acesso em: ${doc.accessDate.trim()}}")
            }
            else -> { // book
                if (doc.publisher.isNotBlank()) fields.add("publisher" to "{${doc.publisher.trim()}}")
                if (doc.city.isNotBlank()) fields.add("address" to "{${doc.city.trim()}}")
                if (doc.edition.isNotBlank()) fields.add("edition" to "{${doc.edition.trim()}}")
            }
        }

        if (doc.doi.isNotBlank()) fields.add("doi" to "{${doc.doi.trim()}}")
        if (doc.isbn.isNotBlank()) fields.add("isbn" to "{${doc.isbn.trim()}}")
        if (doc.url.isNotBlank() && type != "misc") fields.add("url" to "{${doc.url.trim()}}")

        fields.forEachIndexed { index, (k, v) ->
            val comma = if (index < fields.size - 1) "," else ""
            appendLine("  $k = $v$comma")
        }
        append("}")
    }

    private fun StringBuilder.appendApaAuthor(doc: DocumentEntity) {
        if (doc.authorLastName.isNotBlank()) {
            append(doc.authorLastName.trim())
            if (doc.authorFirstName.isNotBlank()) {
                val initials = doc.authorFirstName.trim().split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                    .joinToString(" ") { "${it.first().uppercaseChar()}." }
                append(", $initials")
            }
            append(" ")
        }
    }

    private fun generateCiteKey(doc: DocumentEntity): String {
        val author = doc.authorLastName.lowercase().filter { it.isLetter() }.take(10)
        val year = doc.year.filter { it.isDigit() }.takeLast(4)
        val word = doc.title.split("\\s+".toRegex()).firstOrNull()?.lowercase()?.filter { it.isLetter() }?.take(8) ?: "ref"
        val base = "${author.ifBlank { "doc" }}$year$word"
        return base.ifBlank { "entry" }
    }
}
