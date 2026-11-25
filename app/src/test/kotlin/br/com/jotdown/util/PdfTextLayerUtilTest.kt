package br.com.jotdown.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfTextLayerUtilTest {

    @Test
    fun extractInRect_sortsLinesTopToBottomAndWordsLeftToRight() {
        val words = listOf(
            PdfWordBox("world", NormRect(0.25f, 0.10f, 0.35f, 0.14f)),
            PdfWordBox("second", NormRect(0.10f, 0.20f, 0.22f, 0.24f)),
            PdfWordBox("Hello", NormRect(0.10f, 0.10f, 0.22f, 0.14f)),
            PdfWordBox("line", NormRect(0.25f, 0.20f, 0.34f, 0.24f)),
            PdfWordBox("outside", NormRect(0.70f, 0.70f, 0.85f, 0.74f))
        )

        val result = PdfTextLayerUtil.extractInRect(
            words,
            NormRect(0.08f, 0.08f, 0.40f, 0.26f)
        )

        assertEquals("Hello world second line", result.text)
        assertEquals(2, result.lineRects.size)
        assertEquals(NormRect(0.10f, 0.10f, 0.35f, 0.14f), result.lineRects[0])
        assertEquals(NormRect(0.10f, 0.20f, 0.34f, 0.24f), result.lineRects[1])
    }

    @Test
    fun extractInRect_matchesByThirtyPercentOverlapEvenIfCenterOutside() {
        val word = PdfWordBox("partial", NormRect(0.10f, 0.10f, 0.30f, 0.20f))
        val included = PdfTextLayerUtil.extractInRect(
            listOf(word),
            NormRect(0.10f, 0.10f, 0.18f, 0.20f)
        )
        assertEquals("partial", included.text)

        val excluded = PdfTextLayerUtil.extractInRect(
            listOf(word),
            NormRect(0.10f, 0.10f, 0.14f, 0.20f)
        )
        assertEquals("", excluded.text)
        assertTrue(excluded.lineRects.isEmpty())
    }

    @Test
    fun mergeLineRects_splitsMultiColumnGutter() {
        val leftColA = NormRect(0.10f, 0.10f, 0.20f, 0.12f)
        val leftColB = NormRect(0.21f, 0.10f, 0.32f, 0.12f)
        val rightCol = NormRect(0.55f, 0.10f, 0.70f, 0.12f)

        val merged = PdfTextLayerUtil.mergeLineRects(listOf(rightCol, leftColB, leftColA))
        assertEquals(2, merged.size)
        assertEquals(NormRect(0.10f, 0.10f, 0.32f, 0.12f), merged[0])
        assertEquals(rightCol, merged[1])
    }

    @Test
    fun findMatchRects_caseInsensitiveMultiWordSpan() {
        val words = listOf(
            PdfWordBox("Academic", NormRect(0.10f, 0.10f, 0.22f, 0.13f)),
            PdfWordBox("History", NormRect(0.24f, 0.10f, 0.35f, 0.13f)),
            PdfWordBox("Notes", NormRect(0.37f, 0.10f, 0.45f, 0.13f)),
            PdfWordBox("history", NormRect(0.10f, 0.30f, 0.20f, 0.33f))
        )

        val matches = PdfTextLayerUtil.findMatchRects(words, "history notes")
        assertEquals(1, matches.size)
        assertEquals(NormRect(0.24f, 0.10f, 0.45f, 0.13f), matches[0])

        val singleWordMatches = PdfTextLayerUtil.findMatchRects(words, "HISTORY")
        assertEquals(2, singleWordMatches.size)
        assertEquals(NormRect(0.24f, 0.10f, 0.35f, 0.13f), singleWordMatches[0])
        assertEquals(NormRect(0.10f, 0.30f, 0.20f, 0.33f), singleWordMatches[1])
    }

    @Test
    fun serializeAndParseRects_roundTripAndMalformedSafety() {
        assertEquals("", PdfTextLayerUtil.serializeRects(emptyList()))
        assertTrue(PdfTextLayerUtil.parseRects("").isEmpty())
        assertTrue(PdfTextLayerUtil.parseRects("not json at all").isEmpty())
        assertTrue(PdfTextLayerUtil.parseRects("[{\"l\":0.5,\"t\":0.5,\"r\":0.2,\"b\":0.6}]").isEmpty())

        val rects = listOf(
            NormRect(0.1234f, 0.2345f, 0.4567f, 0.3000f),
            NormRect(0.1000f, 0.4000f, 0.8000f, 0.4500f)
        )
        val json = PdfTextLayerUtil.serializeRects(rects)
        val parsed = PdfTextLayerUtil.parseRects(json)
        assertEquals(2, parsed.size)
        assertEquals(0.1234f, parsed[0].left, 1e-4f)
        assertEquals(0.2345f, parsed[0].top, 1e-4f)
        assertEquals(0.4567f, parsed[0].right, 1e-4f)
        assertEquals(0.3000f, parsed[0].bottom, 1e-4f)
        assertEquals(0.8000f, parsed[1].right, 1e-4f)
    }

    @Test
    fun cropLocalToDisplay_isExactInverseOfDisplayToPdfUserSpaceRelativeToCropBox() {
        val box = PdfVectorExportUtil.PdfBox(100f, 100f, 500f, 700f) // cropW=400, cropH=600
        for (rot in listOf(0, 90, 180, 270, -90)) {
            val (dispW, dispH) = PdfVectorExportUtil.displayDimensions(box, rot)
            val dx = dispW * 0.27f
            val dy = dispH * 0.63f
            val (pdfX, pdfY) = PdfVectorExportUtil.displayToPdfUserSpace(dx, dy, box, rot)
            val ux = pdfX - box.llx
            val uy = pdfY - box.lly
            val (roundDx, roundDy) = PdfTextLayerUtil.cropLocalToDisplay(ux, uy, box.width, box.height, rot)
            assertEquals("dx mismatch for rot=$rot", dx, roundDx, 1e-3f)
            assertEquals("dy mismatch for rot=$rot", dy, roundDy, 1e-3f)
        }
    }

    @Test
    fun glyphQuadToNormRect_cropBoxLocalAndRotatedNonSquarePages() {
        // Unrotated cropped page (400 x 600); LegacyPDFStreamEngine already emits cropBox-local coords:
        // ux0=100 -> 0.25, uy0=450 -> dy=150/600=0.25, h=10 -> top=(150-10.5)/600, bottom=(150+2)/600
        val unrotated = PdfTextLayerUtil.glyphQuadToNormRect(
            ux0 = 100f, uy0 = 450f,
            endX = 180f, endY = 450f,
            scaleX = 14f, shearY = 0f, shearX = 0f, scaleY = 14f,
            widthDirAdj = 80f, heightDir = 10f, fontSizeInPt = 14f,
            cropW = 400f, cropH = 600f, rotation = 0
        )
        assertNotNull(unrotated)
        assertEquals(0.25f, unrotated!!.left, 1e-4f)
        assertEquals(0.45f, unrotated.right, 1e-4f)
        assertEquals((150f - 10.5f) / 600f, unrotated.top, 1e-4f)
        assertEquals((150f + 2f) / 600f, unrotated.bottom, 1e-4f)

        // Non-square page 400 x 800 rotated 90 deg -> dispW=800, dispH=400
        // (ux=80..120, uy=640) -> display x around 640/800=0.80, display y in [80/400, 120/400]=[0.20, 0.30]
        val rot90 = PdfTextLayerUtil.glyphQuadToNormRect(
            ux0 = 80f, uy0 = 640f,
            endX = 120f, endY = 640f,
            scaleX = 14f, shearY = 0f, shearX = 0f, scaleY = 14f,
            widthDirAdj = 40f, heightDir = 10f, fontSizeInPt = 14f,
            cropW = 400f, cropH = 800f, rotation = 90
        )
        assertNotNull(rot90)
        assertEquals((640f - 2f) / 800f, rot90!!.left, 1e-4f)
        assertEquals((640f + 10.5f) / 800f, rot90.right, 1e-4f)
        assertEquals(0.20f, rot90.top, 1e-4f)
        assertEquals(0.30f, rot90.bottom, 1e-4f)

        // Non-square page 400 x 800 rotated 180 deg -> dispW=400, dispH=800
        val rot180 = PdfTextLayerUtil.glyphQuadToNormRect(
            ux0 = 80f, uy0 = 640f,
            endX = 120f, endY = 640f,
            scaleX = 14f, shearY = 0f, shearX = 0f, scaleY = 14f,
            widthDirAdj = 40f, heightDir = 10f, fontSizeInPt = 14f,
            cropW = 400f, cropH = 800f, rotation = 180
        )
        assertNotNull(rot180)
        assertEquals((400f - 120f) / 400f, rot180!!.left, 1e-4f)
        assertEquals((400f - 80f) / 400f, rot180.right, 1e-4f)
        assertEquals((640f - 2f) / 800f, rot180.top, 1e-4f)
        assertEquals((640f + 10.5f) / 800f, rot180.bottom, 1e-4f)

        // Non-square page 400 x 800 rotated 270 deg -> dispW=800, dispH=400
        val rot270 = PdfTextLayerUtil.glyphQuadToNormRect(
            ux0 = 80f, uy0 = 640f,
            endX = 120f, endY = 640f,
            scaleX = 14f, shearY = 0f, shearX = 0f, scaleY = 14f,
            widthDirAdj = 40f, heightDir = 10f, fontSizeInPt = 14f,
            cropW = 400f, cropH = 800f, rotation = 270
        )
        assertNotNull(rot270)
        assertEquals((800f - 640f - 10.5f) / 800f, rot270!!.left, 1e-4f)
        assertEquals((800f - 640f + 2f) / 800f, rot270.right, 1e-4f)
        assertEquals((400f - 120f) / 400f, rot270.top, 1e-4f)
        assertEquals((400f - 80f) / 400f, rot270.bottom, 1e-4f)
    }

    @Test
    fun glyphQuadToNormRect_handlesRotatedGlyphDirectionZeroEndDeltaAndDegenerateBoxes() {
        // Vertical text (90 deg CCW in unrotated page): baseline (0, +1), ascender (-1, 0)
        // endX/endY equals start -> falls back to widthDirAdj along (scaleX, shearY) = (0, 1)
        val vertical = PdfTextLayerUtil.glyphQuadToNormRect(
            ux0 = 200f, uy0 = 300f,
            endX = 200f, endY = 300f,
            scaleX = 0f, shearY = 12f, shearX = -12f, scaleY = 0f,
            widthDirAdj = 50f, heightDir = 10f, fontSizeInPt = 12f,
            cropW = 400f, cropH = 800f, rotation = 0
        )
        assertNotNull(vertical)
        assertEquals((200f - 10.5f) / 400f, vertical!!.left, 1e-4f)
        assertEquals((200f + 2f) / 400f, vertical.right, 1e-4f)
        assertEquals((800f - 350f) / 800f, vertical.top, 1e-4f)
        assertEquals((800f - 300f) / 800f, vertical.bottom, 1e-4f)

        // Completely off-page degenerate box returns null
        val offPage = PdfTextLayerUtil.glyphQuadToNormRect(
            ux0 = -50f, uy0 = -50f,
            endX = -40f, endY = -50f,
            scaleX = 10f, shearY = 0f, shearX = 0f, scaleY = 10f,
            widthDirAdj = 10f, heightDir = 10f, fontSizeInPt = 10f,
            cropW = 400f, cropH = 600f, rotation = 0
        )
        assertNull(offPage)
    }

    @Test
    fun getWordBoxes_missingFileOrInvalidPageReturnsEmptySafely() = runBlocking {
        assertTrue(PdfTextLayerUtil.getWordBoxes(File("non_existent_12345.pdf"), 1).isEmpty())
        assertTrue(PdfTextLayerUtil.getWordBoxes(File("non_existent_12345.pdf"), 0).isEmpty())
    }
}
