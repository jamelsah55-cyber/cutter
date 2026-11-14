package com.example.domain.vector

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import com.example.domain.model.DesignElement
import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import com.example.domain.model.VectorShapeType
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PathSimplifyResult(
    val element: DesignElement,
    val nodesBefore: Int,
    val nodesAfter: Int
)

/**
 * Vector path laboratory providing millimeter-accurate curve & node simplification,
 * Chaikin curve smoothing, intelligent open-curve closing, freehand stroke vectorization,
 * and sticker die-cut contour offset expansion.
 */
class PathEditingEngine(private val geometryEngine: VectorGeometryEngine) {

    fun inspectStats(element: DesignElement): Pair<Int, Int> {
        val polys = geometryEngine.bakeToEditablePaths(element).normalizedPaths
        val totalNodes = polys.sumOf { it.points.size }
        val openCount = polys.count { !it.closed && it.points.size >= 2 }
        return totalNodes to openCount
    }

    /**
     * Simplifies nodes/curves using Ramer-Douglas-Peucker in millimeter scale so toleranceMm
     * reflects true physical cutting precision regardless of element aspect ratio.
     */
    fun simplifyElement(
        element: DesignElement,
        toleranceMm: Float,
        targetPolylineIndex: Int? = null
    ): PathSimplifyResult {
        val baked = geometryEngine.bakeToEditablePaths(element)
        val w = baked.widthMm.coerceAtLeast(1f)
        val h = baked.heightMm.coerceAtLeast(1f)
        val tol = toleranceMm.coerceIn(0.05f, 10f)

        val nodesBefore = baked.normalizedPaths.sumOf { it.points.size }
        val simplifiedPaths = baked.normalizedPaths.mapIndexed { idx, poly ->
            if (targetPolylineIndex != null && idx != targetPolylineIndex) {
                poly
            } else {
                val mmPts = poly.points.map { Point2D(it.xMm * w, it.yMm * h) }
                val deduped = removeNearDuplicates(mmPts, minDistMm = 0.05f, closed = poly.closed)
                val reduced = if (poly.closed) {
                    simplifyClosedMm(deduped, tol)
                } else {
                    simplifyOpenMm(deduped, tol)
                }
                Polyline2D(
                    points = reduced.map { Point2D(it.xMm / w, it.yMm / h) },
                    closed = poly.closed
                )
            }
        }
        val nodesAfter = simplifiedPaths.sumOf { it.points.size }
        return PathSimplifyResult(
            element = baked.copy(normalizedPaths = simplifiedPaths),
            nodesBefore = nodesBefore,
            nodesAfter = nodesAfter
        )
    }

    /**
     * Applies one pass of Chaikin corner-cutting curve smoothing in millimeter space
     * while capping vertex count to keep plotter throughput responsive.
     */
    fun smoothElement(
        element: DesignElement,
        targetPolylineIndex: Int? = null
    ): Pair<DesignElement, Int> {
        val baked = geometryEngine.bakeToEditablePaths(element)
        val w = baked.widthMm.coerceAtLeast(1f)
        val h = baked.heightMm.coerceAtLeast(1f)

        val smoothedPaths = baked.normalizedPaths.mapIndexed { idx, poly ->
            if (targetPolylineIndex != null && idx != targetPolylineIndex) {
                poly
            } else if (poly.points.size < 3 || poly.points.size > 220) {
                poly
            } else {
                val mmPts = poly.points.map { Point2D(it.xMm * w, it.yMm * h) }
                val nextMm = if (poly.closed) smoothClosedChaikin(mmPts) else smoothOpenChaikin(mmPts)
                Polyline2D(
                    points = nextMm.map { Point2D(it.xMm / w, it.yMm / h) },
                    closed = poly.closed
                )
            }
        }
        val newTotal = smoothedPaths.sumOf { it.points.size }
        return baked.copy(normalizedPaths = smoothedPaths) to newTotal
    }

    /**
     * Closes open curves cleanly, merging near-coincident start/end vertices so the plotter
     * does not perform a zero-length stitch at the seam.
     */
    fun closeOpenCurves(
        element: DesignElement,
        targetPolylineIndex: Int? = null,
        mergeThresholdMm: Float = 1.8f
    ): Pair<DesignElement, Int> {
        val baked = geometryEngine.bakeToEditablePaths(element)
        val w = baked.widthMm.coerceAtLeast(1f)
        val h = baked.heightMm.coerceAtLeast(1f)
        var newlyClosed = 0

        val updated = baked.normalizedPaths.mapIndexed { idx, poly ->
            if ((targetPolylineIndex != null && idx != targetPolylineIndex) || poly.closed) {
                poly
            } else if (poly.points.size >= 3) {
                newlyClosed++
                val first = poly.points.first()
                val last = poly.points.last()
                val gapMm = hypot((last.xMm - first.xMm) * w, (last.yMm - first.yMm) * h)
                val trimmed = if (poly.points.size > 3 && gapMm <= mergeThresholdMm) {
                    poly.points.dropLast(1)
                } else {
                    poly.points
                }
                poly.copy(points = trimmed, closed = true)
            } else {
                poly
            }
        }
        return baked.copy(normalizedPaths = updated) to newlyClosed
    }

