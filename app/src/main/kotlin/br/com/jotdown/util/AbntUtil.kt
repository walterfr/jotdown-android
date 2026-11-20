package br.com.jotdown.util

import br.com.jotdown.data.entity.DocumentEntity

/**
 * Formata citações no padrão ABNT NBR 6023:2018.
 *
 * Suporta os tipos de documento cadastrados no Jotdown:
 * livro, capítulo de livro, artigo, tese/trabalho acadêmico, documento jurídico, site e genérico.
 */
object AbntUtil {

    fun format(doc: DocumentEntity): String {
        val normalizedType = doc.docType.trim().lowercase()
        val base = when (normalizedType) {
            "artigo", "artigo de periódico", "artigo de periodico" -> formatArtigo(doc)
            "capítulo de livro", "capitulo de livro"               -> formatCapitulo(doc)
            "tese", "trabalho acadêmico", "trabalho academico"     -> formatTese(doc)
            "documento jurídico", "documento juridico"              -> formatJuridico(doc)
            "site"                                                 -> formatSite(doc)
            else                                                   -> formatLivro(doc)
        }
        return if (normalizedType != "site" && doc.url.isNotBlank()) {
            joinParts(base, formatOnlineAddon(doc))
        } else {
            base
        }
    }

    /** SOBRENOME, Nome. **Título**: subtítulo. ed. Cidade: Editora, Ano. */
    private fun formatLivro(doc: DocumentEntity): String = joinParts(
        formatAuthor(doc),
        formatTitleBold(doc),
        formatEdition(doc),
        formatImprint(doc)
    )

    /** SOBRENOME, Nome. Título do artigo. **Periódico**, local, v. X, n. Y, p. XX-XX, ano. */
    private fun formatArtigo(doc: DocumentEntity): String {
        val tail = listOfNotNull(
            doc.journal.trim().takeIf { it.isNotBlank() }?.let { "**$it**" },
            doc.city.trim().takeIf { it.isNotBlank() },
            doc.volume.trim().takeIf { it.isNotBlank() }?.let { "v. $it" },
            doc.edition.trim().takeIf { it.isNotBlank() }?.let { "n. $it" },
            doc.pages.trim().takeIf { it.isNotBlank() }?.let { "p. $it" },
            doc.year.trim().takeIf { it.isNotBlank() }
        ).joinToString(", ")

        return joinParts(
            formatAuthor(doc),
            formatTitlePlain(doc),
            tail.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty()
        )
    }

    /** SOBRENOME, Nome. Título. In: SUBTÍTULO_UPPER. **Periódico/Livro**. ed. Cidade: Editora, Ano. p. Páginas. */
    private fun formatCapitulo(doc: DocumentEntity): String {
        val title = doc.title.trim()
        val bookAuthor = doc.subtitle.trim()
        val bookTitle = doc.journal.trim()
        val edition = formatEdition(doc)
        val imprint = formatImprint(doc)
        val pages = doc.pages.trim()
        val hasContainer = listOf(bookAuthor, bookTitle, edition, imprint).any { it.isNotBlank() }

        return joinParts(
            formatAuthor(doc),
            title.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(),
            if (hasContainer) "In:" else "",
            bookAuthor.takeIf { it.isNotBlank() }?.let { "${it.uppercase()}." }.orEmpty(),
            bookTitle.takeIf { it.isNotBlank() }?.let { "**$it**." }.orEmpty(),
            edition,
            imprint,
            pages.takeIf { it.isNotBlank() }?.let { "p. $it." }.orEmpty()
        )
    }

    /** SOBRENOME, Nome. **Título**. Ano. [pages f. | journal - ] Editora, Cidade, Ano. */
    private fun formatTese(doc: DocumentEntity): String {
        val year = doc.year.trim()
        val pages = doc.pages.trim()
        val degree = doc.journal.trim()
        val institution = doc.publisher.trim()
        val city = doc.city.trim()

        val hasPlaceOrInst = institution.isNotBlank() || city.isNotBlank()
        val institutionTail = if (hasPlaceOrInst) {
            listOf(institution, city, year).filter { it.isNotBlank() }.joinToString(", ")
        } else {
            ""
        }

        val degreeAndInst = when {
            degree.isNotBlank() && institutionTail.isNotBlank() -> "$degree - $institutionTail."
            degree.isNotBlank() -> "$degree."
            institutionTail.isNotBlank() -> "Tese. $institutionTail."
            else -> ""
        }

        return joinParts(
            formatAuthor(doc),
            formatTitleBold(doc),
            year.takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty(),
            pages.takeIf { it.isNotBlank() }?.let { "$it f." }.orEmpty(),
            degreeAndInst
        )
    }

