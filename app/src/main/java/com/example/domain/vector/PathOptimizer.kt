package com.example.domain.vector

import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import kotlin.math.hypot
import kotlin.math.roundToInt

data class OptimizedPathsResult(
    val polylines: List<Polyline2D>,
    val rawTravelDistanceMm: Float,
    val optimizedTravelDistanceMm: Float,
    val savedTravelPercent: Int
)

/**
 * Optimizes cutting order via Nearest-Neighbor TSP (with open-path reversal)
 * and applies drag-knife Overcut compensation on closed shapes so vinyl corners release cleanly.
 */
object PathOptimizer {

    fun optimizeAndCompensate(
        input: List<Polyline2D>,
        optimizeOrder: Boolean,
        overcutMm: Float
    ): OptimizedPathsResult {
        val valid = input.filter { it.points.size >= 2 }
        if (valid.isEmpty()) {
            return OptimizedPathsResult(emptyList(), 0f, 0f, 0)
        }

        val rawTravel = computeTravelDistance(valid)
        val ordered = if (optimizeOrder) sortNearestNeighbor(valid) else valid
        val optTravel = computeTravelDistance(ordered)

        val compensated = if (overcutMm > 0.05f) {
            ordered.map { applyOvercut(it, overcutMm.coerceIn(0f, 2f)) }
        } else {
            ordered
        }

        val savedPct = if (rawTravel > 1f && optTravel < rawTravel) {
            (((rawTravel - optTravel) / rawTravel) * 100f).roundToInt().coerceIn(0, 99)
        } else 0

        return OptimizedPathsResult(
            polylines = compensated,
            rawTravelDistanceMm = rawTravel,
            optimizedTravelDistanceMm = optTravel,
            savedTravelPercent = savedPct
        )
    }

    fun sortNearestNeighbor(polylines: List<Polyline2D>): List<Polyline2D> {
        if (polylines.size <= 1) return polylines
        val remaining = polylines.toMutableList()
        val result = ArrayList<Polyline2D>(polylines.size)
        var headX = 0f
        var headY = 0f

        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestReverse = false
            var bestDist = Float.MAX_VALUE

            for (i in remaining.indices) {
                val poly = remaining[i]
                val startPt = poly.points.first()
                val dStart = hypot(startPt.xMm - headX, startPt.yMm - headY)
                if (dStart < bestDist) {
                    bestDist = dStart
                    bestIdx = i
                    bestReverse = false
                }
                if (!poly.closed) {
                    val endPt = poly.points.last()
                    val dEnd = hypot(endPt.xMm - headX, endPt.yMm - headY)
                    if (dEnd < bestDist) {
                        bestDist = dEnd
                        bestIdx = i
                        bestReverse = true
                    }
                }
            }

            val chosen = remaining.removeAt(bestIdx)
            val oriented = if (bestReverse) {
                chosen.copy(points = chosen.points.asReversed())
            } else chosen
            result.add(oriented)

            val endPos = if (oriented.closed) oriented.points.first() else oriented.points.last()
            headX = endPos.xMm
            headY = endPos.yMm
        }
        return result
    }

    /**
     * Converts a closed polyline with Overcut into an explicit point list ending slightly past p0
     * along edge (p0 -> p1) with closed=false so the plotter traces p0 -> ... -> p0 -> p0+overcut.
     */
    fun applyOvercut(poly: Polyline2D, overcutMm: Float): Polyline2D {
        if (!poly.closed || poly.points.size < 3 || overcutMm <= 0.05f) return poly
        val p0 = poly.points[0]
        val p1 = poly.points[1]
        val segLen = hypot(p1.xMm - p0.xMm, p1.yMm - p0.yMm)
        if (segLen < 0.2f) return poly

        val extLen = minOf(overcutMm, segLen * 0.45f)
        val ratio = extLen / segLen
        val overcutPt = Point2D(
            xMm = p0.xMm + (p1.xMm - p0.xMm) * ratio,
            yMm = p0.yMm + (p1.yMm - p0.yMm) * ratio
        )
        return Polyline2D(
            points = poly.points + p0 + overcutPt,
            closed = false
        )
    }

    fun computeTravelDistance(polylines: List<Polyline2D>): Float {
        var travel = 0f
        var lastX = 0f
        var lastY = 0f
        polylines.forEach { poly ->
            if (poly.points.size >= 2) {
                val first = poly.points.first()
                travel += hypot(first.xMm - lastX, first.yMm - lastY)
                val endPt = if (poly.closed) first else poly.points.last()
                lastX = endPt.xMm
                lastY = endPt.yMm
            }
        }
        travel += hypot(lastX, lastY)
        return travel
    }
}
