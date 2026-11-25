package br.com.jotdown.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ShapeRecognitionUtilTest {

    @Test
    fun rdpSimplify_collapsesCollinearPointsAndPreservesSharpVertices() {
        val polyline = listOf(
            GeomPoint(0f, 0f),
            GeomPoint(25f, 1f),
            GeomPoint(50f, 0f),
            GeomPoint(50f, 50f),
            GeomPoint(49f, 75f),
            GeomPoint(50f, 100f)
        )
        val simplified = ShapeRecognitionUtil.rdpSimplify(polyline, epsilon = 4f)
        assertEquals(listOf(GeomPoint(0f, 0f), GeomPoint(50f, 0f), GeomPoint(50f, 100f)), simplified)
    }

    @Test
    fun recognize_rejectsShortOrChaoticStrokes() {
        assertNull(ShapeRecognitionUtil.recognize(listOf(GeomPoint(0f, 0f), GeomPoint(50f, 0f))))
        assertNull(
            ShapeRecognitionUtil.recognize(
                listOf(GeomPoint(0f, 0f), GeomPoint(5f, 0f), GeomPoint(10f, 0f), GeomPoint(15f, 0f))
            )
        )

        val zigzag = (0..12).map { i ->
            GeomPoint(i * 15f, if (i % 2 == 0) 0f else 60f)
        }
        assertNull(ShapeRecognitionUtil.recognize(zigzag))
    }

    @Test
    fun recognize_snapsHorizontalVerticalAndPreservesDiagonalLines() {
        // Horizontal within 5 deg -> snapped y == start.y
        val horiz = (0..10).map { i -> GeomPoint(10f + i * 15f, 40f + i * 0.5f) }
        val horizShape = ShapeRecognitionUtil.recognize(horiz)
        assertNotNull(horizShape)
        assertEquals(RecognizedShapeType.LINE, horizShape!!.type)
        assertEquals(2, horizShape.points.size)
        assertEquals(40f, horizShape.points[1].y, 1e-4f)

        // Vertical within 5 deg -> snapped x == start.x
        val vert = (0..10).map { i -> GeomPoint(80f + i * 0.4f, 20f + i * 14f) }
        val vertShape = ShapeRecognitionUtil.recognize(vert)
        assertNotNull(vertShape)
        assertEquals(RecognizedShapeType.LINE, vertShape!!.type)
        assertEquals(80f, vertShape.points[1].x, 1e-4f)

        // Diagonal line at 45 deg
        val diag = (0..10).map { i -> GeomPoint(i * 12f, i * 12f) }
        val diagShape = ShapeRecognitionUtil.recognize(diag)
        assertNotNull(diagShape)
        assertEquals(RecognizedShapeType.LINE, diagShape!!.type)
        assertEquals(120f, diagShape.points[1].x, 1e-3f)
        assertEquals(120f, diagShape.points[1].y, 1e-3f)
    }

    @Test
    fun recognize_detectsArrowsWithSnappedShaftAndSymmetricBarbs() {
        // Single-barb horizontal arrow: (0,50) -> (120,52) -> (98,35)
        val singleBarb = mutableListOf<GeomPoint>()
        for (i in 0..8) singleBarb.add(GeomPoint(i * 15f, 50f + i * 0.25f))
        singleBarb.add(GeomPoint(98f, 35f))

        val res = ShapeRecognitionUtil.recognize(singleBarb)
        assertNotNull(res)
        assertEquals(RecognizedShapeType.ARROW, res!!.type)
        assertEquals(5, res.points.size)
        assertEquals(50f, res.points[1].y, 1e-3f)
        assertEquals(res.points[1], res.points[3])

        // Double-barb continuous arrow: shaft -> tip -> leftBarb -> tip -> rightBarb
        val doubleBarb = mutableListOf<GeomPoint>()
        for (i in 0..10) doubleBarb.add(GeomPoint(i * 14f, 100f))
        doubleBarb.add(GeomPoint(120f, 85f))
        doubleBarb.add(GeomPoint(140f, 100f))
        doubleBarb.add(GeomPoint(120f, 115f))

        val res2 = ShapeRecognitionUtil.recognize(doubleBarb)
        assertNotNull(res2)
        assertEquals(RecognizedShapeType.ARROW, res2!!.type)
        assertEquals(5, res2.points.size)
        assertEquals(100f, res2.points[1].y, 1e-3f)
    }

    @Test
    fun recognize_detectsSmallAndLargeCirclesAndEllipses() {
        fun sampleEllipse(cx: Float, cy: Float, rx: Float, ry: Float, count: Int = 24): List<GeomPoint> =
            (0..count).map { i ->
                val theta = 2.0 * PI * i / count
                GeomPoint(cx + (rx * cos(theta)).toFloat(), cy + (ry * sin(theta)).toFloat())
            }

        // Small circle (radius 25) must still classify as CIRCLE, not RECTANGLE
        val smallCircle = ShapeRecognitionUtil.recognize(sampleEllipse(100f, 100f, 25f, 24f, count = 20))
        assertNotNull(smallCircle)
        assertEquals(RecognizedShapeType.CIRCLE, smallCircle!!.type)
        assertEquals(65, smallCircle.points.size)
        assertEquals(smallCircle.points.first(), smallCircle.points.last())

        // Large circle
        val largeCircle = ShapeRecognitionUtil.recognize(sampleEllipse(200f, 200f, 90f, 85f, count = 32))
        assertNotNull(largeCircle)
        assertEquals(RecognizedShapeType.CIRCLE, largeCircle!!.type)

        // Elongated ellipse (rx=120, ry=45)
        val ellipse = ShapeRecognitionUtil.recognize(sampleEllipse(200f, 200f, 120f, 45f, count = 32))
        assertNotNull(ellipse)
        assertEquals(RecognizedShapeType.ELLIPSE, ellipse!!.type)
        assertEquals(65, ellipse.points.size)
        assertEquals(ellipse.points.first(), ellipse.points.last())
    }

    @Test
    fun recognize_detectsTriangleIncludingMidEdgeClosure() {
        val pts = mutableListOf<GeomPoint>()
        fun addEdge(a: GeomPoint, b: GeomPoint, steps: Int = 6) {
            for (i in 0 until steps) {
                val t = i.toFloat() / steps
                pts.add(GeomPoint(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y)))
            }
        }
        val v0 = GeomPoint(100f, 20f)
        val v1 = GeomPoint(180f, 160f)
        val v2 = GeomPoint(20f, 160f)
        // Start mid-edge on (v2 -> v0) and close back near start
        addEdge(GeomPoint(60f, 90f), v0)
        addEdge(v0, v1)
        addEdge(v1, v2)
        addEdge(v2, GeomPoint(58f, 93f))
        pts.add(GeomPoint(58f, 93f))

        val tri = ShapeRecognitionUtil.recognize(pts)
        assertNotNull(tri)
        assertEquals(RecognizedShapeType.TRIANGLE, tri!!.type)
        assertEquals(4, tri.points.size)
        assertEquals(tri.points.first(), tri.points.last())
    }

    @Test
    fun recognize_detectsAxisAlignedRectangleSquareAndTiltedRectangle() {
        fun sampleRect(c: List<GeomPoint>): List<GeomPoint> {
            val out = mutableListOf<GeomPoint>()
            for (k in 0 until 4) {
                val a = c[k]
                val b = c[(k + 1) % 4]
                for (i in 0 until 5) {
                    val t = i / 5f
                    out.add(GeomPoint(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y)))
                }
            }
            out.add(c[0])
            return out
        }

        // Wide rectangle
        val rectPts = sampleRect(
            listOf(GeomPoint(20f, 30f), GeomPoint(220f, 30f), GeomPoint(220f, 110f), GeomPoint(20f, 110f))
        )
        val rect = ShapeRecognitionUtil.recognize(rectPts)
        assertNotNull(rect)
        assertEquals(RecognizedShapeType.RECTANGLE, rect!!.type)
        assertEquals(5, rect.points.size)
        assertEquals(rect.points.first(), rect.points.last())

        // Near-square (100x92 -> ratio 0.92 >= 0.85) snaps to exact equal sides preserving center
        val nearSquarePts = sampleRect(
            listOf(GeomPoint(0f, 0f), GeomPoint(100f, 0f), GeomPoint(100f, 92f), GeomPoint(0f, 92f))
        )
        val square = ShapeRecognitionUtil.recognize(nearSquarePts)
        assertNotNull(square)
        assertEquals(RecognizedShapeType.RECTANGLE, square!!.type)
        val sideW = hypot((square.points[1].x - square.points[0].x).toDouble(), (square.points[1].y - square.points[0].y).toDouble())
        val sideH = hypot((square.points[2].x - square.points[1].x).toDouble(), (square.points[2].y - square.points[1].y).toDouble())
        assertEquals(sideW, sideH, 1e-2)

        // Tilted 30 deg rectangle -> 4 exact 90-degree corners
        val rad = 30.0 * PI / 180.0
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val vx = -uy
        val vy = ux
        fun pt(u: Float, v: Float) = GeomPoint(150f + u * ux + v * vx, 150f + u * uy + v * vy)
        val tilted = ShapeRecognitionUtil.recognize(
            sampleRect(listOf(pt(-80f, -35f), pt(80f, -35f), pt(80f, 35f), pt(-80f, 35f)))
        )
        assertNotNull(tilted)
        assertEquals(RecognizedShapeType.RECTANGLE, tilted!!.type)
        for (i in 0 until 4) {
            val prev = tilted.points[(i + 3) % 4]
            val cur = tilted.points[i]
            val next = tilted.points[(i + 1) % 4]
            val dot = (prev.x - cur.x) * (next.x - cur.x) + (prev.y - cur.y) * (next.y - cur.y)
            assertTrue("Corner $i should be 90 deg, dot=$dot", abs(dot) < 1e-2f)
        }
    }
}
