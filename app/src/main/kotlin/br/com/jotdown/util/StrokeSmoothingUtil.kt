package br.com.jotdown.util

import kotlin.math.hypot

data class QuadSegment(
    val start: GeomPoint,
    val control: GeomPoint,
    val end: GeomPoint,
    val startPressure: Float,
    val endPressure: Float
)

/**
 * Pure-Kotlin quadratic midpoint Bézier smoothing, corner preservation,
 * quadratic-to-cubic degree elevation (for PDF operators), and point deduplication.
 */
object StrokeSmoothingUtil {

    fun buildQuadraticSegments(
        points: List<Triple<Float, Float, Float>>,
        sharpCornerCosThreshold: Float = 0.25f
    ): List<QuadSegment> {
        if (points.size < 2) return emptyList()
        if (points.size == 2) {
            val p0 = GeomPoint(points[0].first, points[0].second)
            val p1 = GeomPoint(points[1].first, points[1].second)
            val mid = GeomPoint((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
            return listOf(QuadSegment(p0, mid, p1, points[0].third, points[1].third))
        }

        val segments = ArrayList<QuadSegment>(points.size)
        var currStart = GeomPoint(points[0].first, points[0].second)
        var currStartP = points[0].third

        for (i in 1 until points.size - 1) {
            val pPrev = GeomPoint(points[i - 1].first, points[i - 1].second)
            val pi = GeomPoint(points[i].first, points[i].second)
            val pri = points[i].third
            val pNext = GeomPoint(points[i + 1].first, points[i + 1].second)
            val prNext = points[i + 1].third

            val v1x = (pi.x - pPrev.x).toDouble()
            val v1y = (pi.y - pPrev.y).toDouble()
            val v2x = (pNext.x - pi.x).toDouble()
            val v2y = (pNext.y - pi.y).toDouble()
            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)

            val isSharpCorner = if (len1 > 1e-6 && len2 > 1e-6) {
                val cosAngle = ((v1x * v2x + v1y * v2y) / (len1 * len2)).toFloat()
                cosAngle <= sharpCornerCosThreshold
            } else {
                false
            }

            if (isSharpCorner) {
                val midCtrl = GeomPoint((currStart.x + pi.x) * 0.5f, (currStart.y + pi.y) * 0.5f)
                segments.add(QuadSegment(currStart, midCtrl, pi, currStartP, pri))
                currStart = pi
                currStartP = pri
            } else {
                val mid = GeomPoint((pi.x + pNext.x) * 0.5f, (pi.y + pNext.y) * 0.5f)
                val midP = (pri + prNext) * 0.5f
                segments.add(QuadSegment(currStart, pi, mid, currStartP, midP))
                currStart = mid
                currStartP = midP
            }
        }

        val last = points.last()
        val pLast = GeomPoint(last.first, last.second)
        val prLast = last.third
        val finalCtrl = GeomPoint((currStart.x + pLast.x) * 0.5f, (currStart.y + pLast.y) * 0.5f)
        segments.add(QuadSegment(currStart, finalCtrl, pLast, currStartP, prLast))
        return segments
    }

    fun quadToCubicControlPoints(
        start: GeomPoint,
        control: GeomPoint,
        end: GeomPoint
    ): Pair<GeomPoint, GeomPoint> {
        val c1 = GeomPoint(
            start.x + (2f / 3f) * (control.x - start.x),
            start.y + (2f / 3f) * (control.y - start.y)
        )
        val c2 = GeomPoint(
            end.x + (2f / 3f) * (control.x - end.x),
            end.y + (2f / 3f) * (control.y - end.y)
        )
        return c1 to c2
    }

    fun deduplicatePoints(
        points: List<Triple<Float, Float, Float>>,
        minDistance: Float = 0.75f
    ): List<Triple<Float, Float, Float>> {
        if (points.size <= 2) return points
        val out = ArrayList<Triple<Float, Float, Float>>(points.size)
        out.add(points[0])
        var lastKept = points[0]
        for (i in 1 until points.size - 1) {
            val pt = points[i]
            val dist = hypot((pt.first - lastKept.first).toDouble(), (pt.second - lastKept.second).toDouble()).toFloat()
            if (dist >= minDistance) {
                out.add(pt)
                lastKept = pt
            }
        }
        out.add(points.last())
        return out
    }
}
