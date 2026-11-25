package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeGeometryUtilTest {

    @Test
    fun pointToSegmentDistance_handlesEndpointsAndPerpendicularProjection() {
        assertEquals(0f, StrokeGeometryUtil.pointToSegmentDistance(5f, 0f, 0f, 0f, 10f, 0f), 1e-4f)
        assertEquals(3f, StrokeGeometryUtil.pointToSegmentDistance(5f, 3f, 0f, 0f, 10f, 0f), 1e-4f)
        assertEquals(5f, StrokeGeometryUtil.pointToSegmentDistance(-3f, 4f, 0f, 0f, 10f, 0f), 1e-4f)
        // Degenerate zero-length segment
        assertEquals(5f, StrokeGeometryUtil.pointToSegmentDistance(3f, 4f, 0f, 0f, 0f, 0f), 1e-4f)
    }

    @Test
    fun segmentsIntersect_crossingEndpointAndCollinearOverlap() {
        // Crossing X
        assertTrue(
            StrokeGeometryUtil.segmentsIntersect(
                GeomPoint(0f, 0f), GeomPoint(10f, 10f),
                GeomPoint(0f, 10f), GeomPoint(10f, 0f)
            )
        )
        // Touching endpoint (T-junction)
        assertTrue(
            StrokeGeometryUtil.segmentsIntersect(
                GeomPoint(0f, 0f), GeomPoint(10f, 0f),
                GeomPoint(5f, 0f), GeomPoint(5f, 8f)
            )
        )
        // Collinear overlapping
        assertTrue(
            StrokeGeometryUtil.segmentsIntersect(
                GeomPoint(0f, 0f), GeomPoint(10f, 0f),
                GeomPoint(6f, 0f), GeomPoint(15f, 0f)
            )
        )
        // Collinear disjoint
        assertFalse(
            StrokeGeometryUtil.segmentsIntersect(
                GeomPoint(0f, 0f), GeomPoint(4f, 0f),
                GeomPoint(6f, 0f), GeomPoint(10f, 0f)
            )
        )
        // Parallel disjoint
        assertFalse(
            StrokeGeometryUtil.segmentsIntersect(
                GeomPoint(0f, 0f), GeomPoint(10f, 0f),
                GeomPoint(0f, 2f), GeomPoint(10f, 2f)
            )
        )
    }

    @Test
    fun segmentToSegmentDistance_zeroOnIntersectOtherwiseMinEndpointDistance() {
        assertEquals(
            0f,
            StrokeGeometryUtil.segmentToSegmentDistance(
                GeomPoint(0f, 5f), GeomPoint(10f, 5f),
                GeomPoint(5f, 0f), GeomPoint(5f, 10f)
            ),
            1e-4f
        )
        assertEquals(
            2f,
            StrokeGeometryUtil.segmentToSegmentDistance(
                GeomPoint(0f, 0f), GeomPoint(10f, 0f),
                GeomPoint(2f, 2f), GeomPoint(8f, 5f)
            ),
            1e-4f
        )
    }

    @Test
    fun doesEraserHitPath_handlesEmptySinglePointAndMultiSegment() {
        assertFalse(StrokeGeometryUtil.doesEraserHitPath(emptyList(), listOf(GeomPoint(0f, 0f)), 10f))
        assertFalse(StrokeGeometryUtil.doesEraserHitPath(listOf(GeomPoint(0f, 0f)), emptyList(), 10f))

        // Single-point eraser hitting single-point path
        assertTrue(
            StrokeGeometryUtil.doesEraserHitPath(
                listOf(GeomPoint(0f, 0f)),
                listOf(GeomPoint(3f, 4f)),
                hitRadius = 5f
            )
        )
        assertFalse(
            StrokeGeometryUtil.doesEraserHitPath(
                listOf(GeomPoint(0f, 0f)),
                listOf(GeomPoint(3f, 4f)),
                hitRadius = 4.9f
            )
        )

        // Single-point eraser hitting multi-point stroke
        assertTrue(
            StrokeGeometryUtil.doesEraserHitPath(
                listOf(GeomPoint(50f, 12f)),
                listOf(GeomPoint(0f, 0f), GeomPoint(100f, 0f)),
                hitRadius = 15f
            )
        )

        // Multi-point eraser hitting single-point stroke
        assertTrue(
            StrokeGeometryUtil.doesEraserHitPath(
                listOf(GeomPoint(0f, 0f), GeomPoint(100f, 0f)),
                listOf(GeomPoint(50f, 10f)),
                hitRadius = 10f
            )
        )

        // Crossing strokes
        assertTrue(
            StrokeGeometryUtil.doesEraserHitPath(
                listOf(GeomPoint(0f, 50f), GeomPoint(100f, 50f)),
                listOf(GeomPoint(50f, 0f), GeomPoint(50f, 100f)),
                hitRadius = 1f
            )
        )

        // Far apart AABB reject
        assertFalse(
            StrokeGeometryUtil.doesEraserHitPath(
                listOf(GeomPoint(0f, 0f), GeomPoint(10f, 10f)),
                listOf(GeomPoint(100f, 100f), GeomPoint(120f, 120f)),
                hitRadius = 15f
            )
        )
    }

    @Test
    fun isPointInPolygon_rayCasting() {
        val square = listOf(
            GeomPoint(0f, 0f),
            GeomPoint(10f, 0f),
            GeomPoint(10f, 10f),
            GeomPoint(0f, 10f)
        )
        assertTrue(StrokeGeometryUtil.isPointInPolygon(5f, 5f, square))
        assertFalse(StrokeGeometryUtil.isPointInPolygon(15f, 5f, square))
        assertFalse(StrokeGeometryUtil.isPointInPolygon(5f, 5f, square.take(2)))
    }

    @Test
    fun isPathSelectedByLasso_fractionAndCentroidFallback() {
        val poly = listOf(
            GeomPoint(0f, 0f),
            GeomPoint(100f, 0f),
            GeomPoint(100f, 100f),
            GeomPoint(0f, 100f)
        )
        // 2/3 points inside -> >= 0.5 -> true
        assertTrue(
            StrokeGeometryUtil.isPathSelectedByLasso(
                listOf(GeomPoint(50f, 50f), GeomPoint(60f, 60f), GeomPoint(150f, 50f)),
                poly
            )
        )
        // 1/3 points inside (80,50), others outside (105,50), (105,50) -> centroid (96.6, 50) inside -> true
        assertTrue(
            StrokeGeometryUtil.isPathSelectedByLasso(
                listOf(GeomPoint(80f, 50f), GeomPoint(105f, 50f), GeomPoint(105f, 50f)),
                poly
            )
        )
        // Completely outside -> false
        assertFalse(
            StrokeGeometryUtil.isPathSelectedByLasso(
                listOf(GeomPoint(120f, 120f), GeomPoint(140f, 140f)),
                poly
            )
        )
    }

    @Test
    fun boundingBoxOfPaths_computesMinMaxOrNullWhenEmpty() {
        assertNull(StrokeGeometryUtil.boundingBoxOfPaths(emptyList()))
        assertNull(StrokeGeometryUtil.boundingBoxOfPaths(listOf(emptyList())))

        val bbox = StrokeGeometryUtil.boundingBoxOfPaths(
            listOf(
                listOf(GeomPoint(10f, 20f), GeomPoint(30f, 5f)),
                listOf(GeomPoint(-4f, 50f))
            )
        )
        assertNotNull(bbox)
        assertEquals(-4f, bbox!!.left, 1e-4f)
        assertEquals(5f, bbox.top, 1e-4f)
        assertEquals(30f, bbox.right, 1e-4f)
        assertEquals(50f, bbox.bottom, 1e-4f)
    }
}
