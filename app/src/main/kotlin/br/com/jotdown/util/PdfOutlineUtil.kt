package br.com.jotdown.util

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfBookmark(
    val title: String,
    val page: Int,
    val children: List<PdfBookmark> = emptyList()
)

object PdfOutlineUtil {

    suspend fun getBookmarks(pdfFile: File): List<PdfBookmark> = withContext(Dispatchers.IO) {
        val bookmarks = mutableListOf<PdfBookmark>()
        var pdDoc: PDDocument? = null
        try {
            pdDoc = PDDocument.load(pdfFile)
            val outline = pdDoc.documentCatalog.documentOutline
            if (outline != null) {
                // PDFBox uses zero-indexed pages, but we need 1-indexed for the UI.
                val pages = pdDoc.documentCatalog.pages
                
                fun processItem(item: PDOutlineItem?): List<PdfBookmark> {
                    val result = mutableListOf<PdfBookmark>()
                    var current = item
                    while (current != null) {
                        var pageIndex = -1
                        try {
                            if (current.destination is PDPageDestination) {
                                pageIndex = pages.indexOf((current.destination as PDPageDestination).page)
                            } else if (current.action is PDActionGoTo) {
                                val dest = (current.action as PDActionGoTo).destination
                                if (dest is PDPageDestination) {
                                    pageIndex = pages.indexOf(dest.page)
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }

                        val title = current.title ?: "Sem Título"
                        val children = processItem(current.firstChild)
                        
                        // Adiciona ao sumário, mesmo se não conseguirmos extrair a página, para ter a estrutura
                        result.add(PdfBookmark(title = title, page = if (pageIndex >= 0) pageIndex + 1 else 0, children = children))
                        
                        current = current.nextSibling
                    }
                    return result
                }

                bookmarks.addAll(processItem(outline.firstChild))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdDoc?.close()
        }
        return@withContext bookmarks
    }
}
