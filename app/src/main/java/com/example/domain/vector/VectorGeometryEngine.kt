package com.example.domain.vector

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.domain.model.BoundingBoxMm
import com.example.domain.model.DesignElement
import com.example.domain.model.DesignFontStyle
import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import com.example.domain.model.VectorShapeType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class VectorGeometryEngine(private val context: Context? = null) {

    private val typefaceCache = mutableMapOf<DesignFontStyle, Typeface>()

    private fun resolveTypeface(style: DesignFontStyle): Typeface {
        typefaceCache[style]?.let { return it }
        val loaded = if (context != null) {
            val resId = when (style) {
                DesignFontStyle.CAIRO -> R.font.cairo
                DesignFontStyle.AMIRI -> R.font.amiri
                DesignFontStyle.MONOSPACE -> R.font.jetbrains_mono
            }
            try {
                ResourcesCompat.getFont(context, resId) ?: Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        } else {
            Typeface.DEFAULT
        }
        typefaceCache[style] = loaded
        return loaded
    }

    /**
     * Converts an Arabic/Latin string into normalized [0..1] vector polylines using Android's
     * native HarfBuzz shaping + PathMeasure contour extraction, and returns the natural aspect ratio.
     */
    fun vectorizeText(
        text: String,
        fontStyle: DesignFontStyle
    ): Pair<List<Polyline2D>, Float> {
        val cleanText = text.ifBlank { "كتر" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 140f
            typeface = resolveTypeface(fontStyle)
            style = Paint.Style.STROKE
        }

        val lines = cleanText.split('\n').filter { it.isNotEmpty() }.ifEmpty { listOf(" ") }
        val lineHeight = paint.fontSpacing.coerceAtLeast(140f)
        val combinedPath = Path()

        lines.forEachIndexed { index, line ->
            val linePath = Path()
            paint.getTextPath(line, 0, line.length, 0f, index * lineHeight, linePath)
            combinedPath.addPath(linePath)
        }

        val bounds = RectF()
        combinedPath.computeBounds(bounds, true)
        if (bounds.width() <= 0.1f || bounds.height() <= 0.1f) {
            return listOf(
                Polyline2D(
                    listOf(Point2D(0f, 0f), Point2D(1f, 0f), Point2D(1f, 1f), Point2D(0f, 1f)),
                    closed = true
                )
            ) to 2.5f
        }

        val polylines = mutableListOf<Polyline2D>()
        val pathMeasure = PathMeasure(combinedPath, false)
        val pos = FloatArray(2)

        do {
            val length = pathMeasure.length
            if (length > 0.5f) {
                val steps = (length / 2.2f).toInt().coerceIn(8, 220)
                val pts = ArrayList<Point2D>(steps + 1)
                for (i in 0..steps) {
                    val dist = (i.toFloat() / steps) * length
                    if (pathMeasure.getPosTan(dist, pos, null)) {
                        val nx = (pos[0] - bounds.left) / bounds.width()
                        val ny = (pos[1] - bounds.top) / bounds.height()
                        pts.add(Point2D(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f)))
                    }
                }
                if (pts.size >= 2) {
                    polylines.add(Polyline2D(pts, closed = pathMeasure.isClosed))
                }
            }
        } while (pathMeasure.nextContour())

        val aspectRatio = (bounds.width() / bounds.height()).coerceIn(0.4f, 12f)
        return polylines to aspectRatio
    }

    /**
     * Converts any parametric shape or text element into a node-editable element with normalizedPaths.
     */
    fun bakeToEditablePaths(element: DesignElement): DesignElement {
        if (element.normalizedPaths.isNotEmpty() &&
            (element.type == VectorShapeType.CUSTOM_PATH ||
                element.type == VectorShapeType.IMPORTED_SVG ||
                element.type == VectorShapeType.TRACED_IMAGE ||
                element.type == VectorShapeType.TEXT)
        ) {
            return element
        }
        val w = element.widthMm.coerceAtLeast(1f)
        val h = element.heightMm.coerceAtLeast(1f)
        val local = buildLocalPolylines(element)
        val normalized = local.map { poly ->
            Polyline2D(
                points = poly.points.map { Point2D(it.xMm / w, it.yMm / h) },
                closed = poly.closed
            )
        }
        return element.copy(
            type = VectorShapeType.CUSTOM_PATH,
            normalizedPaths = normalized
        )
    }

    /**
     * Converts a world-space delta (dxMm, dyMm) into a local normalized delta for node dragging,
     * taking element dimensions, mirror, and rotation into account.
     */
    fun worldDeltaToNormalizedDelta(
        element: DesignElement,
        worldDxMm: Float,
        worldDyMm: Float
    ): Point2D {
        val rad = -element.rotationDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val localDx = worldDxMm * cosA - worldDyMm * sinA
        val localDy = worldDxMm * sinA + worldDyMm * cosA
        val w = element.widthMm.coerceAtLeast(1f)
        val h = element.heightMm.coerceAtLeast(1f)
        val mx = if (element.mirrorHorizontal) -localDx else localDx
        val my = if (element.mirrorVertical) -localDy else localDy
        return Point2D(mx / w, my / h)
    }

    /**
     * Re-normalizes an element's normalizedPaths tightly to [0..1] and adjusts (xMm, yMm, widthMm, heightMm)
     * when rotation is 0 so resize handles fit the edited nodes cleanly.
     */
    fun renormalizeBoundsIfUnrotated(element: DesignElement): DesignElement {
        if (element.normalizedPaths.isEmpty() ||
            element.rotationDeg != 0f ||
            element.mirrorHorizontal ||
            element.mirrorVertical
        ) {
            return element
        }
        var minNx = Float.MAX_VALUE
        var minNy = Float.MAX_VALUE
        var maxNx = -Float.MAX_VALUE
        var maxNy = -Float.MAX_VALUE
        element.normalizedPaths.forEach { poly ->
            poly.points.forEach { pt ->
                minNx = min(minNx, pt.xMm)
                minNy = min(minNy, pt.yMm)
                maxNx = max(maxNx, pt.xMm)
                maxNy = max(maxNy, pt.yMm)
            }
        }
        if (minNx == Float.MAX_VALUE) return element
        val spanX = (maxNx - minNx).coerceAtLeast(0.02f)
        val spanY = (maxNy - minNy).coerceAtLeast(0.02f)
        val newX = element.xMm + minNx * element.widthMm
        val newY = element.yMm + minNy * element.heightMm
        val newW = (spanX * element.widthMm).coerceAtLeast(5f)
        val newH = (spanY * element.heightMm).coerceAtLeast(5f)
        val renormalized = element.normalizedPaths.map { poly ->
            Polyline2D(
                points = poly.points.map { pt ->
                    Point2D((pt.xMm - minNx) / spanX, (pt.yMm - minNy) / spanY)
                },
                closed = poly.closed
            )
        }
        return element.copy(
            xMm = newX,
            yMm = newY,
            widthMm = newW,
            heightMm = newH,
            normalizedPaths = renormalized
        )
    }

    /**
     * Performs a real boolean Union (Weld) across one or more elements using Skia PathOps,
     * removing overlapping interior cut lines so the plotter cuts a single unified outline.
     */
    fun weldElements(elements: List<DesignElement>): DesignElement? =
        booleanOpElements(elements, com.example.domain.model.VectorBooleanOp.UNION)

    /**
     * Performs real SignMaster-style 2D boolean shaping across selected elements:
     * - UNION (لحام): merges overlapping boundaries into one outline.
     * - DIFFERENCE (تفريغ): subtracts upper elements from the bottom-most base element.
     * - INTERSECT (تقاطع): keeps only the overlapping intersection area.
     */
    fun booleanOpElements(
        elements: List<DesignElement>,
        op: com.example.domain.model.VectorBooleanOp
    ): DesignElement? {
        if (elements.isEmpty()) return null
        if (op != com.example.domain.model.VectorBooleanOp.UNION && elements.size < 2) return null

        val scale = 10f // 0.1mm precision for Skia PathOps
        val combinedPath = Path()
        var hasAny = false

        val skiaOp = when (op) {
            com.example.domain.model.VectorBooleanOp.UNION -> Path.Op.UNION
            com.example.domain.model.VectorBooleanOp.DIFFERENCE -> Path.Op.DIFFERENCE
            com.example.domain.model.VectorBooleanOp.INTERSECT -> Path.Op.INTERSECT
        }

        elements.forEach { el ->
            val elPath = Path()
            buildWorldPolylines(el).forEach { poly ->
                if (poly.points.size >= 2) {
                    val p = Path()
                    poly.points.forEachIndexed { idx, pt ->
                        val sx = pt.xMm * scale
                        val sy = pt.yMm * scale
                        if (idx == 0) p.moveTo(sx, sy) else p.lineTo(sx, sy)
                    }
                    p.close()
                    elPath.addPath(p)
                }
            }
            if (!hasAny) {
                combinedPath.set(elPath)
                hasAny = true
            } else {
                combinedPath.op(elPath, skiaOp)
            }
        }

        if (!hasAny) return null
        val bounds = RectF()
        combinedPath.computeBounds(bounds, true)
        if (bounds.width() < 1f || bounds.height() < 1f) return null

        val polylines = mutableListOf<Polyline2D>()
        val pm = PathMeasure(combinedPath, false)
        val pos = FloatArray(2)
        do {
            val len = pm.length
            if (len > 1f) {
                val steps = (len / 4f).toInt().coerceIn(8, 280)
                val pts = ArrayList<Point2D>(steps + 1)
                for (i in 0..steps) {
                    val d = (i.toFloat() / steps) * len
                    if (pm.getPosTan(d, pos, null)) {
                        val nx = ((pos[0] - bounds.left) / bounds.width()).coerceIn(0f, 1f)
                        val ny = ((pos[1] - bounds.top) / bounds.height()).coerceIn(0f, 1f)
                        pts.add(Point2D(nx, ny))
                    }
                }
                if (pts.size >= 3) {
                    polylines.add(Polyline2D(pts, closed = true))
                }
            }
        } while (pm.nextContour())

        if (polylines.isEmpty()) return null

        val resultLabel = when (op) {
            com.example.domain.model.VectorBooleanOp.UNION -> "ملحوم (${elements.size})"
            com.example.domain.model.VectorBooleanOp.DIFFERENCE -> "مفرّغ (${elements.first().name})"
            com.example.domain.model.VectorBooleanOp.INTERSECT -> "تقاطع (${elements.size})"
        }

        return DesignElement(
            id = java.util.UUID.randomUUID().toString(),
            name = resultLabel,
            type = VectorShapeType.CUSTOM_PATH,
            xMm = bounds.left / scale,
            yMm = bounds.top / scale,
            widthMm = (bounds.width() / scale).coerceAtLeast(5f),
            heightMm = (bounds.height() / scale).coerceAtLeast(5f),
            normalizedPaths = polylines,
            strokeColorHex = elements.first().strokeColorHex,
            fillColorHex = elements.first().fillColorHex
        )
    }

    /**
     * Breaks apart a multi-contour element (e.g. traced image, SVG, Arabic text, Islamic star)
     * into separate independent DesignElements, one per contour.
     */
    fun breakApartElement(element: DesignElement): List<DesignElement> {
        val worldPolys = buildWorldPolylines(element)
        if (worldPolys.size <= 1) return listOf(element.copy(groupId = null))

        return worldPolys.mapIndexedNotNull { idx, poly ->
            if (poly.points.size < 2) return@mapIndexedNotNull null
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            poly.points.forEach { pt ->
                minX = min(minX, pt.xMm)
                minY = min(minY, pt.yMm)
                maxX = max(maxX, pt.xMm)
                maxY = max(maxY, pt.yMm)
            }
            val w = (maxX - minX).coerceAtLeast(2f)
            val h = (maxY - minY).coerceAtLeast(2f)
            val normPts = poly.points.map { pt ->
                Point2D((pt.xMm - minX) / w, (pt.yMm - minY) / h)
            }
            DesignElement(
                id = java.util.UUID.randomUUID().toString(),
                name = "${element.name} (${idx + 1})",
                type = VectorShapeType.CUSTOM_PATH,
                xMm = minX,
                yMm = minY,
                widthMm = w,
                heightMm = h,
                normalizedPaths = listOf(Polyline2D(normPts, closed = poly.closed)),
                strokeColorHex = element.strokeColorHex,
                fillColorHex = element.fillColorHex,
                groupId = null
            )
        }
    }

    /**
     * Generates final millimeter-space polylines for a DesignElement including mirror, rotation, and position.
     */
    fun buildWorldPolylines(element: DesignElement): List<Polyline2D> {
        val localPolylines = buildLocalPolylines(element)
        val cx = element.widthMm / 2f
        val cy = element.heightMm / 2f
        val rad = element.rotationDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)

        return localPolylines.map { poly ->
            val transformed = poly.points.map { p ->
                val mx = if (element.mirrorHorizontal) element.widthMm - p.xMm else p.xMm
                val my = if (element.mirrorVertical) element.heightMm - p.yMm else p.yMm
                val dy = my - cy
                val dx = mx - cx
                val rx = dx * cosA - dy * sinA + cx + element.xMm
                val ry = dx * sinA + dy * cosA + cy + element.yMm
                Point2D(rx, ry)
            }
            Polyline2D(transformed, poly.closed)
        }
    }

    fun buildLocalPolylines(element: DesignElement): List<Polyline2D> {
        val w = element.widthMm.coerceAtLeast(1f)
        val h = element.heightMm.coerceAtLeast(1f)

        return when (element.type) {
            VectorShapeType.RECTANGLE -> listOf(
                Polyline2D(
                    listOf(Point2D(0f, 0f), Point2D(w, 0f), Point2D(w, h), Point2D(0f, h)),
                    closed = true
                )
            )

            VectorShapeType.ROUNDED_RECT -> {
                val r = element.cornerRadiusMm.coerceIn(0.5f, min(w, h) / 2f)
                val pts = mutableListOf<Point2D>()
                fun addArc(cx: Float, cy: Float, startDeg: Float, endDeg: Float) {
                    val steps = 8
                    for (i in 0..steps) {
                        val a = (startDeg + (endDeg - startDeg) * (i.toFloat() / steps)) * (PI.toFloat() / 180f)
                        pts.add(Point2D(cx + r * cos(a), cy + r * sin(a)))
                    }
                }
                addArc(w - r, r, -90f, 0f)
                addArc(w - r, h - r, 0f, 90f)
                addArc(r, h - r, 90f, 180f)
                addArc(r, r, 180f, 270f)
                listOf(Polyline2D(pts, closed = true))
            }

            VectorShapeType.CIRCLE -> {
                val segments = 48
                val rx = w / 2f
                val ry = h / 2f
                val pts = (0 until segments).map { i ->
                    val theta = (2f * PI.toFloat() * i) / segments
                    Point2D(rx + rx * cos(theta), ry + ry * sin(theta))
                }
                listOf(Polyline2D(pts, closed = true))
            }

            VectorShapeType.STAR -> {
                val pointsCount = element.starPoints.coerceIn(4, 12)
                val totalVertices = pointsCount * 2
                val cx = w / 2f
                val cy = h / 2f
                val outerRx = w / 2f
                val outerRy = h / 2f
                val innerRatio = 0.42f
                val pts = (0 until totalVertices).map { i ->
                    val angle = (-PI.toFloat() / 2f) + (i * PI.toFloat() / pointsCount)
                    val ratio = if (i % 2 == 0) 1f else innerRatio
                    Point2D(cx + outerRx * ratio * cos(angle), cy + outerRy * ratio * sin(angle))
                }
                listOf(Polyline2D(pts, closed = true))
            }

            VectorShapeType.HEXAGON -> {
                val cx = w / 2f
                val cy = h / 2f
                val pts = (0 until 6).map { i ->
                    val angle = (-PI.toFloat() / 2f) + (i * 2f * PI.toFloat() / 6f)
                    Point2D(cx + (w / 2f) * cos(angle), cy + (h / 2f) * sin(angle))
                }
                listOf(Polyline2D(pts, closed = true))
            }

            VectorShapeType.DIAMOND -> listOf(
                Polyline2D(
                    listOf(
                        Point2D(w / 2f, 0f),
                        Point2D(w, h / 2f),
                        Point2D(w / 2f, h),
                        Point2D(0f, h / 2f)
                    ),
                    closed = true
                )
            )

            VectorShapeType.HEART -> {
                val steps = 56
                val rawPts = (0 until steps).map { i ->
                    val t = (2f * PI.toFloat() * i) / steps
                    val x = 16f * sin(t).pow(3)
                    val y = -(13f * cos(t) - 5f * cos(2 * t) - 2f * cos(3 * t) - cos(4 * t))
                    x to y
                }
                val minX = rawPts.minOf { it.first }
                val maxX = rawPts.maxOf { it.first }
                val minY = rawPts.minOf { it.second }
                val maxY = rawPts.maxOf { it.second }
                val pts = rawPts.map { (x, y) ->
                    Point2D(
                        ((x - minX) / (maxX - minX)) * w,
                        ((y - minY) / (maxY - minY)) * h
                    )
                }
                listOf(Polyline2D(pts, closed = true))
            }

            VectorShapeType.ISLAMIC_STAR -> {
                val cx = w / 2f
                val cy = h / 2f
                val outerPts = (0 until 16).map { i ->
                    val angle = i * (2f * PI.toFloat() / 16f)
                    val rFactor = if (i % 2 == 0) 1f else 0.7071f
                    Point2D(cx + (w / 2f) * rFactor * cos(angle), cy + (h / 2f) * rFactor * sin(angle))
                }
                val innerPts = (0 until 8).map { i ->
                    val angle = i * (2f * PI.toFloat() / 8f) + (PI.toFloat() / 8f)
                    Point2D(cx + (w * 0.24f) * cos(angle), cy + (h * 0.24f) * sin(angle))
                }
                listOf(
                    Polyline2D(outerPts, closed = true),
                    Polyline2D(innerPts, closed = true)
                )
            }

            VectorShapeType.ARROW -> listOf(
                Polyline2D(
                    listOf(
                        Point2D(0f, h * 0.30f),
                        Point2D(w * 0.60f, h * 0.30f),
                        Point2D(w * 0.60f, 0f),
                        Point2D(w, h * 0.50f),
                        Point2D(w * 0.60f, h),
                        Point2D(w * 0.60f, h * 0.70f),
                        Point2D(0f, h * 0.70f)
                    ),
                    closed = true
                )
            )

            VectorShapeType.REGISTRATION_MARKS -> {
                val arm = min(w, h) * 0.18f
                listOf(
                    Polyline2D(listOf(Point2D(0f, arm), Point2D(0f, 0f), Point2D(arm, 0f)), closed = false),
                    Polyline2D(listOf(Point2D(w - arm, 0f), Point2D(w, 0f), Point2D(w, arm)), closed = false),
                    Polyline2D(listOf(Point2D(0f, h - arm), Point2D(0f, h), Point2D(arm, h)), closed = false),
                    Polyline2D(listOf(Point2D(w - arm, h), Point2D(w, h), Point2D(w, h - arm)), closed = false)
                )
            }

            VectorShapeType.TEXT,
            VectorShapeType.IMPORTED_SVG,
            VectorShapeType.TRACED_IMAGE,
            VectorShapeType.CUSTOM_PATH -> {
                if (element.normalizedPaths.isNotEmpty()) {
                    element.normalizedPaths.map { poly ->
                        Polyline2D(
                            points = poly.points.map { Point2D(it.xMm * w, it.yMm * h) },
                            closed = poly.closed
                        )
                    }
                } else if (element.type == VectorShapeType.TEXT) {
                    val (paths, _) = vectorizeText(element.textContent, element.fontStyle)
                    paths.map { poly ->
                        Polyline2D(
                            points = poly.points.map { Point2D(it.xMm * w, it.yMm * h) },
                            closed = poly.closed
                        )
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    fun computeElementsBoundingBox(elements: List<DesignElement>): BoundingBoxMm? {
        if (elements.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        elements.forEach { el ->
            buildWorldPolylines(el).forEach { poly ->
                poly.points.forEach { pt ->
                    minX = min(minX, pt.xMm)
                    minY = min(minY, pt.yMm)
                    maxX = max(maxX, pt.xMm)
                    maxY = max(maxY, pt.yMm)
                }
            }
        }
        if (minX == Float.MAX_VALUE) return null
        return BoundingBoxMm(minX, minY, maxX, maxY)
    }
}
