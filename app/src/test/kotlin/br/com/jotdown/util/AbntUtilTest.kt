package br.com.jotdown.util

import br.com.jotdown.data.entity.DocumentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbntUtilTest {

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
    )

    private fun assertNoEmptyArtifacts(formatted: String) {
        val forbidden = listOf(
            "..",
            ",.",
            ":.",
            ", ,",
            ",,",
            "  ",
            "v. ,",
            "v. .",
            "n. ,",
            "n. .",
            "p. ,",
            "p. .",
            "In: .",
            "Disponível em: .",
            "Acesso em: .",
            "****",
        )
        forbidden.forEach { artifact ->
            assertFalse("Unexpected artifact '$artifact' in: $formatted", formatted.contains(artifact))
        }
    }

    // ── Livro / Monografia (default) ─────────────────────────────────────────

    @Test
    fun format_livroFull_formatsAllFieldsAndNormalizesDocType() {
        val base = makeDoc(
            docType = "livro",
            authorLastName = "Martin",
            authorFirstName = "Robert Cecil",
            title = "Arquitetura Limpa",
            subtitle = "o guia do artesão",
            edition = "2",
            city = "Rio de Janeiro",
            publisher = "Alta Books",
            year = "2019",
        )
        val result = AbntUtil.format(base)
        val fromUiLabel = AbntUtil.format(base.copy(docType = "  Livro / Monografia  "))
        val fromFallback = AbntUtil.format(base.copy(docType = "outro"))

        assertEquals(result, fromUiLabel)
        assertEquals(result, fromFallback)
        assertTrue(result.startsWith("MARTIN, Robert Cecil. **Arquitetura Limpa**: o guia do artesão."))
        assertTrue(result.contains("2"))
        assertTrue(result.contains("ed."))
        assertTrue(result.contains("Rio de Janeiro:"))
        assertTrue(result.contains("Alta Books"))
        assertTrue(result.contains("2019."))
        assertNoEmptyArtifacts(result)
    }

    @Test
    fun format_livroWithBlankOptionalFields_avoidsSeparatorArtifacts() {
        val onlyTitle = AbntUtil.format(makeDoc(docType = "livro", title = "Livro Sem Metadados"))
        assertEquals("**Livro Sem Metadados**.", onlyTitle)
        assertNoEmptyArtifacts(onlyTitle)

        val onlyAuthorLastName = AbntUtil.format(
            makeDoc(docType = "livro", authorLastName = "Silva", title = "Manual")
        )
        assertTrue(onlyAuthorLastName.startsWith("SILVA. **Manual**."))
        assertNoEmptyArtifacts(onlyAuthorLastName)

        val cityWithoutPublisherOrYear = AbntUtil.format(
            makeDoc(docType = "livro", authorLastName = "Silva", title = "Manual", city = "São Paulo")
        )
        assertTrue(cityWithoutPublisherOrYear.contains("São Paulo"))
        assertNoEmptyArtifacts(cityWithoutPublisherOrYear)

        val publisherWithoutYear = AbntUtil.format(
            makeDoc(docType = "livro", authorLastName = "Silva", title = "Manual", publisher = "Atlas")
        )
        assertTrue(publisherWithoutYear.contains("Atlas"))
        assertNoEmptyArtifacts(publisherWithoutYear)
    }

    // ── Artigo de Periódico ──────────────────────────────────────────────────

    @Test
    fun format_artigoAliases_produceSameFormattedCitation() {
        val artigo = makeDoc(
            docType = "artigo",
            authorLastName = "Vaswani",
            authorFirstName = "Ashish",
            title = "Attention Is All You Need",
            journal = "Advances in Neural Information Processing Systems",
            city = "Long Beach",
            volume = "30",
            edition = "1",
            pages = "5998-6008",
            year = "2017",
        )
        val expected = AbntUtil.format(artigo)

        assertEquals(expected, AbntUtil.format(artigo.copy(docType = "Artigo de Periódico")))
        assertEquals(expected, AbntUtil.format(artigo.copy(docType = "  artigo de periodico  ")))

        assertTrue(expected.startsWith("VASWANI, Ashish. Attention Is All You Need."))
        assertFalse(expected.contains("**Attention Is All You Need**"))
        assertTrue(expected.contains("**Advances in Neural Information Processing Systems**"))
        assertTrue(expected.contains("Long Beach"))
        assertTrue(expected.contains("v. 30,"))
        assertTrue(expected.contains("n. 1,"))
        assertTrue(expected.contains("p. 5998-6008,"))
        assertTrue(expected.contains("2017."))
        assertNoEmptyArtifacts(expected)
    }

    @Test
    fun format_artigoWithBlankFields_omitsEmptyPrefixesAndSeparators() {
        val minimal = AbntUtil.format(
            makeDoc(
                docType = "Artigo de Periódico",
                authorLastName = "Costa",
                authorFirstName = "Mariana",
                title = "Artigo Enxuto",
                journal = "Revista Ciência",
            )
        )
        assertTrue(minimal.startsWith("COSTA, Mariana. Artigo Enxuto."))
        assertTrue(minimal.contains("**Revista Ciência**"))
        assertFalse(minimal.contains("v. "))
        assertFalse(minimal.contains("n. "))
        assertFalse(minimal.contains("p. "))
        assertTrue(minimal.endsWith("."))
        assertNoEmptyArtifacts(minimal)

        val partial = AbntUtil.format(
            makeDoc(
                docType = "artigo",
                authorLastName = "Costa",
                title = "Artigo Parcial",
                journal = "Revista Ciência",
                volume = "12",
                year = "2024",
            )
        )
        assertTrue(partial.contains("v. 12,"))
        assertTrue(partial.contains("2024."))
        assertFalse(partial.contains("n. "))
        assertFalse(partial.contains("p. "))
        assertNoEmptyArtifacts(partial)
    }

    // ── Capítulo de Livro ────────────────────────────────────────────────────

    @Test
    fun format_capituloDeLivro_formatsInAndUppercaseBookOrganizer() {
        val doc = makeDoc(
            docType = "Capítulo de Livro",
            authorLastName = "Eco",
            authorFirstName = "Umberto",
            title = "Sobre a leitura",
            subtitle = "Rorty, Richard",
            journal = "Interpretação e superinterpretação",
            edition = "2",
            city = "São Paulo",
            publisher = "Martins Fontes",
            year = "2005",
            pages = "23-45",
        )
        val result = AbntUtil.format(doc)
        val unaccented = AbntUtil.format(doc.copy(docType = "  capitulo de livro "))

        assertEquals(result, unaccented)
        assertTrue(result.startsWith("ECO, Umberto. Sobre a leitura."))
        assertTrue(result.contains("In: RORTY, RICHARD."))
        assertTrue(result.contains("**Interpretação e superinterpretação**."))
        assertTrue(result.contains("2"))
        assertTrue(result.contains("ed."))
        assertTrue(result.contains("São Paulo: Martins Fontes, 2005."))
        assertTrue(result.contains("p. 23-45."))
        assertNoEmptyArtifacts(result)
    }

    @Test
    fun format_capituloWithBlankOptionalFields_avoidsSeparatorArtifacts() {
        val result = AbntUtil.format(
            makeDoc(
                docType = "capitulo de livro",
                authorLastName = "Eco",
                title = "Capítulo Isolado",
                journal = "Coletânea",
            )
        )
        assertTrue(result.startsWith("ECO. Capítulo Isolado."))
        assertTrue(result.contains("**Coletânea**"))
        assertFalse(result.contains("In: ."))
        assertFalse(result.contains("p. ."))
        assertNoEmptyArtifacts(result)
    }

    // ── Tese / Trabalho Acadêmico ────────────────────────────────────────────

    @Test
    fun format_teseAliases_formatsAuthorAndBoldTitle() {
        val tese = makeDoc(
            docType = "tese",
            authorLastName = "Oliveira",
            authorFirstName = "Ana Paula",
            title = "Sistemas Distribuídos Resilientes",
            journal = "Dissertação (Mestrado)",
            publisher = "Universidade de São Paulo",
            city = "São Paulo",
            year = "2020",
            pages = "120",
        )
        val expected = AbntUtil.format(tese)

        assertEquals(expected, AbntUtil.format(tese.copy(docType = "Trabalho Acadêmico")))
        assertEquals(expected, AbntUtil.format(tese.copy(docType = "  trabalho academico  ")))
        assertTrue(expected.startsWith("OLIVEIRA, Ana Paula. **Sistemas Distribuídos Resilientes**."))
        assertTrue(expected.contains("Universidade de São Paulo"))
        assertTrue(expected.contains("São Paulo"))
        assertTrue(expected.contains("2020"))
        assertNoEmptyArtifacts(expected)
    }

    @Test
    fun format_teseWithBlankOptionalFields_avoidsSeparatorArtifacts() {
        val result = AbntUtil.format(
            makeDoc(
                docType = "Trabalho Acadêmico",
                authorLastName = "Oliveira",
                title = "Monografia Sem Dados Extras",
            )
        )
        assertTrue(result.startsWith("OLIVEIRA. **Monografia Sem Dados Extras**."))
        assertNoEmptyArtifacts(result)
    }

    // ── Documento Jurídico ───────────────────────────────────────────────────

    @Test
    fun format_documentoJuridicoAliases_uppercasesJurisdictionWithoutComma() {
        val doc = makeDoc(
            docType = "Documento Jurídico",
            authorLastName = "Brasil",
            title = "Lei nº 13.709, de 14 de agosto de 2018",
            edition = "13.709",
            year = "2018",
            publisher = "Diário Oficial da União",
            city = "Brasília",
        )
        val result = AbntUtil.format(doc)
        val unaccented = AbntUtil.format(doc.copy(docType = "  documento juridico "))

        assertEquals(result, unaccented)
        assertTrue(result.startsWith("BRASIL. **Lei nº 13.709, de 14 de agosto de 2018**"))
        assertFalse(result.startsWith("BRASIL, "))
        assertTrue(result.contains("2018"))
        assertTrue(result.contains("Diário Oficial da União"))
        assertTrue(result.contains("Brasília"))
        assertNoEmptyArtifacts(result)
    }

    @Test
    fun format_documentoJuridicoWithBlankOptionalFields_avoidsSeparatorArtifacts() {
        val result = AbntUtil.format(
            makeDoc(
                docType = "documento juridico",
                authorLastName = "São Paulo",
                title = "Decreto Estadual 100",
            )
        )
        assertTrue(result.startsWith("SÃO PAULO. **Decreto Estadual 100**"))
        assertNoEmptyArtifacts(result)
    }

    // ── Site & Online Addon ──────────────────────────────────────────────────

    @Test
    fun format_site_formatsUrlAndAccessDateWithoutDuplication() {
        val fullSite = AbntUtil.format(
            makeDoc(
                docType = "  Site ",
                authorLastName = "Mozilla",
                title = "MDN Web Docs",
                url = "https://developer.mozilla.org",
                accessDate = "10 nov. 2024",
            )
        )
        assertTrue(fullSite.startsWith("MOZILLA. **MDN Web Docs**."))
        assertTrue(fullSite.contains("Disponível em: https://developer.mozilla.org."))
        assertTrue(fullSite.contains("Acesso em: 10 nov. 2024."))
        assertEquals(1, Regex("Disponível em:").findAll(fullSite).count())
        assertNoEmptyArtifacts(fullSite)

        val siteWithoutAccessDate = AbntUtil.format(
            makeDoc(
                docType = "site",
                title = "Página Sem Data",
                url = "https://example.org",
            )
        )
        assertTrue(siteWithoutAccessDate.contains("Disponível em: https://example.org."))
        assertFalse(siteWithoutAccessDate.contains("Acesso em:"))
        assertNoEmptyArtifacts(siteWithoutAccessDate)

        val siteWithoutUrlOrDate = AbntUtil.format(
            makeDoc(docType = "site", title = "Página Offline")
        )
        assertFalse(siteWithoutUrlOrDate.contains("Disponível em:"))
        assertFalse(siteWithoutUrlOrDate.contains("Acesso em:"))
        assertNoEmptyArtifacts(siteWithoutUrlOrDate)
    }

    @Test
    fun format_nonSiteDocTypes_appendOnlineAddonOnlyWhenUrlPresent() {
        val nonSiteTypes = listOf(
            "livro",
            "Artigo de Periódico",
            "Capítulo de Livro",
            "Trabalho Acadêmico",
            "Documento Jurídico",
        )

        nonSiteTypes.forEach { type ->
            val withUrlAndDate = AbntUtil.format(
                makeDoc(
                    docType = type,
                    authorLastName = "Autor",
                    authorFirstName = "Teste",
                    title = "Obra Digital",
                    journal = "Fonte",
                    publisher = "Editora",
                    year = "2024",
                    url = "https://doi.org/10.1000/xyz",
                    accessDate = "15 mar. 2025",
                )
            )
            assertTrue(
                "Expected Disponível em for $type: $withUrlAndDate",
                withUrlAndDate.endsWith("Disponível em: https://doi.org/10.1000/xyz. Acesso em: 15 mar. 2025.")
            )
            assertNoEmptyArtifacts(withUrlAndDate)

            val withUrlOnly = AbntUtil.format(
                makeDoc(
                    docType = type,
                    authorLastName = "Autor",
                    title = "Obra Digital",
                    url = "https://doi.org/10.1000/xyz",
                    accessDate = "",
                )
            )
            assertTrue(
                "Expected URL without Acesso em for $type: $withUrlOnly",
                withUrlOnly.endsWith("Disponível em: https://doi.org/10.1000/xyz.")
            )
            assertFalse("Unexpected Acesso em for $type: $withUrlOnly", withUrlOnly.contains("Acesso em:"))
            assertNoEmptyArtifacts(withUrlOnly)

            val withoutUrl = AbntUtil.format(
                makeDoc(
                    docType = type,
                    authorLastName = "Autor",
                    title = "Obra Impressa",
                    url = "",
                    accessDate = "15 mar. 2025",
                )
            )
            assertFalse("Unexpected Disponível em for $type: $withoutUrl", withoutUrl.contains("Disponível em:"))
            assertFalse("Unexpected Acesso em for $type: $withoutUrl", withoutUrl.contains("Acesso em:"))
            assertNoEmptyArtifacts(withoutUrl)
        }
    }
}
