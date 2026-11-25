package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeSmoothingUtilTest {

    @Test
    fun buildQuadraticSegments_handlesFewerThanTwoAndTwoPoints() {
        assertTrue(StrokeSmoothingUtil.buildQuadraticSegments(emptyList()).isEmpty())
        assertTrue(
            StrokeSmoothingUtil.buildQuadraticSegments(listOf(Triple(10f, 20f, 0.8f))).isEmpty()
        )

        val two = StrokeSmoothingUtil.buildQuadraticSegments(
            listOf(Triple(0f, 0f, 0.4f), Triple(10f, 20f, 0.9f))
        )
        assertEquals(1, two.size)
        assertEquals(GeomPoint(0f, 0f), two[0].start)
        assertEquals(GeomPoint(5f, 10f), two[0].control)
        assertEquals(GeomPoint(10f, 20f), two[0].end)
        assertEquals(0.4f, two[0].startPressure, 1e-4f)
        assertEquals(0.9f, two[0].endPressure, 1e-4f)
    }

    @Test
    fun buildQuadraticSegments_smoothCurveHasC1ContinuityThroughMidpoints() {
        val pts = listOf(
            Triple(0f, 0f, 0.5f),
            Triple(20f, 10f, 0.7f),
            Triple(40f, 15f, 0.9f),
            Triple(60f, 12f, 0.6f)
        )
        val segs = StrokeSmoothingUtil.buildQuadraticSegments(pts)

        assertEquals(3, segs.size)
        assertEquals(GeomPoint(0f, 0f), segs[0].start)
        assertEquals(GeomPoint(20f, 10f), segs[0].control)
        assertEquals(GeomPoint(30f, 12.5f), segs[0].end)

        assertEquals(segs[0].end, segs[1].start)
        assertEquals(segs[0].endPressure, segs[1].startPressure, 1e-4f)
        assertEquals(GeomPoint(40f, 15f), segs[1].control)
        assertEquals(GeomPoint(50f, 13.5f), segs[1].end)

        assertEquals(segs[1].end, segs[2].start)
        assertEquals(GeomPoint(60f, 12f), segs[2].end)
    }

    @Test
    fun buildQuadraticSegments_preservesSharpCornersForRectanglesAndArrows() {
        val rect = listOf(
            Triple(0f, 0f, 1f),
            Triple(100f, 0f, 1f),
            Triple(100f, 80f, 1f),
            Triple(0f, 80f, 1f),
            Triple(0f, 0f, 1f)
        )
        val rectSegs = StrokeSmoothingUtil.buildQuadraticSegments(rect)
        assertEquals(4, rectSegs.size)
        assertEquals(GeomPoint(0f, 0f), rectSegs[0].start)
        assertEquals(GeomPoint(100f, 0f), rectSegs[0].end)
        assertEquals(GeomPoint(100f, 80f), rectSegs[1].end)
        assertEquals(GeomPoint(0f, 80f), rectSegs[2].end)
        assertEquals(GeomPoint(0f, 0f), rectSegs[3].end)

        val arrow = listOf(
            Triple(0f, 50f, 1f),
            Triple(100f, 50f, 1f),
            Triple(80f, 38f, 1f),
            Triple(100f, 50f, 1f),
            Triple(80f, 62f, 1f)
        )
        val arrowSegs = StrokeSmoothingUtil.buildQuadraticSegments(arrow)
        assertEquals(4, arrowSegs.size)
        assertEquals(GeomPoint(100f, 50f), arrowSegs[0].end)
        assertEquals(GeomPoint(80f, 38f), arrowSegs[1].end)
        assertEquals(GeomPoint(100f, 50f), arrowSegs[2].end)
        assertEquals(GeomPoint(80f, 62f), arrowSegs[3].end)
    }

    @Test
    fun buildQuadraticSegments_handlesDuplicatePointsWithoutNaN() {
        val dup = listOf(
            Triple(10f, 10f, 1f),
            Triple(10f, 10f, 1f),
            Triple(30f, 40f, 1f)
        )
        val segs = StrokeSmoothingUtil.buildQuadraticSegments(dup)
        assertTrue(segs.isNotEmpty())
        segs.forEach { seg ->
            assertTrue(seg.start.x.isFinite() && seg.start.y.isFinite())
            assertTrue(seg.control.x.isFinite() && seg.control.y.isFinite())
            assertTrue(seg.end.x.isFinite() && seg.end.y.isFinite())
        }
    }

    @Test
    fun quadToCubicControlPoints_elevatesDegreeExactly() {
        val p0 = GeomPoint(0f, 0f)
        val ctrl = GeomPoint(3f, 6f)
        val p2 = GeomPoint(6f, 0f)
        val (c1, c2) = StrokeSmoothingUtil.quadToCubicControlPoints(p0, ctrl, p2)

        assertEquals(2f, c1.x, 1e-4f)
        assertEquals(4f, c1.y, 1e-4f)
        assertEquals(4f, c2.x, 1e-4f)
        assertEquals(4f, c2.y, 1e-4f)

        // Midpoint t=0.5 of quad vs elevated cubic must match identically
        val qMidX = 0.25f * p0.x + 0.5f * ctrl.x + 0.25f * p2.x
        val qMidY = 0.25f * p0.y + 0.5f * ctrl.y + 0.25f * p2.y
        val cMidX = 0.125f * p0.x + 0.375f * c1.x + 0.375f * c2.x + 0.125f * p2.x
        val cMidY = 0.125f * p0.y + 0.375f * c1.y + 0.375f * c2.y + 0.125f * p2.y
        assertEquals(qMidX, cMidX, 1e-4f)
        assertEquals(qMidY, cMidY, 1e-4f)
    }

    @Test
    fun deduplicatePoints_dropsSubThresholdIntermediatesAndPreservesEndpoints() {
        val raw = listOf(
            Triple(0f, 0f, 0.5f),
            Triple(0.2f, 0.1f, 0.5f),
            Triple(0.5f, 0.2f, 0.5f),
            Triple(2.0f, 0f, 0.7f),
            Triple(2.1f, 0.1f, 0.7f),
            Triple(2.2f, 0.2f, 0.9f)
        )
        val deduped = StrokeSmoothingUtil.deduplicatePoints(raw, minDistance = 0.75f)
        assertEquals(3, deduped.size)
        assertEquals(raw.first(), deduped.first())
        assertEquals(Triple(2.0f, 0f, 0.7f), deduped[1])
        assertEquals(raw.last(), deduped.last())
    }
}
