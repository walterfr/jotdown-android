package br.com.jotdown.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfSearchResult(
    val page: Int, // 1-based index for UI
    val snippet: String
)

object PdfSearchUtil {

    suspend fun search(
        pdfFile: File,
        query: String,
        maxResults: Int = 60
    ): List<PdfSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 3 || !pdfFile.exists()) return@withContext emptyList()

        val results = mutableListOf<PdfSearchResult>()
        var pdDoc: PDDocument? = null
        try {
            pdDoc = PDDocument.load(pdfFile)
            val stripper = PDFTextStripper()
            val numPages = pdDoc.numberOfPages

            for (p in 1..numPages) {
                if (results.size >= maxResults) break
                stripper.startPage = p
                stripper.endPage = p
                val pageText = try {
                    stripper.getText(pdDoc)
                } catch (e: Exception) {
                    ""
                }
                if (pageText.isNotBlank()) {
                    var startIndex = 0
                    while (startIndex < pageText.length && results.size < maxResults) {
                        val foundIndex = pageText.indexOf(trimmed, startIndex, ignoreCase = true)
                        if (foundIndex == -1) break

                        val snippetStart = (foundIndex - 35).coerceAtLeast(0)
                        val snippetEnd = (foundIndex + trimmed.length + 35).coerceAtMost(pageText.length)
                        val rawSnippet = pageText.substring(snippetStart, snippetEnd).replace("[\r\n\t]+".toRegex(), " ").trim()
                        val snippet = "${if (snippetStart > 0) "…" else ""}$rawSnippet${if (snippetEnd < pageText.length) "…" else ""}"

                        results.add(PdfSearchResult(page = p, snippet = snippet))
                        startIndex = foundIndex + trimmed.length
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                pdDoc?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return@withContext results
    }
}
