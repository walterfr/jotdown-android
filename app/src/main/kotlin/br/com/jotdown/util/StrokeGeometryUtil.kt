package br.com.jotdown.util

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class GeomPoint(val x: Float, val y: Float)
data class GeomRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Pure Kotlin stroke hit-testing and lasso selection geometry utilities.
 */
object StrokeGeometryUtil {

    fun pointToSegmentDistance(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq <= 1e-12f) {
            return hypot((px - ax).toDouble(), (py - ay).toDouble()).toFloat()
        }
        val t = (((px - ax) * abx + (py - ay) * aby) / lenSq).coerceIn(0f, 1f)
        val cx = ax + t * abx
        val cy = ay + t * aby
        return hypot((px - cx).toDouble(), (py - cy).toDouble()).toFloat()
    }

    fun segmentsIntersect(a1: GeomPoint, a2: GeomPoint, b1: GeomPoint, b2: GeomPoint): Boolean {
        if (max(a1.x, a2.x) < min(b1.x, b2.x) ||
            max(b1.x, b2.x) < min(a1.x, a2.x) ||
            max(a1.y, a2.y) < min(b1.y, b2.y) ||
            max(b1.y, b2.y) < min(a1.y, a2.y)
        ) {
            return false
        }

        val o1 = cross(a1, a2, b1)
        val o2 = cross(a1, a2, b2)
        val o3 = cross(b1, b2, a1)
        val o4 = cross(b1, b2, a2)

        val epsA = collinearEps(a1, a2, b1, b2)
        val epsB = collinearEps(b1, b2, a1, a2)

        if (((o1 > epsA && o2 < -epsA) || (o1 < -epsA && o2 > epsA)) &&
            ((o3 > epsB && o4 < -epsB) || (o3 < -epsB && o4 > epsB))
        ) {
            return true
        }

        if (abs(o1) <= epsA && onSegment(a1, b1, a2)) return true
        if (abs(o2) <= epsA && onSegment(a1, b2, a2)) return true
        if (abs(o3) <= epsB && onSegment(b1, a1, b2)) return true
        if (abs(o4) <= epsB && onSegment(b1, a2, b2)) return true

        return false
    }

    private fun cross(p: GeomPoint, q: GeomPoint, r: GeomPoint): Double {
        return (q.x - p.x).toDouble() * (r.y - p.y).toDouble() -
            (q.y - p.y).toDouble() * (r.x - p.x).toDouble()
    }

    private fun collinearEps(p: GeomPoint, q: GeomPoint, r1: GeomPoint, r2: GeomPoint): Double {
        val lenPQ = hypot((q.x - p.x).toDouble(), (q.y - p.y).toDouble())
        val lenR = max(
            hypot((r1.x - p.x).toDouble(), (r1.y - p.y).toDouble()),
            hypot((r2.x - p.x).toDouble(), (r2.y - p.y).toDouble())
        )
        return 1e-7 * max(1.0, lenPQ * lenR)
    }

    private fun onSegment(p: GeomPoint, q: GeomPoint, r: GeomPoint): Boolean {
        val eps = 1e-4f
        return q.x >= min(p.x, r.x) - eps &&
            q.x <= max(p.x, r.x) + eps &&
            q.y >= min(p.y, r.y) - eps &&
            q.y <= max(p.y, r.y) + eps
    }

    fun segmentToSegmentDistance(
        a1: GeomPoint,
        a2: GeomPoint,
        b1: GeomPoint,
        b2: GeomPoint
    ): Float {
        if (segmentsIntersect(a1, a2, b1, b2)) return 0f
        return minOf(
            pointToSegmentDistance(a1.x, a1.y, b1.x, b1.y, b2.x, b2.y),
            pointToSegmentDistance(a2.x, a2.y, b1.x, b1.y, b2.x, b2.y),
            pointToSegmentDistance(b1.x, b1.y, a1.x, a1.y, a2.x, a2.y),
            pointToSegmentDistance(b2.x, b2.y, a1.x, a1.y, a2.x, a2.y)
        )
    }

    fun doesEraserHitPath(
        eraserPoints: List<GeomPoint>,
        pathPoints: List<GeomPoint>,
        hitRadius: Float
    ): Boolean {
        if (eraserPoints.isEmpty() || pathPoints.isEmpty()) return false

        val eBox = boundsOf(eraserPoints)
        val pBox = boundsOf(pathPoints)
        if (eBox.right + hitRadius < pBox.left ||
            pBox.right + hitRadius < eBox.left ||
            eBox.bottom + hitRadius < pBox.top ||
            pBox.bottom + hitRadius < eBox.top
        ) {
            return false
        }

        if (eraserPoints.size == 1 && pathPoints.size == 1) {
            val e0 = eraserPoints[0]
            val p0 = pathPoints[0]
            return hypot((e0.x - p0.x).toDouble(), (e0.y - p0.y).toDouble()).toFloat() <= hitRadius
        }
        if (eraserPoints.size == 1) {
            val e0 = eraserPoints[0]
            for (j in 0 until pathPoints.size - 1) {
                if (pointToSegmentDistance(
                        e0.x, e0.y,
                        pathPoints[j].x, pathPoints[j].y,
                        pathPoints[j + 1].x, pathPoints[j + 1].y
                    ) <= hitRadius
                ) {
                    return true
                }
            }
            return false
        }
        if (pathPoints.size == 1) {
            val p0 = pathPoints[0]
            for (i in 0 until eraserPoints.size - 1) {
                if (pointToSegmentDistance(
                        p0.x, p0.y,
                        eraserPoints[i].x, eraserPoints[i].y,
                        eraserPoints[i + 1].x, eraserPoints[i + 1].y
                    ) <= hitRadius
                ) {
                    return true
                }
            }
            return false
        }

        for (i in 0 until eraserPoints.size - 1) {
            val e1 = eraserPoints[i]
            val e2 = eraserPoints[i + 1]
            val eMinX = min(e1.x, e2.x) - hitRadius
            val eMaxX = max(e1.x, e2.x) + hitRadius
            val eMinY = min(e1.y, e2.y) - hitRadius
            val eMaxY = max(e1.y, e2.y) + hitRadius
            if (eMaxX < pBox.left || eMinX > pBox.right || eMaxY < pBox.top || eMinY > pBox.bottom) {
                continue
            }
            for (j in 0 until pathPoints.size - 1) {
                val p1 = pathPoints[j]
                val p2 = pathPoints[j + 1]
                if (max(p1.x, p2.x) < eMinX || min(p1.x, p2.x) > eMaxX ||
                    max(p1.y, p2.y) < eMinY || min(p1.y, p2.y) > eMaxY
                ) {
                    continue
                }
                if (segmentToSegmentDistance(e1, e2, p1, p2) <= hitRadius) {
                    return true
                }
            }
        }
        return false
    }

    fun isPointInPolygon(px: Float, py: Float, polygon: List<GeomPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val crossesY = (yi > py) != (yj > py)
            if (crossesY) {
                val xIntersect = (xj - xi).toDouble() * (py - yi).toDouble() / (yj - yi).toDouble() + xi.toDouble()
                if (px < xIntersect) {
                    inside = !inside
                }
            }
            j = i
        }
        return inside
    }

    fun isPathSelectedByLasso(
        pathPoints: List<GeomPoint>,
        polygon: List<GeomPoint>,
        minFractionInside: Float = 0.5f
    ): Boolean {
        if (pathPoints.isEmpty() || polygon.size < 3) return false
        var insideCount = 0
        var sumX = 0.0
        var sumY = 0.0
        for (pt in pathPoints) {
            if (isPointInPolygon(pt.x, pt.y, polygon)) {
                insideCount++
            }
            sumX += pt.x
            sumY += pt.y
        }
        if (insideCount.toFloat() / pathPoints.size.toFloat() >= minFractionInside) {
            return true
        }
        if (insideCount > 0) {
            val cx = (sumX / pathPoints.size).toFloat()
            val cy = (sumY / pathPoints.size).toFloat()
            return isPointInPolygon(cx, cy, polygon)
        }
        return false
    }

    fun boundingBoxOfPaths(pathsPoints: List<List<GeomPoint>>): GeomRect? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var found = false
        for (pts in pathsPoints) {
            for (pt in pts) {
                found = true
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }
        return if (found) GeomRect(minX, minY, maxX, maxY) else null
    }

    private fun boundsOf(points: List<GeomPoint>): GeomRect {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (pt in points) {
            if (pt.x < minX) minX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x
            if (pt.y > maxY) maxY = pt.y
        }
        return GeomRect(minX, minY, maxX, maxY)
    }
}