    /**
     * Converts a finger-drawn millimeter trajectory into a clean, simplified CUSTOM_PATH element.
     */
    fun buildFreehandElement(
        worldPointsMm: List<Point2D>,
        simplifyToleranceMm: Float = 0.7f,
        autoCloseIfNearMm: Float = 9f
    ): DesignElement? {
        if (worldPointsMm.size < 2) return null
        val cleaned = removeNearDuplicates(worldPointsMm, minDistMm = 0.6f, closed = false)
        if (cleaned.size < 2) return null

        val shouldClose = cleaned.size >= 4 && hypot(
            cleaned.last().xMm - cleaned.first().xMm,
            cleaned.last().yMm - cleaned.first().yMm
        ) <= autoCloseIfNearMm

        val trimmedForClose = if (shouldClose) cleaned.dropLast(1) else cleaned
        val simplified = if (shouldClose && trimmedForClose.size >= 3) {
            simplifyClosedMm(trimmedForClose, simplifyToleranceMm)
        } else {
            simplifyOpenMm(cleaned, simplifyToleranceMm)
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        simplified.forEach { pt ->
            minX = min(minX, pt.xMm)
            minY = min(minY, pt.yMm)
            maxX = max(maxX, pt.xMm)
            maxY = max(maxY, pt.yMm)
        }
        val w = (maxX - minX).coerceAtLeast(4f)
        val h = (maxY - minY).coerceAtLeast(4f)
        val norm = simplified.map { pt ->
            Point2D(((pt.xMm - minX) / w).coerceIn(0f, 1f), ((pt.yMm - minY) / h).coerceIn(0f, 1f))
        }

        return DesignElement(
            id = UUID.randomUUID().toString(),
            name = "رسم حر",
            type = VectorShapeType.CUSTOM_PATH,
            xMm = minX,
            yMm = minY,
            widthMm = w,
            heightMm = h,
            normalizedPaths = listOf(Polyline2D(norm, closed = shouldClose)),
            strokeColorHex = 0xFF00D4B8L
        )
    }

    /**
     * Generates an expanded die-cut sticker contour outline around an element by expanding each
     * contour (closed polygon outward normal + rounded corners, or open stroke capsule) and
     * fusing overlapping regions with Skia PathOps.UNION.
     */
    fun createContourOffsetElement(
        source: DesignElement,
        offsetMm: Float
    ): DesignElement? {
        val clampedOffset = offsetMm.coerceIn(0.8f, 20f)
        val scale = 10f
        val worldPolys = geometryEngine.buildWorldPolylines(source)
        if (worldPolys.isEmpty()) return null

        val expandedPath = Path()
        var hasExpanded = false

        worldPolys.forEach { poly ->
            val cleanPts = removeNearDuplicates(poly.points, minDistMm = 0.08f, closed = poly.closed)
            if (cleanPts.size < 2) return@forEach
            val offsetLoops = if (poly.closed && cleanPts.size >= 3) {
                listOf(buildClosedPolygonOffset(cleanPts, clampedOffset))
            } else {
                buildOpenStrokeCapsules(cleanPts, clampedOffset)
            }
            offsetLoops.forEach { loop ->
                if (loop.size >= 3) {
                    val p = Path()
                    loop.forEachIndexed { idx, pt ->
                        val sx = pt.xMm * scale
                        val sy = pt.yMm * scale
                        if (idx == 0) p.moveTo(sx, sy) else p.lineTo(sx, sy)
                    }
                    p.close()
                    if (!hasExpanded) {
                        expandedPath.set(p)
                        hasExpanded = true
                    } else {
                        expandedPath.op(p, Path.Op.UNION)
                    }
                }
            }
        }

        if (!hasExpanded) return null

        val bounds = RectF()
        expandedPath.computeBounds(bounds, true)
        if (bounds.width() < 2f || bounds.height() < 2f) return null

        val outW = (bounds.width() / scale).coerceAtLeast(4f)
        val outH = (bounds.height() / scale).coerceAtLeast(4f)

        val extractedPolys = mutableListOf<Polyline2D>()
        val pm = PathMeasure(expandedPath, false)
        val pos = FloatArray(2)
        do {
            val len = pm.length
            if (len > 2f) {
                val steps = (len / 4.5f).toInt().coerceIn(12, 260)
                val mmPts = ArrayList<Point2D>(steps)
                for (i in 0 until steps) {
                    val d = (i.toFloat() / steps) * len
                    if (pm.getPosTan(d, pos, null)) {
                        mmPts.add(Point2D((pos[0] - bounds.left) / scale, (pos[1] - bounds.top) / scale))
                    }
                }
                val simplifiedMm = simplifyClosedMm(mmPts, toleranceMm = 0.25f)
                if (simplifiedMm.size >= 3) {
                    extractedPolys.add(
                        Polyline2D(
                            points = simplifiedMm.map { pt ->
                                Point2D((pt.xMm / outW).coerceIn(0f, 1f), (pt.yMm / outH).coerceIn(0f, 1f))
                            },
                            closed = true
                        )
                    )
                }
            }
        } while (pm.nextContour())

        if (extractedPolys.isEmpty()) return null

        return DesignElement(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (حد +${clampedOffset.roundToInt()}مم)",
            type = VectorShapeType.CUSTOM_PATH,
            xMm = bounds.left / scale,
            yMm = bounds.top / scale,
            widthMm = outW,
            heightMm = outH,
            normalizedPaths = extractedPolys,
            strokeColorHex = 0xFFFF4D6DL,
            fillColorHex = null
        )
    }

    private fun buildClosedPolygonOffset(pts: List<Point2D>, offsetMm: Float): List<Point2D> {
        val n = pts.size
        // Compute signed area (2 * A); positive = CW in screen Y-down coordinates
        var signedArea2 = 0f
        for (i in 0 until n) {
            val a = pts[i]
            val b = pts[(i + 1) % n]
            signedArea2 += a.xMm * b.yMm - b.xMm * a.yMm
        }
        // In screen Y-down coords:
        // If signedArea2 > 0 (CW: top goes right (1,0)), outward normal is (dy, -dx) -> (0, -1) UP
        // If signedArea2 < 0 (CCW), outward normal is (-dy, dx)
        val sign = if (signedArea2 >= 0f) 1f else -1f
        val out = ArrayList<Point2D>(n * 3)

        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]
            val cur = pts[i]
            val next = pts[(i + 1) % n]

            val dx1 = cur.xMm - prev.xMm
            val dy1 = cur.yMm - prev.yMm
            val l1 = hypot(dx1, dy1).coerceAtLeast(1e-4f)
            val n1x = sign * (dy1 / l1)
            val n1y = sign * (-dx1 / l1)

            val dx2 = next.xMm - cur.xMm
            val dy2 = next.yMm - cur.yMm
            val l2 = hypot(dx2, dy2).coerceAtLeast(1e-4f)
            val n2x = sign * (dy2 / l2)
            val n2y = sign * (-dx2 / l2)

            val a1 = kotlin.math.atan2(n1y, n1x)
            val a2 = kotlin.math.atan2(n2y, n2x)
            var sweep = a2 - a1
            if (sign > 0f && sweep < 0f) sweep += (2f * kotlin.math.PI.toFloat())
            if (sign < 0f && sweep > 0f) sweep -= (2f * kotlin.math.PI.toFloat())

            if (abs(sweep) > 0.25f && abs(sweep) < kotlin.math.PI.toFloat() * 1.15f) {
                val arcSteps = 4
                for (s in 0..arcSteps) {
                    val ang = a1 + sweep * (s.toFloat() / arcSteps)
                    out.add(
                        Point2D(
                            cur.xMm + offsetMm * kotlin.math.cos(ang),
                            cur.yMm + offsetMm * kotlin.math.sin(ang)
                        )
                    )
                }
            } else {
                out.add(
                    Point2D(
                        cur.xMm + ((n1x + n2x) * 0.5f) * offsetMm,
                        cur.yMm + ((n1y + n2y) * 0.5f) * offsetMm
                    )
                )
            }
        }
        return out
    }

    private fun buildOpenStrokeCapsules(pts: List<Point2D>, offsetMm: Float): List<List<Point2D>> {
        val capsules = mutableListOf<List<Point2D>>()
        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            val dx = b.xMm - a.xMm
            val dy = b.yMm - a.yMm
            val baseAngle = kotlin.math.atan2(dy, dx)
            val cap = ArrayList<Point2D>(14)
            for (s in 0..6) {
                val ang = baseAngle + (kotlin.math.PI.toFloat() / 2f) + (s / 6f) * kotlin.math.PI.toFloat()
                cap.add(Point2D(a.xMm + offsetMm * kotlin.math.cos(ang), a.yMm + offsetMm * kotlin.math.sin(ang)))
            }
            for (s in 0..6) {
                val ang = baseAngle - (kotlin.math.PI.toFloat() / 2f) + (s / 6f) * kotlin.math.PI.toFloat()
                cap.add(Point2D(b.xMm + offsetMm * kotlin.math.cos(ang), b.yMm + offsetMm * kotlin.math.sin(ang)))
            }
            capsules.add(cap)
        }
        return capsules
    }

    private fun removeNearDuplicates(
        pts: List<Point2D>,
        minDistMm: Float,
        closed: Boolean
    ): List<Point2D> {
        if (pts.size < 2) return pts
        val out = ArrayList<Point2D>(pts.size)
        out.add(pts.first())
        for (i in 1 until pts.size) {
            val prev = out.last()
            val cur = pts[i]
            if (hypot(cur.xMm - prev.xMm, cur.yMm - prev.yMm) >= minDistMm) {
                out.add(cur)
            }
        }
        if (closed && out.size > 3) {
            val first = out.first()
            val last = out.last()
            if (hypot(last.xMm - first.xMm, last.yMm - first.yMm) < minDistMm) {
                out.removeAt(out.lastIndex)
            }
        }
        return if (out.size >= 2) out else pts
    }

    private fun simplifyClosedMm(pts: List<Point2D>, toleranceMm: Float): List<Point2D> {
        if (pts.size <= 4) return pts
        // Find vertex farthest from pts[0] to split closed loop into two well-conditioned open halves
        val p0 = pts.first()
        var splitIdx = pts.size / 2
        var maxDist = -1f
        for (i in 1 until pts.lastIndex) {
            val d = hypot(pts[i].xMm - p0.xMm, pts[i].yMm - p0.yMm)
            if (d > maxDist) {
                maxDist = d
                splitIdx = i
            }
        }
        val firstHalf = douglasPeuckerOpen(pts.subList(0, splitIdx + 1), toleranceMm)
        val secondHalf = douglasPeuckerOpen(pts.subList(splitIdx, pts.size) + p0, toleranceMm)
        val merged = firstHalf.dropLast(1) + secondHalf.dropLast(1)
        return if (merged.size >= 3) merged else pts
    }

    private fun simplifyOpenMm(pts: List<Point2D>, toleranceMm: Float): List<Point2D> {
        if (pts.size <= 2) return pts
        val res = douglasPeuckerOpen(pts, toleranceMm)
        return if (res.size >= 2) res else listOf(pts.first(), pts.last())
    }

    private fun douglasPeuckerOpen(pts: List<Point2D>, epsilon: Float): List<Point2D> {
        if (pts.size < 3) return pts
        val first = pts.first()
        val last = pts.last()
        var maxDist = 0f
        var maxIdx = 0
        for (i in 1 until pts.lastIndex) {
            val d = perpendicularDistance(pts[i], first, last)
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }
        return if (maxDist > epsilon) {
            val left = douglasPeuckerOpen(pts.subList(0, maxIdx + 1), epsilon)
            val right = douglasPeuckerOpen(pts.subList(maxIdx, pts.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(p: Point2D, a: Point2D, b: Point2D): Float {
        val dx = b.xMm - a.xMm
        val dy = b.yMm - a.yMm
        val len = hypot(dx, dy)
        if (len < 1e-5f) return hypot(p.xMm - a.xMm, p.yMm - a.yMm)
        return abs(dy * p.xMm - dx * p.yMm + b.xMm * a.yMm - b.yMm * a.xMm) / len
    }

    private fun smoothClosedChaikin(pts: List<Point2D>): List<Point2D> {
        val n = pts.size
        val out = ArrayList<Point2D>(n * 2)
        for (i in 0 until n) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % n]
            out.add(Point2D(0.75f * p0.xMm + 0.25f * p1.xMm, 0.75f * p0.yMm + 0.25f * p1.yMm))
            out.add(Point2D(0.25f * p0.xMm + 0.75f * p1.xMm, 0.25f * p0.yMm + 0.75f * p1.yMm))
        }
        return out
    }

    private fun smoothOpenChaikin(pts: List<Point2D>): List<Point2D> {
        if (pts.size < 3) return pts
        val out = ArrayList<Point2D>(pts.size * 2)
        out.add(pts.first())
        for (i in 0 until pts.lastIndex) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            out.add(Point2D(0.75f * p0.xMm + 0.25f * p1.xMm, 0.75f * p0.yMm + 0.25f * p1.yMm))
            out.add(Point2D(0.25f * p0.xMm + 0.75f * p1.xMm, 0.25f * p0.yMm + 0.75f * p1.yMm))
        }
        out.add(pts.last())
        return out
    }
}
