package br.com.jotdown.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfVectorExportUtilTest {

    @Test
    fun pagePaddingSpec_modesVisualScaleAndHexParsing() {
        val none = PagePaddingSpec.fromMode("NONE")
        assertFalse(none.hasPadding)
        assertEquals(1f, none.visualScale, 1e-4f)

        val right = PagePaddingSpec.fromMode("RIGHT")
        assertTrue(right.hasPadding)
        assertEquals(0.35f, right.extraRightFraction, 1e-4f)
        assertEquals(0f, right.extraBottomFraction, 1e-4f)
        assertEquals(1f / 1.35f, right.visualScale, 1e-4f)

        val bottom = PagePaddingSpec.fromMode("BOTTOM")
        assertEquals(0f, bottom.extraRightFraction, 1e-4f)
        assertEquals(0.25f, bottom.extraBottomFraction, 1e-4f)
        assertEquals(1f, bottom.visualScale, 1e-4f)

        val both = PagePaddingSpec.fromMode("BOTH")
        assertEquals(0.30f, both.extraRightFraction, 1e-4f)
        assertEquals(0.25f, both.extraBottomFraction, 1e-4f)
        assertEquals(1f / 1.30f, both.visualScale, 1e-4f)

        assertEquals(Triple(244, 236, 216), PagePaddingSpec.parseHexRgb("#F4ECD8"))
        assertEquals(Triple(253, 224, 71), PagePaddingSpec.parseHexRgb("#80FDE047"))
        assertEquals(Triple(1, 2, 3), PagePaddingSpec.parseHexRgb("invalid", Triple(1, 2, 3)))
    }

    @Test
    fun displayToPdfUserSpace_mapsAllFourRotationsCorrectly() {
        val box = PdfVectorExportUtil.PdfBox(10f, 20f, 110f, 220f) // w=100, h=200

        // Rotation 0: display (w=100, h=200)
        assertEquals(100f to 200f, PdfVectorExportUtil.displayDimensions(box, 0))
        assertEquals(10f to 220f, PdfVectorExportUtil.displayToPdfUserSpace(0f, 0f, box, 0))
        assertEquals(110f to 20f, PdfVectorExportUtil.displayToPdfUserSpace(100f, 200f, box, 0))

        // Rotation 90: display (w=200, h=100)
        assertEquals(200f to 100f, PdfVectorExportUtil.displayDimensions(box, 90))
        assertEquals(10f to 20f, PdfVectorExportUtil.displayToPdfUserSpace(0f, 0f, box, 90))
        assertEquals(10f to 220f, PdfVectorExportUtil.displayToPdfUserSpace(200f, 0f, box, 90))
        assertEquals(110f to 20f, PdfVectorExportUtil.displayToPdfUserSpace(0f, 100f, box, 90))

        // Rotation 180: display (w=100, h=200)
        assertEquals(100f to 200f, PdfVectorExportUtil.displayDimensions(box, 180))
        assertEquals(110f to 20f, PdfVectorExportUtil.displayToPdfUserSpace(0f, 0f, box, 180))
        assertEquals(10f to 220f, PdfVectorExportUtil.displayToPdfUserSpace(100f, 200f, box, 180))

        // Rotation 270 (and negative -90): display (w=200, h=100)
        assertEquals(200f to 100f, PdfVectorExportUtil.displayDimensions(box, -90))
        assertEquals(110f to 220f, PdfVectorExportUtil.displayToPdfUserSpace(0f, 0f, box, 270))
        assertEquals(10f to 20f, PdfVectorExportUtil.displayToPdfUserSpace(200f, 100f, box, 270))
    }

    @Test
    fun expandBoxForPadding_expandsInCorrectUserSpaceDirectionForAllRotations() {
        val box = PdfVectorExportUtil.PdfBox(0f, 0f, 100f, 200f)

        val r0 = PdfVectorExportUtil.expandBoxForPadding(box, 0, 30f, 50f)
        assertEquals(PdfVectorExportUtil.PdfBox(0f, -50f, 130f, 200f), r0)

        val r90 = PdfVectorExportUtil.expandBoxForPadding(box, 90, 30f, 50f)
        assertEquals(PdfVectorExportUtil.PdfBox(0f, 0f, 150f, 230f), r90)

        val r180 = PdfVectorExportUtil.expandBoxForPadding(box, 180, 30f, 50f)
        assertEquals(PdfVectorExportUtil.PdfBox(-30f, 0f, 100f, 250f), r180)

        val r270 = PdfVectorExportUtil.expandBoxForPadding(box, 270, 30f, 50f)
        assertEquals(PdfVectorExportUtil.PdfBox(-50f, -30f, 100f, 200f), r270)
    }

    @Test
    fun normRectToQuadPoints_matchesPdfOrderTopLeftTopRightBottomLeftBottomRight() {
        val box = PdfVectorExportUtil.PdfBox(0f, 0f, 100f, 200f)
        val quad = PdfVectorExportUtil.normRectToQuadPoints(
            NormRect(0.1f, 0.25f, 0.6f, 0.50f),
            box,
            0
        )
        // x: 10..60, y from top: 50..100 -> PDF y: 150..100
        assertArrayEquals(
            floatArrayOf(10f, 150f, 60f, 150f, 10f, 100f, 60f, 100f),
            quad,
            1e-3f
        )
    }

    @Test
    fun resolveVectorStrokes_replaysEraserChronologicallyAndPreservesLaterStrokes() {
        val stroke1 = PdfVectorExportUtil.VectorStroke(
            tool = "PEN",
            colorArgb = 0xFF000000.toInt(),
            widthMultiplier = 1f,
            points = listOf(Triple(0f, 0f, 1f), Triple(100f, 0f, 1f))
        )
        val stroke2 = PdfVectorExportUtil.VectorStroke(
            tool = "HIGHLIGHTER",
            colorArgb = 0xFFFDE047.toInt(),
            widthMultiplier = 1f,
            points = listOf(Triple(0f, 200f, 1f), Triple(100f, 200f, 1f))
        )
        val eraserHitStroke1 = PdfVectorExportUtil.VectorStroke(
            tool = "ERASER",
            colorArgb = 0,
            widthMultiplier = 1f,
            points = listOf(Triple(50f, -5f, 1f), Triple(50f, 5f, 1f))
        )
        val redrawnOverErasedSpot = PdfVectorExportUtil.VectorStroke(
            tool = "PENCIL",
            colorArgb = 0xFFEF4444.toInt(),
            widthMultiplier = 1f,
            points = listOf(Triple(10f, 0f, 1f), Triple(90f, 0f, 1f))
        )
        val ignoredLasso = PdfVectorExportUtil.VectorStroke(
            tool = "LASSO",
            colorArgb = 0,
            widthMultiplier = 1f,
            points = listOf(Triple(0f, 0f, 1f), Triple(10f, 10f, 1f))
        )

        val resolved = PdfVectorExportUtil.resolveVectorStrokes(
            listOf(stroke1, stroke2, eraserHitStroke1, redrawnOverErasedSpot, ignoredLasso)
        )

        assertEquals(2, resolved.size)
        assertEquals(stroke2, resolved[0])
        assertEquals(redrawnOverErasedSpot, resolved[1])
    }

    @Test
    fun resolveVectorStrokes_preservesAndErasesStudyTapeStrokes() {
        val tape = PdfVectorExportUtil.VectorStroke(
            tool = "TAPE",
            colorArgb = 0xFF1F2937.toInt(),
            widthMultiplier = 1f,
            points = listOf(Triple(0f, 100f, 1f), Triple(120f, 100f, 1f))
        )
        val keptTape = PdfVectorExportUtil.VectorStroke(
            tool = "TAPE",
            colorArgb = 0xFF2563EB.toInt(),
            widthMultiplier = 1f,
            points = listOf(Triple(0f, 300f, 1f), Triple(120f, 300f, 1f))
        )
        val eraser = PdfVectorExportUtil.VectorStroke(
            tool = "ERASER",
            colorArgb = 0,
            widthMultiplier = 1f,
            points = listOf(Triple(60f, 110f, 1f), Triple(60f, 90f, 1f))
        )

        val resolved = PdfVectorExportUtil.resolveVectorStrokes(listOf(tape, keptTape, eraser))
        assertEquals(1, resolved.size)
        assertEquals(keptTape, resolved[0])
    }
}
