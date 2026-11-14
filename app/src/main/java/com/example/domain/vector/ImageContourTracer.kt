package com.example.domain.vector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ImageTraceMode(val labelAr: String) {
    SILHOUETTE("تعبئة وحدود (دقيق)"),
    OUTLINE_EDGES("استخراج الحواف (رسم)")
}

data class TracedImageResult(
    val normalizedPaths: List<Polyline2D>,
    val aspectRatio: Float,
    val totalNodes: Int
)

/**
 * High-precision raster-to-vector boundary tracer:
 * - Otsu automatic optimal thresholding
 * - 3x3 despeckle noise filter
 * - 420px max working resolution for sharp logos & Arabic calligraphy
 * - Sub-pixel corner smoothing + corner-preserving Douglas-Peucker simplification
 */
object ImageContourTracer {

    fun loadScaledBitmap(context: Context, uri: Uri, maxSidePx: Int = 420): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            while (max(origW, origH) / sampleSize > maxSidePx * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val scale = maxSidePx.toFloat() / max(decoded.width, decoded.height).toFloat()
            val targetW = (decoded.width * scale).toInt().coerceIn(32, maxSidePx)
            val targetH = (decoded.height * scale).toInt().coerceIn(32, maxSidePx)
            if (decoded.width != targetW || decoded.height != targetH) {
                Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            } else {
                decoded
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Computes the optimal Otsu binary threshold for the bitmap's luminance histogram.
     */
    fun computeOtsuThreshold(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        var validCount = 0
        for (c in pixels) {
            if (Color.alpha(c) >= 64) {
                val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c))
                    .toInt().coerceIn(0, 255)
                hist[lum]++
                validCount++
            }
        }
        if (validCount == 0) return 128

        var sumAll = 0.0
        for (t in 0..255) sumAll += t * hist[t]

        var sumB = 0.0
        var wB = 0
        var maxBetween = -1.0
        var bestSum = 0
        var bestCount = 0

        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = validCount - wB
            if (wF == 0) break
            sumB += (t * hist[t]).toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (between > maxBetween + 1e-6) {
                maxBetween = between
                bestSum = t
                bestCount = 1
            } else if (abs(between - maxBetween) <= 1e-6) {
                bestSum += t
                bestCount++
            }
        }
        val bestT = if (bestCount > 0) bestSum / bestCount else 128
        return bestT.coerceIn(35, 225)
    }

    fun traceBitmap(
        bitmap: Bitmap,
        threshold: Int = 128,
        invert: Boolean = false,
        simplifyEpsilonPx: Float = 1.1f,
        minNoiseFilter: Int = 6,
        traceMode: ImageTraceMode = ImageTraceMode.SILHOUETTE
    ): TracedImageResult {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val lums = IntArray(w * h)
        val alphas = IntArray(w * h)
        val compositedLums = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = Color.alpha(c)
            alphas[i] = a
            val rawLum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c))
            lums[i] = rawLum.toInt().coerceIn(0, 255)
            compositedLums[i] = ((rawLum * a + 255f * (255 - a)) / 255f).toInt().coerceIn(0, 255)
        }

        val mask = BooleanArray(w * h)
        when (traceMode) {
            ImageTraceMode.SILHOUETTE -> {
                for (i in pixels.indices) {
                    mask[i] = if (alphas[i] < 64) {
                        false
                    } else {
                        if (invert) lums[i] >= threshold else lums[i] < threshold
                    }
                }
            }
            ImageTraceMode.OUTLINE_EDGES -> {
                val edgeLimit = (255 - threshold).coerceIn(25, 220)
                for (y in 1 until h - 1) {
                    for (x in 1 until w - 1) {
                        val idx = y * w + x
                        val hasOpaqueNeighbor = alphas[idx] >= 64 ||
                            alphas[(y - 1) * w + x] >= 64 ||
                            alphas[(y + 1) * w + x] >= 64 ||
                            alphas[y * w + (x - 1)] >= 64 ||
                            alphas[y * w + (x + 1)] >= 64
                        if (!hasOpaqueNeighbor) {
                            mask[idx] = false
                            continue
                        }
                        val gx = -compositedLums[(y - 1) * w + (x - 1)] + compositedLums[(y - 1) * w + (x + 1)] -
                            2 * compositedLums[y * w + (x - 1)] + 2 * compositedLums[y * w + (x + 1)] -
                            compositedLums[(y + 1) * w + (x - 1)] + compositedLums[(y + 1) * w + (x + 1)]
                        val gy = -compositedLums[(y - 1) * w + (x - 1)] - 2 * compositedLums[(y - 1) * w + x] - compositedLums[(y - 1) * w + (x + 1)] +
                            compositedLums[(y + 1) * w + (x - 1)] + 2 * compositedLums[(y + 1) * w + x] + compositedLums[(y + 1) * w + (x + 1)]
                        val mag = abs(gx) + abs(gy)
                        val isEdge = mag >= edgeLimit * 2
                        mask[idx] = if (invert) !isEdge else isEdge
                    }
                }
            }
        }

        // Despeckle isolated 1-pixel noise to prevent jagged micro-cuts
        val cleanedMask = despeckleMask(mask, w, h)
        return traceMask(
            mask = cleanedMask,
            w = w,
            h = h,
            simplifyEpsilonPx = simplifyEpsilonPx,
            minPerimeterNodes = minNoiseFilter.coerceIn(4, 24)
        )
    }

    private fun despeckleMask(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = mask.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                var neighbors = 0
                if (mask[(y - 1) * w + x]) neighbors++
                if (mask[(y + 1) * w + x]) neighbors++
                if (mask[y * w + (x - 1)]) neighbors++
                if (mask[y * w + (x + 1)]) neighbors++
                if (mask[idx] && neighbors == 0) {
                    out[idx] = false
                } else if (!mask[idx] && neighbors == 4) {
                    out[idx] = true
                }
            }
        }
        return out
    }

    fun traceMask(
        mask: BooleanArray,
        w: Int,
        h: Int,
        simplifyEpsilonPx: Float = 1.1f,
        minPerimeterNodes: Int = 4
    ): TracedImageResult {
        fun isSolid(x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x >= w || y >= h) return false
            return mask[y * w + x]
        }

        fun packVertex(vx: Int, vy: Int): Long = (vx.toLong() shl 16) or (vy.toLong() and 0xFFFFL)
        fun unpackX(key: Long): Int = (key shr 16).toInt()
        fun unpackY(key: Long): Int = (key and 0xFFFFL).toInt()

        val adjacency = HashMap<Long, MutableList<Long>>()

        fun addEdge(x0: Int, y0: Int, x1: Int, y1: Int) {
            val k0 = packVertex(x0, y0)
            val k1 = packVertex(x1, y1)
            adjacency.getOrPut(k0) { mutableListOf() }.add(k1)
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!isSolid(x, y)) continue
                if (!isSolid(x, y - 1)) addEdge(x, y, x + 1, y)
                if (!isSolid(x + 1, y)) addEdge(x + 1, y, x + 1, y + 1)
                if (!isSolid(x, y + 1)) addEdge(x + 1, y + 1, x, y + 1)
                if (!isSolid(x - 1, y)) addEdge(x, y + 1, x, y)
            }
        }

        val rawLoops = mutableListOf<List<Pair<Float, Float>>>()
        val iteratorKeys = adjacency.keys.toList()

        for (startKey in iteratorKeys) {
            while (true) {
                val list = adjacency[startKey]
                if (list == null || list.isEmpty()) break
                val nextKey = list.removeAt(list.lastIndex)
                val loop = mutableListOf<Pair<Float, Float>>()
                loop.add(unpackX(startKey).toFloat() to unpackY(startKey).toFloat())

                var prevKey = startKey
                var curKey = nextKey
                var guard = 0
                val maxGuard = (w + 2) * (h + 2) * 4

                while (curKey != startKey && guard < maxGuard) {
                    guard++
                    loop.add(unpackX(curKey).toFloat() to unpackY(curKey).toFloat())
                    val candidates = adjacency[curKey]
                    if (candidates == null || candidates.isEmpty()) break
                    val chosenIdx = if (candidates.size == 1) 0 else {
                        val px = unpackX(prevKey)
                        val py = unpackY(prevKey)
                        val cx = unpackX(curKey)
                        val cy = unpackY(curKey)
                        val inDx = cx - px
                        val inDy = cy - py
                        var bestIdx = 0
                        var bestScore = Int.MIN_VALUE
                        candidates.forEachIndexed { idx, cand ->
                            val nx = unpackX(cand)
                            val ny = unpackY(cand)
                            val outDx = nx - cx
                            val outDy = ny - cy
                            val cross = inDx * outDy - inDy * outDx
                            val dot = inDx * outDx + inDy * outDy
                            val score = if (cross > 0) 3 else if (dot > 0) 2 else 1
                            if (score > bestScore) {
                                bestScore = score
                                bestIdx = idx
                            }
                        }
                        bestIdx
                    }
                    prevKey = curKey
                    curKey = candidates.removeAt(chosenIdx)
                }

                if (loop.size >= 4) {
                    rawLoops.add(loop)
                }
            }
        }

        if (rawLoops.isEmpty()) {
            return TracedImageResult(emptyList(), w.toFloat() / h.toFloat(), 0)
        }

        // Filter tiny pixel noise loops by perimeter length before smoothing
        val minArcLen = (minPerimeterNodes * 2.5f).coerceAtLeast(8f)
        val simplifiedLoops = rawLoops.mapNotNull { loop ->
            val arcLen = loopArcLength(loop)
            if (arcLen < minArcLen && loop.size < minPerimeterNodes * 2) return@mapNotNull null

            val cleaned = removeCollinear(loop)
            val smoothed = if (simplifyEpsilonPx >= 0.7f && cleaned.size >= 8) chaikinClosed(cleaned) else cleaned
            val dp = douglasPeuckerClosed(smoothed, simplifyEpsilonPx.coerceAtLeast(0.35f))
            if (dp.size >= 4) dp else null
        }

        if (simplifiedLoops.isEmpty()) {
            return TracedImageResult(emptyList(), w.toFloat() / h.toFloat(), 0)
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        simplifiedLoops.forEach { pts ->
            pts.forEach { (px, py) ->
                minX = min(minX, px)
                minY = min(minY, py)
                maxX = max(maxX, px)
                maxY = max(maxY, py)
            }
        }
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)

        var nodeSum = 0
        val normalized = simplifiedLoops.map { pts ->
            nodeSum += pts.size
            Polyline2D(
                points = pts.map { (px, py) ->
                    Point2D((px - minX) / spanX, (py - minY) / spanY)
                },
                closed = true
            )
        }

        return TracedImageResult(
            normalizedPaths = normalized,
            aspectRatio = (spanX / spanY).coerceIn(0.2f, 5f),
            totalNodes = nodeSum
        )
    }

    private fun loopArcLength(pts: List<Pair<Float, Float>>): Float {
        if (pts.size < 2) return 0f
        var sum = 0f
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            sum += hypot(b.first - a.first, b.second - a.second)
        }
        return sum
    }

    private fun removeCollinear(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        if (pts.size < 3) return pts
        val out = ArrayList<Pair<Float, Float>>(pts.size)
        val n = pts.size
        for (i in 0 until n) {
            val prev = pts[(i - 1 + n) % n]
            val cur = pts[i]
            val next = pts[(i + 1) % n]
            val cross = (cur.first - prev.first) * (next.second - cur.second) -
                (cur.second - prev.second) * (next.first - cur.first)
            if (abs(cross) > 0.001f) {
                out.add(cur)
            }
        }
        return if (out.size >= 3) out else pts
    }

    private fun chaikinClosed(pts: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val n = pts.size
        val out = ArrayList<Pair<Float, Float>>(n * 2)
        for (i in 0 until n) {
            val p0 = pts[i]
            val p1 = pts[(i + 1) % n]
            out.add((0.75f * p0.first + 0.25f * p1.first) to (0.75f * p0.second + 0.25f * p1.second))
            out.add((0.25f * p0.first + 0.75f * p1.first) to (0.25f * p0.second + 0.75f * p1.second))
        }
        return out
    }

    private fun douglasPeuckerClosed(
        pts: List<Pair<Float, Float>>,
        epsilon: Float
    ): List<Pair<Float, Float>> {
        if (pts.size <= 4) return pts
        val split = douglasPeuckerOpen(pts + pts.first(), epsilon)
        return if (split.size > 1) split.dropLast(1) else pts
    }

    private fun douglasPeuckerOpen(
        pts: List<Pair<Float, Float>>,
        epsilon: Float
    ): List<Pair<Float, Float>> {
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

    private fun perpendicularDistance(
        p: Pair<Float, Float>,
        a: Pair<Float, Float>,
        b: Pair<Float, Float>
    ): Float {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len = hypot(dx, dy)
        if (len < 1e-5f) return hypot(p.first - a.first, p.second - a.second)
        return abs(dy * p.first - dx * p.second + b.first * a.second - b.second * a.first) / len
    }
}