    /** JURISDIÇÃO_UPPER. **Título**, n. edition, Ano. Editora, Cidade. */
    private fun formatJuridico(doc: DocumentEntity): String {
        val jurisdiction = doc.authorLastName.trim()
        val title = doc.title.trim()
        val edition = doc.edition.trim()
        val year = doc.year.trim()
        val publisher = doc.publisher.trim()
        val city = doc.city.trim()

        val titleClause = if (title.isNotBlank()) {
            buildString {
                append("**$title**")
                if (edition.isNotBlank()) append(", n. $edition")
                if (year.isNotBlank()) append(", $year")
                append(".")
            }
        } else {
            listOfNotNull(
                edition.takeIf { it.isNotBlank() }?.let { "n. $it" },
                year.takeIf { it.isNotBlank() }
            ).joinToString(", ").takeIf { it.isNotBlank() }?.let { "$it." }.orEmpty()
        }

        val pubClause = listOf(publisher, city)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .takeIf { it.isNotBlank() }
            ?.let { "$it." }
            .orEmpty()

        return joinParts(
            jurisdiction.takeIf { it.isNotBlank() }?.let { "${it.uppercase()}." }.orEmpty(),
            titleClause,
            pubClause
        )
    }

    /** SOBRENOME, Nome. **Título**. Disponível em: URL. Acesso em: data. */
    private fun formatSite(doc: DocumentEntity): String = joinParts(
        formatAuthor(doc),
        formatTitleBold(doc),
        formatOnlineAddon(doc)
    )

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun joinParts(vararg parts: String): String =
        parts.filter { it.isNotBlank() }.joinToString(" ")

    private fun formatAuthor(doc: DocumentEntity): String {
        val last = doc.authorLastName.trim()
        if (last.isBlank()) return ""
        val first = doc.authorFirstName.trim()
        return buildString {
            append(last.uppercase())
            if (first.isNotBlank()) append(", $first")
            append(".")
        }
    }

    private fun formatTitleBold(doc: DocumentEntity): String {
        val title = doc.title.trim()
        val subtitle = doc.subtitle.trim()
        return when {
            title.isNotBlank() && subtitle.isNotBlank() -> "**$title**: $subtitle."
            title.isNotBlank() -> "**$title**."
            subtitle.isNotBlank() -> "**$subtitle**."
            else -> ""
        }
    }

    private fun formatTitlePlain(doc: DocumentEntity): String {
        val title = doc.title.trim()
        val subtitle = doc.subtitle.trim()
        return when {
            title.isNotBlank() && subtitle.isNotBlank() -> "$title: $subtitle."
            title.isNotBlank() -> "$title."
            subtitle.isNotBlank() -> "$subtitle."
            else -> ""
        }
    }

    private fun formatEdition(doc: DocumentEntity): String {
        val edition = doc.edition.trim()
        return if (edition.isNotBlank()) "$edition. ed." else ""
    }

    private fun formatImprint(doc: DocumentEntity): String {
        val city = doc.city.trim()
        val publisher = doc.publisher.trim()
        val year = doc.year.trim()
        val placeAndPublisher = when {
            city.isNotBlank() && publisher.isNotBlank() -> "$city: $publisher"
            city.isNotBlank() -> city
            publisher.isNotBlank() -> publisher
            else -> ""
        }
        val combined = listOf(placeAndPublisher, year)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        return if (combined.isNotBlank()) "$combined." else ""
    }

    private fun formatOnlineAddon(doc: DocumentEntity): String {
        val url = doc.url.trim()
        val accessDate = doc.accessDate.trim()
        return joinParts(
            url.takeIf { it.isNotBlank() }?.let { "Disponível em: $it." }.orEmpty(),
            accessDate.takeIf { it.isNotBlank() }?.let { "Acesso em: $it." }.orEmpty()
        )
    }
}
