package br.com.jotdown.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.entity.HighlightEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportUtilTest {

    private fun makeDoc(
        fileName: String = "documento.pdf",
        title: String = "Arquitetura Limpa",
        docType: String = "livro",
        authorLastName: String = "Martin",
        authorFirstName: String = "Robert",
        publisher: String = "Alta Books",
        city: String = "Rio de Janeiro",
        year: String = "2019",
        labels: String = "engenharia, software",
    ) = DocumentEntity(
        id = "doc-1",
        fileName = fileName,
        title = title,
        dateAdded = 0L,
        pdfFilePath = "/tmp/$fileName",
        docType = docType,
        authorLastName = authorLastName,
        authorFirstName = authorFirstName,
        publisher = publisher,
        city = city,
        year = year,
        labels = labels,
    )

    // ── toMarkdown ───────────────────────────────────────────────────────────

    @Test
    fun toMarkdown_includesTitleHeaderMetadataAndCitationBlocks() {
        val doc = makeDoc()
        val md = ExportUtil.toMarkdown(doc, emptyList(), emptyList())

        assertTrue(md.startsWith("# Arquitetura Limpa\n"))
        assertTrue(md.contains("## Metadados"))
        assertTrue(md.contains("- **Autor:** Martin, Robert"))
        assertTrue(md.contains("- **Ano:** 2019"))
        assertTrue(md.contains("- **Editora:** Alta Books"))
        assertTrue(md.contains("- **Cidade:** Rio de Janeiro"))
        assertTrue(md.contains("- **Rótulos:** engenharia, software"))
        assertTrue(md.contains("- **Exportado:** "))
        assertTrue(md.contains(" via Jotdown"))

        assertTrue(md.contains("## Referência ABNT\n\n> ${AbntUtil.format(doc)}"))
        assertTrue(md.contains("## Referência APA (7ª ed.)\n\n> ${CitationUtil.formatApa(doc)}"))
        assertFalse(md.contains("## 📌 Fichamentos"))
        assertFalse(md.contains("## \uD83D\uDCDD Anotações (Categorizadas)"))
    }

    @Test
    fun toMarkdown_blankTitle_fallsBackToFileNameAndOmitsBlankMetadata() {
        val doc = makeDoc(
            fileName = "leitura_avulsa.pdf",
            title = "",
            authorLastName = "",
            authorFirstName = "",
            publisher = "",
            city = "",
            year = "",
            labels = "",
        )
        val md = ExportUtil.toMarkdown(doc, emptyList(), emptyList())

        assertTrue(md.startsWith("# leitura_avulsa.pdf\n"))
        assertFalse(md.contains("- **Autor:**"))
        assertFalse(md.contains("- **Ano:**"))
        assertFalse(md.contains("- **Editora:**"))
        assertFalse(md.contains("- **Cidade:**"))
        assertFalse(md.contains("- **Rótulos:**"))
    }

    @Test
    fun toMarkdown_highlights_sortedByPageAndBlankTextsFiltered() {
        val doc = makeDoc()
        val highlights = listOf(
            HighlightEntity(id = 1, documentId = "doc-1", page = 12, text = "  Terceiro trecho  "),
            HighlightEntity(id = 2, documentId = "doc-1", page = 3, text = "Primeiro trecho"),
            HighlightEntity(id = 3, documentId = "doc-1", page = 7, text = "   "),
            HighlightEntity(id = 4, documentId = "doc-1", page = 3, text = "Segundo trecho na p3"),
        )

        val md = ExportUtil.toMarkdown(doc, emptyList(), highlights)

        assertTrue(md.contains("## 📌 Fichamentos"))
        val p3Idx = md.indexOf("### Página 3")
        val firstQuoteIdx = md.indexOf("> Primeiro trecho")
        val secondQuoteIdx = md.indexOf("> Segundo trecho na p3")
        val p12Idx = md.indexOf("### Página 12")
        val thirdQuoteIdx = md.indexOf("> Terceiro trecho")

        assertTrue(p3Idx in 0 until firstQuoteIdx)
        assertTrue(firstQuoteIdx < secondQuoteIdx)
        assertTrue(secondQuoteIdx < p12Idx)
        assertTrue(p12Idx < thirdQuoteIdx)
        assertEquals(1, Regex("### Página 3").findAll(md).count())
        assertFalse(md.contains("### Página 7"))
    }

    @Test
    fun toMarkdown_annotations_categorizedByColorAndSortedByPage() {
        val doc = makeDoc()
        val annotations = listOf(
            AnnotationEntity(
                id = 1,
                documentId = "doc-1",
                page = 9,
                x = 0.1f,
                y = 0.2f,
                text = "Revisar prova",
                color = Color.Red.toArgb().toString(),
            ),
            AnnotationEntity(
                id = 2,
                documentId = "doc-1",
                page = 2,
                x = 0.1f,
                y = 0.2f,
                text = "Erro de premissa",
                color = Color.Red.toArgb().toString(),
            ),
            AnnotationEntity(
                id = 3,
                documentId = "doc-1",
                page = 4,
                x = 0.2f,
                y = 0.3f,
                text = "Esclarecer conceito",
                color = Color(0xFFEAB308).toArgb().toString(),
            ),
            AnnotationEntity(
                id = 4,
                documentId = "doc-1",
                page = 5,
                x = 0.3f,
                y = 0.4f,
                text = "Ver Dijkstra 1968",
                color = Color.Blue.toArgb().toString(),
            ),
            AnnotationEntity(
                id = 5,
                documentId = "doc-1",
                page = 6,
                x = 0.4f,
                y = 0.5f,
                text = "Citação-chave do capítulo",
                color = Color(0xFF22C55E).toArgb().toString(),
            ),
            AnnotationEntity(
                id = 6,
                documentId = "doc-1",
                page = 8,
                x = 0.5f,
                y = 0.6f,
                text = "Nota geral com hex padrão",
                color = "#FDE047",
            ),
            AnnotationEntity(
                id = 7,
                documentId = "doc-1",
                page = 10,
                x = 0.5f,
                y = 0.6f,
                text = "   ",
                color = Color.Red.toArgb().toString(),
            ),
        )

        val md = ExportUtil.toMarkdown(doc, annotations, emptyList())

        assertTrue(md.contains("## \uD83D\uDCDD Anotações (Categorizadas)"))
        assertTrue(md.contains("### Crítico / Revisar"))
        assertTrue(md.contains("### Dúvidas / Detalhes"))
        assertTrue(md.contains("### Referências / Autores"))
        assertTrue(md.contains("### Citações Importantes"))
        assertTrue(md.contains("### Geral / Outros"))

        val p2Idx = md.indexOf("- **p. 2** — Erro de premissa")
        val p9Idx = md.indexOf("- **p. 9** — Revisar prova")
        assertTrue(p2Idx in 0 until p9Idx)

        assertTrue(md.contains("- **p. 4** — Esclarecer conceito"))
        assertTrue(md.contains("- **p. 5** — Ver Dijkstra 1968"))
        assertTrue(md.contains("- **p. 6** — Citação-chave do capítulo"))
        assertTrue(md.contains("- **p. 8** — Nota geral com hex padrão"))
        assertFalse(md.contains("**p. 10**"))
    }

    // ── suggestedFileName & suggestedBibFileName ─────────────────────────────

    @Test
    fun suggestedFileNames_sanitizeSpecialCharsAndSpaces() {
        val doc = makeDoc(
            authorLastName = "Silva Neto",
            title = "Código Limpo: Guia / Prático!",
            year = "2023",
        )

        assertEquals(
            "Silva_Neto_Código_Limpo_Guia__Prático_2023.md",
            ExportUtil.suggestedFileName(doc)
        )
        assertEquals(
            "Silva_Neto_Código_Limpo_Guia__Prático_2023.bib",
            ExportUtil.suggestedBibFileName(doc)
        )
    }

    @Test
    fun suggestedFileNames_blankAuthorAndYear_useJotdownFallbackWithoutTrailingUnderscore() {
        val doc = makeDoc(
            authorLastName = "",
            title = "Resumo Estudo",
            year = "",
        )

        assertEquals("Jotdown_Resumo_Estudo.md", ExportUtil.suggestedFileName(doc))
        assertEquals("Jotdown_Resumo_Estudo.bib", ExportUtil.suggestedBibFileName(doc))
    }

    @Test
    fun suggestedFileNames_longAuthorAndTitle_truncateToBounds() {
        val doc = makeDoc(
            authorLastName = "SobrenomeMuitoLongoDemaisParaCabimento",
            title = "TituloExtremamenteLongoQueUltrapassaTrintaCaracteres",
            year = "2025",
        )

        val mdName = ExportUtil.suggestedFileName(doc)
        val bibName = ExportUtil.suggestedBibFileName(doc)

        assertEquals("SobrenomeMuitoLongoD_TituloExtremamenteLongoQueUltr_2025.md", mdName)
        assertEquals("SobrenomeMuitoLongoD_TituloExtremamenteLongoQueUltr_2025.bib", bibName)
    }
}
