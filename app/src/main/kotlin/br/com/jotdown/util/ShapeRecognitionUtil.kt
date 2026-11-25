package br.com.jotdown.util

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

enum class RecognizedShapeType { LINE, ARROW, CIRCLE, ELLIPSE, TRIANGLE, RECTANGLE }

data class RecognizedShape(
    val type: RecognizedShapeType,
    val points: List<GeomPoint>
)

/**
 * Pure-Kotlin single-stroke geometric shape recognition for Hold-to-Snap.
 */
object ShapeRecognitionUtil {

    fun rdpSimplify(points: List<GeomPoint>, epsilon: Float): List<GeomPoint> {
        if (points.size < 3 || epsilon <= 0f) return points
        val first = points.first()
        val last = points.last()
        var maxDist = -1f
        var splitIdx = -1
        for (i in 1 until points.lastIndex) {
            val d = StrokeGeometryUtil.pointToSegmentDistance(
                points[i].x, points[i].y,
                first.x, first.y,
                last.x, last.y
            )
            if (d > maxDist) {
                maxDist = d
                splitIdx = i
            }
        }
        return if (maxDist > epsilon && splitIdx in 1 until points.lastIndex) {
            val left = rdpSimplify(points.subList(0, splitIdx + 1), epsilon)
            val right = rdpSimplify(points.subList(splitIdx, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    fun recognize(rawPoints: List<GeomPoint>): RecognizedShape? {
        if (rawPoints.size < 4) return null

        val cumLen = FloatArray(rawPoints.size)
        var minX = rawPoints[0].x
        var maxX = rawPoints[0].x
        var minY = rawPoints[0].y
        var maxY = rawPoints[0].y
        for (i in 1 until rawPoints.size) {
            val p = rawPoints[i]
            val prev = rawPoints[i - 1]
            cumLen[i] = cumLen[i - 1] + dist(prev, p)
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val arcLen = cumLen.last()
        val w = maxX - minX
        val h = maxY - minY
        val diag = hypot(w.toDouble(), h.toDouble()).toFloat()
        if (arcLen < 40f || diag < 28f) return null

        val closureDist = dist(rawPoints.first(), rawPoints.last())
        val isClosed = closureDist <= maxOf(36f, 0.26f * diag) && arcLen >= 1.8f * diag
        if (isClosed) {
            return recognizeClosedShape(rawPoints, minX, minY, maxX, maxY, w, h, diag)
        }

        return recognizeOpenShape(rawPoints, cumLen, arcLen)
    }

    private fun recognizeClosedShape(
        rawPoints: List<GeomPoint>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        w: Float,
        h: Float,
        diag: Float
    ): RecognizedShape? {
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val rx = w * 0.5f
        val ry = h * 0.5f

        val ellipseFit = if (rx >= 8f && ry >= 8f) {
            computeEllipseFit(rawPoints, cx, cy, rx, ry)
        } else {
            null
        }

        // Tight ellipse check first so small circles simplified to 4 vertices by RDP
        // are not misclassified as squares/diamonds.
        if (rawPoints.size >= 8 && ellipseFit != null && ellipseFit.first <= 0.11f && ellipseFit.second <= 0.24f) {
            return buildCircleOrEllipse(cx, cy, rx, ry)
        }

        val epsilon = maxOf(8f, 0.055f * diag)
        val verts = rdpSimplify(rawPoints, epsilon).toMutableList()
        if (verts.size > 1 && dist(verts.first(), verts.last()) <= maxOf(36f, 0.26f * diag)) {
            verts.removeAt(verts.lastIndex)
        }

        // Merge ultra-close adjacent vertices left from closure gaps
        var changed = true
        while (changed && verts.size > 3) {
            changed = false
            for (i in verts.indices) {
                val nextIdx = (i + 1) % verts.size
                if (dist(verts[i], verts[nextIdx]) < epsilon) {
                    verts.removeAt(nextIdx)
                    changed = true
                    break
                }
            }
        }

        // Cyclic near-collinear vertex merge (turn angle < 25 deg)
        val minTurnCos = cos(25.0 * PI / 180.0).toFloat()
        changed = true
        while (changed && verts.size > 3) {
            changed = false
            for (i in verts.indices) {
                val prev = verts[(i - 1 + verts.size) % verts.size]
                val cur = verts[i]
                val next = verts[(i + 1) % verts.size]
                val v1x = (cur.x - prev.x).toDouble()
                val v1y = (cur.y - prev.y).toDouble()
                val v2x = (next.x - cur.x).toDouble()
                val v2y = (next.y - cur.y).toDouble()
                val l1 = hypot(v1x, v1y)
                val l2 = hypot(v2x, v2y)
                if (l1 <= 1e-4 || l2 <= 1e-4) {
                    verts.removeAt(i)
                    changed = true
                    break
                }
                val cosTurn = ((v1x * v2x + v1y * v2y) / (l1 * l2)).toFloat()
                if (cosTurn > minTurnCos) {
                    verts.removeAt(i)
                    changed = true
                    break
                }
            }
        }

        if (verts.size == 3) {
            val v0 = verts[0]
            val v1 = verts[1]
            val v2 = verts[2]
            val doubleArea = abs((v1.x - v0.x) * (v2.y - v0.y) - (v2.x - v0.x) * (v1.y - v0.y))
            val minAltitude = minOf(
                StrokeGeometryUtil.pointToSegmentDistance(v0.x, v0.y, v1.x, v1.y, v2.x, v2.y),
                StrokeGeometryUtil.pointToSegmentDistance(v1.x, v1.y, v2.x, v2.y, v0.x, v0.y),
                StrokeGeometryUtil.pointToSegmentDistance(v2.x, v2.y, v0.x, v0.y, v1.x, v1.y)
            )
            if (doubleArea >= 160f && minAltitude >= 8f) {
                val meanDist = meanDistanceToPolygonEdges(rawPoints, verts)
                if (meanDist <= maxOf(10f, 0.07f * diag)) {
                    return RecognizedShape(RecognizedShapeType.TRIANGLE, listOf(v0, v1, v2, v0))
                }
            }
        }

        if (verts.size == 4) {
            val allAnglesValid = (0 until 4).all { idx ->
                val prev = verts[(idx + 3) % 4]
                val cur = verts[idx]
                val next = verts[(idx + 1) % 4]
                val deg = interiorAngleDeg(prev, cur, next)
                deg in 65f..115f
            }
            if (allAnglesValid) {
                val meanDist = meanDistanceToPolygonEdges(rawPoints, verts)
                if (meanDist <= maxOf(10f, 0.07f * diag)) {
                    return buildSnappedRectangle(verts, rawPoints, minX, minY, maxX, maxY)
                }
            }
        }

        if (ellipseFit != null && ellipseFit.first <= 0.16f && ellipseFit.second <= 0.35f) {
            return buildCircleOrEllipse(cx, cy, rx, ry)
        }

        return null
    }

    private fun computeEllipseFit(
        rawPoints: List<GeomPoint>,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float
    ): Pair<Float, Float> {
        var sumErr = 0.0
        var maxErr = 0f
        for (p in rawPoints) {
            val dx = (p.x - cx) / rx
            val dy = (p.y - cy) / ry
            val rNorm = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val err = abs(rNorm - 1f)
            sumErr += err
            if (err > maxErr) maxErr = err
        }
        return (sumErr / rawPoints.size).toFloat() to maxErr
    }

    private fun buildCircleOrEllipse(cx: Float, cy: Float, rx: Float, ry: Float): RecognizedShape {
        val ratio = minOf(rx, ry) / maxOf(rx, ry)
        val isCircle = ratio >= 0.80f
        val rFinalX = if (isCircle) (rx + ry) * 0.5f else rx
        val rFinalY = if (isCircle) (rx + ry) * 0.5f else ry
        val count = 64
        val pts = ArrayList<GeomPoint>(count + 1)
        for (i in 0 until count) {
            val theta = 2.0 * PI * i / count
            pts.add(
                GeomPoint(
                    x = cx + (rFinalX * cos(theta)).toFloat(),
                    y = cy + (rFinalY * sin(theta)).toFloat()
                )
            )
        }
        pts.add(pts[0])
        return RecognizedShape(
            type = if (isCircle) RecognizedShapeType.CIRCLE else RecognizedShapeType.ELLIPSE,
            points = pts
        )
    }

    private fun buildSnappedRectangle(
        verts: List<GeomPoint>,
        rawPoints: List<GeomPoint>,
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float
    ): RecognizedShape {
        var longestA = verts[0]
        var longestB = verts[1]
        var longestLen = -1f
        for (i in 0 until 4) {
            val a = verts[i]
            val b = verts[(i + 1) % 4]
            val d = dist(a, b)
            if (d > longestLen) {
                longestLen = d
                longestA = a
                longestB = b
            }
        }
        val angleDeg = (atan2((longestB.y - longestA.y).toDouble(), (longestB.x - longestA.x).toDouble()) * 180.0 / PI).toFloat()
        val mod90 = ((angleDeg % 90f) + 90f) % 90f
        val axisDev = minOf(mod90, 90f - mod90)

        return if (axisDev <= 10f) {
            val rw = maxX - minX
            val rh = maxY - minY
            if (rw > 0f && rh > 0f && minOf(rw, rh) / maxOf(rw, rh) >= 0.85f) {
                val side = (rw + rh) * 0.5f
                val cx = (minX + maxX) * 0.5f
                val cy = (minY + maxY) * 0.5f
                val half = side * 0.5f
                val c0 = GeomPoint(cx - half, cy - half)
                val c1 = GeomPoint(cx + half, cy - half)
                val c2 = GeomPoint(cx + half, cy + half)
                val c3 = GeomPoint(cx - half, cy + half)
                RecognizedShape(RecognizedShapeType.RECTANGLE, listOf(c0, c1, c2, c3, c0))
            } else {
                val c0 = GeomPoint(minX, minY)
                val c1 = GeomPoint(maxX, minY)
                val c2 = GeomPoint(maxX, maxY)
                val c3 = GeomPoint(minX, maxY)
                RecognizedShape(RecognizedShapeType.RECTANGLE, listOf(c0, c1, c2, c3, c0))
            }
        } else {
            val len = longestLen.coerceAtLeast(1e-4f)
            val ux = (longestB.x - longestA.x) / len
            val uy = (longestB.y - longestA.y) / len
            val vx = -uy
            val vy = ux

            var uMin = Float.POSITIVE_INFINITY
            var uMax = Float.NEGATIVE_INFINITY
            var vMin = Float.POSITIVE_INFINITY
            var vMax = Float.NEGATIVE_INFINITY
            for (p in rawPoints) {
                val pu = p.x * ux + p.y * uy
                val pv = p.x * vx + p.y * vy
                if (pu < uMin) uMin = pu
                if (pu > uMax) uMax = pu
                if (pv < vMin) vMin = pv
                if (pv > vMax) vMax = pv
            }
            val rw = uMax - uMin
            val rh = vMax - vMin
            if (rw > 0f && rh > 0f && minOf(rw, rh) / maxOf(rw, rh) >= 0.85f) {
                val side = (rw + rh) * 0.5f
                val uMid = (uMin + uMax) * 0.5f
                val vMid = (vMin + vMax) * 0.5f
                val half = side * 0.5f
                uMin = uMid - half
                uMax = uMid + half
                vMin = vMid - half
                vMax = vMid + half
            }
            fun fromUv(u: Float, v: Float) = GeomPoint(u * ux + v * vx, u * uy + v * vy)
            val c0 = fromUv(uMin, vMin)
            val c1 = fromUv(uMax, vMin)
            val c2 = fromUv(uMax, vMax)
            val c3 = fromUv(uMin, vMax)
            RecognizedShape(RecognizedShapeType.RECTANGLE, listOf(c0, c1, c2, c3, c0))
        }
    }

    private fun recognizeOpenShape(
        rawPoints: List<GeomPoint>,
        cumLen: FloatArray,
        arcLen: Float
    ): RecognizedShape? {
        val first = rawPoints.first()
        val last = rawPoints.last()
        val chordLen = dist(first, last)

        // 1. Straight line check
        if (chordLen >= 35f && chordLen / arcLen >= 0.92f) {
            val maxPerp = rawPoints.maxOf {
                StrokeGeometryUtil.pointToSegmentDistance(it.x, it.y, first.x, first.y, last.x, last.y)
            }
            if (maxPerp <= maxOf(9f, 0.055f * chordLen)) {
                val snappedEnd = snapEndpointAxis(first, last)
                return RecognizedShape(RecognizedShapeType.LINE, listOf(first, snappedEnd))
            }
        }

        // 2. Arrow check: tip >= 55% along arc length maximizing distance from first
        var tipIdx = -1
        var bestTipDist = -1f
        for (i in 1 until rawPoints.lastIndex) {
            if (cumLen[i] >= 0.55f * arcLen) {
                val d = dist(first, rawPoints[i])
                if (d > bestTipDist + 1e-3f) {
                    bestTipDist = d
                    tipIdx = i
                }
            }
        }
        if (tipIdx <= 0) return null

        val tip = rawPoints[tipIdx]
        val shaftArc = cumLen[tipIdx]
        val shaftChord = dist(first, tip)
        if (shaftChord < 40f || shaftArc <= 0f || shaftChord / shaftArc < 0.90f) return null

        var maxShaftDev = 0f
        for (i in 0..tipIdx) {
            val d = StrokeGeometryUtil.pointToSegmentDistance(
                rawPoints[i].x, rawPoints[i].y,
                first.x, first.y,
                tip.x, tip.y
            )
            if (d > maxShaftDev) maxShaftDev = d
        }
        if (maxShaftDev > maxOf(10f, 0.065f * shaftChord)) return null

        val headLen = dist(tip, last)
        if (headLen < 8f || headLen > 0.45f * shaftChord) return null

        val ux = (tip.x - first.x) / shaftChord
        val uy = (tip.y - first.y) / shaftChord
        val tx = (last.x - tip.x) / headLen
        val ty = (last.y - tip.y) / headLen
        if (ux * tx + uy * ty >= -0.25f) return null

        val snappedTip = snapEndpointAxis(first, tip)
        val snappedLen = dist(first, snappedTip).coerceAtLeast(1e-4f)
        val sux = (snappedTip.x - first.x) / snappedLen
        val suy = (snappedTip.y - first.y) / snappedLen
        val barbLen = (0.18f * snappedLen).coerceIn(14f, 36f)
        val cos30 = cos(PI / 6.0).toFloat()
        val sin30 = 0.5f
        val bx = -sux
        val by = -suy
        val barbLeft = GeomPoint(
            snappedTip.x + barbLen * (bx * cos30 - by * sin30),
            snappedTip.y + barbLen * (bx * sin30 + by * cos30)
        )
        val barbRight = GeomPoint(
            snappedTip.x + barbLen * (bx * cos30 + by * sin30),
            snappedTip.y + barbLen * (-bx * sin30 + by * cos30)
        )
        return RecognizedShape(
            type = RecognizedShapeType.ARROW,
            points = listOf(first, snappedTip, barbLeft, snappedTip, barbRight)
        )
    }

    private fun snapEndpointAxis(start: GeomPoint, end: GeomPoint): GeomPoint {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val angleDeg = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
        val absAngle = abs(angleDeg)
        return when {
            absAngle <= 5f || abs(absAngle - 180f) <= 5f -> GeomPoint(end.x, start.y)
            abs(absAngle - 90f) <= 5f -> GeomPoint(start.x, end.y)
            else -> end
        }
    }

    private fun interiorAngleDeg(prev: GeomPoint, cur: GeomPoint, next: GeomPoint): Float {
        val ax = (prev.x - cur.x).toDouble()
        val ay = (prev.y - cur.y).toDouble()
        val bx = (next.x - cur.x).toDouble()
        val by = (next.y - cur.y).toDouble()
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la <= 1e-6 || lb <= 1e-6) return 0f
        val cosA = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1.0, 1.0)
        return (acos(cosA) * 180.0 / PI).toFloat()
    }

    private fun meanDistanceToPolygonEdges(rawPoints: List<GeomPoint>, poly: List<GeomPoint>): Float {
        var sum = 0.0
        for (p in rawPoints) {
            var best = Float.POSITIVE_INFINITY
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                val d = StrokeGeometryUtil.pointToSegmentDistance(p.x, p.y, a.x, a.y, b.x, b.y)
                if (d < best) best = d
            }
            sum += best
        }
        return (sum / rawPoints.size).toFloat()
    }

    private fun dist(a: GeomPoint, b: GeomPoint): Float =
        hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
}
