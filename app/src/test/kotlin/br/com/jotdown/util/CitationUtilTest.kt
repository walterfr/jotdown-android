package br.com.jotdown.util

import br.com.jotdown.data.entity.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationUtilTest {

    private fun makeDoc(
        docType: String = "livro",
        authorLastName: String = "",
        authorFirstName: String = "",
        title: String = "Título Padrão",
        subtitle: String = "",
        edition: String = "",
        city: String = "",
        publisher: String = "",
        year: String = "",
        journal: String = "",
        volume: String = "",
        pages: String = "",
        url: String = "",
        accessDate: String = "",
        doi: String = "",
        isbn: String = "",
    ) = DocumentEntity(
        id = "doc-1",
        fileName = "arquivo.pdf",
        title = title,
        dateAdded = 0L,
        pdfFilePath = "/tmp/arquivo.pdf",
        docType = docType,
        authorLastName = authorLastName,
        authorFirstName = authorFirstName,
        subtitle = subtitle,
        edition = edition,
        city = city,
        publisher = publisher,
        year = year,
        journal = journal,
        volume = volume,
        pages = pages,
        url = url,
        accessDate = accessDate,
        doi = doi,
        isbn = isbn,
    )

    // ── formatApa ────────────────────────────────────────────────────────────

    @Test
    fun formatApa_multiWordFirstName_formatsAllInitials() {
        val doc = makeDoc(
            docType = "livro",
            authorLastName = "Martin",
            authorFirstName = "Robert Cecil",
            year = "2008",
            title = "Clean Code",
            publisher = "Prentice Hall",
        )
        assertEquals(
            "Martin, R. C. (2008). *Clean Code*. Prentice Hall.",
            CitationUtil.formatApa(doc)
        )
    }

    @Test
    fun formatApa_blankYear_usesSemDataPlaceholder() {
        val doc = makeDoc(
            docType = "livro",
            authorLastName = "Silva",
            authorFirstName = "Ana Paula",
            year = "   ",
            title = "Notas de Aula",
            publisher = "Editora Acadêmica",
        )
        assertEquals(
            "Silva, A. P. (s.d.). *Notas de Aula*. Editora Acadêmica.",
            CitationUtil.formatApa(doc)
        )
    }

    @Test
    fun formatApa_bookWithSubtitleAndEdition_formatsExpectedApa() {
        val doc = makeDoc(
            docType = "Livro / Monografia",
            authorLastName = "Cormen",
            authorFirstName = "Thomas H.",
            year = "2009",
            title = "Introduction to Algorithms",
            subtitle = "Foundations",
            edition = "3",
            publisher = "MIT Press",
        )
        assertEquals(
            "Cormen, T. H. (2009). *Introduction to Algorithms*: Foundations (3 ed.). MIT Press.",
            CitationUtil.formatApa(doc)
        )
    }

    @Test
    fun formatApa_artigoDePeriodico_formatsJournalVolumeIssuePages() {
        val doc = makeDoc(
            docType = "Artigo de Periódico",
            authorLastName = "Vaswani",
            authorFirstName = "Ashish",
            year = "2017",
            title = "Attention Is All You Need",
            journal = "NeurIPS",
            volume = "30",
            edition = "1",
            pages = "5998-6008",
        )
        assertEquals(
            "Vaswani, A. (2017). Attention Is All You Need. *NeurIPS*, *30*(1), 5998-6008.",
            CitationUtil.formatApa(doc)
        )
    }

    @Test
    fun formatApa_capituloDeLivro_formatsInBookPagesPublisher() {
        val doc = makeDoc(
            docType = "Capítulo de Livro",
            authorLastName = "Eco",
            authorFirstName = "Umberto",
            year = "1990",
            title = "A interpretação",
            subtitle = "R. Rorty (Org.),",
            journal = "Interpretação e superinterpretação",
            pages = "23-45",
            publisher = "Martins Fontes",
        )
        assertEquals(
            "Eco, U. (1990). A interpretação. In R. Rorty (Org.), *Interpretação e superinterpretação* (pp. 23-45). Martins Fontes.",
            CitationUtil.formatApa(doc)
        )
    }

    @Test
    fun formatApa_trabalhoAcademico_formatsInstitutionWithJournalFallback() {
        val withPublisher = makeDoc(
            docType = "Trabalho Acadêmico",
            authorLastName = "Souza",
            authorFirstName = "Carla",
            year = "2022",
            title = "Aprendizado de Máquina",
            publisher = "USP",
        )
        assertEquals(
            "Souza, C. (2022). *Aprendizado de Máquina* [Tese acadêmica, USP].",
            CitationUtil.formatApa(withPublisher)
        )

        val withJournalFallback = withPublisher.copy(publisher = "", journal = "Unicamp")
        assertEquals(
            "Souza, C. (2022). *Aprendizado de Máquina* [Tese acadêmica, Unicamp].",
            CitationUtil.formatApa(withJournalFallback)
        )

        val withoutInstitution = withPublisher.copy(publisher = "", journal = "")
        assertEquals(
            "Souza, C. (2022). *Aprendizado de Máquina* [Tese acadêmica].",
            CitationUtil.formatApa(withoutInstitution)
        )
    }

    @Test
    fun formatApa_site_formatsTitleAndUrl() {
        val doc = makeDoc(
            docType = "site",
            authorLastName = "Mozilla",
            year = "2024",
            title = "MDN Web Docs",
            url = "https://developer.mozilla.org",
        )
        assertEquals(
            "Mozilla (2024). *MDN Web Docs*. https://developer.mozilla.org",
            CitationUtil.formatApa(doc)
        )
    }

    // ── toBibtex ─────────────────────────────────────────────────────────────

    @Test
    fun toBibtex_book_generatesValidBookEntryWithDoiAndIsbn() {
        val doc = makeDoc(
            docType = "livro",
            authorLastName = "Martin",
            authorFirstName = "Robert Cecil",
            title = "Clean Code",
            subtitle = "A Handbook of Agile Software Craftsmanship",
            publisher = "Prentice Hall",
            city = "Upper Saddle River",
            edition = "1",
            year = "2008",
            doi = "10.5555/1388398",
            isbn = "978-0132350884",
            url = "https://example.com/clean-code",
        )
        val bib = CitationUtil.toBibtex(doc)

        assertTrue(bib.startsWith("@book{martin2008clean,"))
        assertTrue(bib.contains("  author = {Martin, Robert Cecil},"))
        assertTrue(bib.contains("  title = {Clean Code: A Handbook of Agile Software Craftsmanship},"))
        assertTrue(bib.contains("  year = {2008},"))
        assertTrue(bib.contains("  publisher = {Prentice Hall},"))
        assertTrue(bib.contains("  address = {Upper Saddle River},"))
        assertTrue(bib.contains("  edition = {1},"))
        assertTrue(bib.contains("  doi = {10.5555/1388398},"))
        assertTrue(bib.contains("  isbn = {978-0132350884},"))
        assertTrue(bib.contains("  url = {https://example.com/clean-code}\n}"))
        assertValidBibtexCommas(bib)
    }

    @Test
    fun toBibtex_article_generatesValidArticleEntry() {
        val doc = makeDoc(
            docType = "Artigo de Periódico",
            authorLastName = "Vaswani",
            authorFirstName = "Ashish",
            title = "Attention Is All You Need",
            journal = "NeurIPS",
            volume = "30",
            edition = "1",
            pages = "5998-6008",
            year = "2017",
            doi = "10.48550/arXiv.1706.03762",
        )
        val bib = CitationUtil.toBibtex(doc)

        assertTrue(bib.startsWith("@article{vaswani2017attentio,"))
        assertTrue(bib.contains("  journal = {NeurIPS},"))
        assertTrue(bib.contains("  volume = {30},"))
        assertTrue(bib.contains("  number = {1},"))
        assertTrue(bib.contains("  pages = {5998-6008},"))
        assertTrue(bib.contains("  doi = {10.48550/arXiv.1706.03762}\n}"))
        assertValidBibtexCommas(bib)
    }

    @Test
    fun toBibtex_phdthesis_generatesValidPhdThesisEntry() {
        val doc = makeDoc(
            docType = "Trabalho Acadêmico",
            authorLastName = "Souza",
            authorFirstName = "Carla",
            title = "Aprendizado Profundo",
            publisher = "Universidade de São Paulo",
            city = "São Paulo",
            year = "2022",
        )
        val bib = CitationUtil.toBibtex(doc)

        assertTrue(bib.startsWith("@phdthesis{souza2022aprendiz,"))
        assertTrue(bib.contains("  school = {Universidade de São Paulo},"))
        assertTrue(bib.contains("  address = {São Paulo}\n}"))
        assertValidBibtexCommas(bib)
    }

    @Test
    fun toBibtex_incollection_generatesValidIncollectionEntry() {
        val doc = makeDoc(
            docType = "Capítulo de Livro",
            authorLastName = "Eco",
            authorFirstName = "Umberto",
            title = "A interpretação",
            journal = "Interpretação e superinterpretação",
            publisher = "Martins Fontes",
            city = "São Paulo",
            pages = "23-45",
            year = "2005",
            isbn = "978-8533621114",
        )
        val bib = CitationUtil.toBibtex(doc)

        assertTrue(bib.startsWith("@incollection{eco2005a,"))
        assertTrue(bib.contains("  booktitle = {Interpretação e superinterpretação},"))
        assertTrue(bib.contains("  publisher = {Martins Fontes},"))
        assertTrue(bib.contains("  address = {São Paulo},"))
        assertTrue(bib.contains("  pages = {23-45},"))
        assertTrue(bib.contains("  isbn = {978-8533621114}\n}"))
        assertValidBibtexCommas(bib)
    }

    @Test
    fun toBibtex_site_generatesValidMiscEntryWithoutDuplicateUrlField() {
        val doc = makeDoc(
            docType = "site",
            authorLastName = "O'Connor",
            authorFirstName = "Liam",
            title = "Functional Notes",
            year = "2023",
            url = "https://example.org/notes",
            accessDate = "12 mai. 2024",
        )
        val bib = CitationUtil.toBibtex(doc)

        assertTrue(bib.startsWith("@misc{oconnor2023function,"))
        assertTrue(bib.contains("  howpublished = {\\url{https://example.org/notes}},"))
        assertTrue(bib.contains("  note = {Acesso em: 12 mai. 2024}\n}"))
        assertFalse(bib.contains("\n  url = "))
        assertValidBibtexCommas(bib)
    }

    @Test
    fun toBibtex_blankAuthor_usesDocFallbackInCiteKeyAndValidCommas() {
        val doc = makeDoc(
            docType = "livro",
            authorLastName = "",
            title = "Guia Rápido",
            year = "2021",
        )
        val bib = CitationUtil.toBibtex(doc)

        assertTrue(bib.startsWith("@book{doc2021guia,"))
        assertValidBibtexCommas(bib)
    }

    private fun assertValidBibtexCommas(bib: String) {
        val lines = bib.lines()
        assertTrue("Expected at least header, 1 field, and closing brace", lines.size >= 3)
        assertEquals("}", lines.last())

        val fieldLines = lines.subList(1, lines.size - 1)
        fieldLines.dropLast(1).forEach { line ->
            assertTrue("Expected trailing comma on field line: $line", line.endsWith(","))
        }
        assertFalse(
            "Last field line before '}' must not have trailing comma: ${fieldLines.last()}",
            fieldLines.last().endsWith(",")
        )
    }
}
