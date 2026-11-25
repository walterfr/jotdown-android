package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageTemplateUtilTest {

    @Test
    fun computeTemplatePageSize_handlesRotationsAndInvalidFallback() {
        assertEquals(595f to 842f, PdfPageTemplateUtil.computeTemplatePageSize(0f, 800f, 0))
        assertEquals(595f to 842f, PdfPageTemplateUtil.computeTemplatePageSize(600f, -10f, 90))
        assertEquals(612f to 792f, PdfPageTemplateUtil.computeTemplatePageSize(612f, 792f, 0))
        assertEquals(792f to 612f, PdfPageTemplateUtil.computeTemplatePageSize(612f, 792f, 90))
        assertEquals(612f to 792f, PdfPageTemplateUtil.computeTemplatePageSize(612f, 792f, 180))
        assertEquals(792f to 612f, PdfPageTemplateUtil.computeTemplatePageSize(612f, 792f, 270))
        assertEquals(792f to 612f, PdfPageTemplateUtil.computeTemplatePageSize(612f, 792f, -90))
    }

    @Test
    fun buildTemplateGeometry_generatesBoundedShapesForAllFiveTemplates() {
        val w = 595f
        val h = 842f

        val blank = PdfPageTemplateUtil.buildTemplateGeometry(PaperTemplate.BLANK, w, h)
        assertTrue(blank.lines.isEmpty())
        assertTrue(blank.dots.isEmpty())

        val lined = PdfPageTemplateUtil.buildTemplateGeometry(PaperTemplate.LINED, w, h)
        assertTrue(lined.lines.size > 15)
        assertTrue(lined.lines.any { it.x1 == it.x2 }) // vertical margin line
        assertTrue(lined.lines.any { it.y1 == it.y2 }) // horizontal ruled lines
        assertTrue(lined.dots.isEmpty())
        assertBounds(lined, w, h)

        val grid = PdfPageTemplateUtil.buildTemplateGeometry(PaperTemplate.GRID, w, h)
        assertTrue(grid.lines.count { it.x1 == it.x2 } >= 25)
        assertTrue(grid.lines.count { it.y1 == it.y2 } >= 35)
        assertTrue(grid.dots.isEmpty())
        assertBounds(grid, w, h)

        val dotted = PdfPageTemplateUtil.buildTemplateGeometry(PaperTemplate.DOTTED, w, h)
        assertTrue(dotted.lines.isEmpty())
        assertTrue(dotted.dots.size > 500)
        assertBounds(dotted, w, h)

        val cornell = PdfPageTemplateUtil.buildTemplateGeometry(PaperTemplate.CORNELL, w, h)
        assertTrue(cornell.lines.any { it.x1 == it.x2 }) // cue column divider
        assertTrue(cornell.lines.count { it.y1 == it.y2 } > 10)
        assertTrue(cornell.dots.isEmpty())
        assertBounds(cornell, w, h)

        val invalid = PdfPageTemplateUtil.buildTemplateGeometry(PaperTemplate.GRID, 0f, 842f)
        assertTrue(invalid.lines.isEmpty() && invalid.dots.isEmpty())
    }

    private fun assertBounds(geom: TemplateGeometry, w: Float, h: Float) {
        for (line in geom.lines) {
            assertTrue(line.x1 in 0f..w && line.x2 in 0f..w)
            assertTrue(line.y1 in 0f..h && line.y2 in 0f..h)
            assertTrue(line.width > 0f)
        }
        for (dot in geom.dots) {
            assertTrue(dot.x in 0f..w && dot.y in 0f..h)
            assertTrue(dot.radius > 0f)
        }
    }
}
