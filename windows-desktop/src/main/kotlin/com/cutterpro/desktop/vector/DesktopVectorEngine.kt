package com.cutterpro.desktop.vector

import com.cutterpro.desktop.model.AlignmentAction
import com.cutterpro.desktop.model.BoundingBoxMm
import com.cutterpro.desktop.model.CuttingMatConfig
import com.cutterpro.desktop.model.DesignElement
import com.cutterpro.desktop.model.Point2D
import com.cutterpro.desktop.model.Polyline2D
import com.cutterpro.desktop.model.VectorBooleanOp
import com.cutterpro.desktop.model.VectorShapeType
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.FlatteningPathIterator
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class DesktopVectorEngine {

    fun listAvailableFonts(): List<String> {
        val preferred = listOf("Cairo", "Amiri", "Tahoma", "Arial", "Segoe UI", "Times New Roman", "Courier New")
        val system = runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
        }.getOrDefault(emptyList())
        return (preferred.filter { it in system } + system.take(40)).distinct().ifEmpty { listOf("SansSerif") }
    }

    fun vectorizeText(text: String, fontFamily: String): Pair<List<Polyline2D>, Float> {
        val cleanText = text.ifBlank { "كتر" }
        val font = Font(fontFamily.ifBlank { Font.SANS_SERIF }, Font.BOLD, 140)
        val frc = FontRenderContext(AffineTransform(), true, true)
        val lines = cleanText.split('\n').filter { it.isNotEmpty() }.ifEmpty { listOf(" ") }
        val lineHeight = font.getLineMetrics("Ag", frc).height.coerceAtLeast(140f)

        val combined = Path2D.Float(Path2D.WIND_EVEN_ODD)
        lines.forEachIndexed { idx, line ->
            val gv = font.createGlyphVector(frc, line)
            val outline = gv.getOutline(0f, idx * lineHeight)
            combined.append(outline, false)
        }

        val bounds = combined.bounds2D
        if (bounds.width <= 0.1 || bounds.height <= 0.1) {
            return listOf(
                Polyline2D(
                    listOf(Point2D(0f, 0f), Point2D(1f, 0f), Point2D(1f, 1f), Point2D(0f, 1f)),
                    closed = true
                )
            ) to 2.5f
        }

        val polylines = extractPolylinesFromShape(
            path = combined,
            flatness = 0.65,
            boundsLeft = bounds.x.toFloat(),
            boundsTop = bounds.y.toFloat(),
            boundsWidth = bounds.width.toFloat(),
            boundsHeight = bounds.height.toFloat()
        )

        val aspect = (bounds.width / bounds.height).toFloat().coerceIn(0.4f, 12f)
        return polylines.ifEmpty {
            listOf(Polyline2D(listOf(Point2D(0f, 0f), Point2D(1f, 0f), Point2D(1f, 1f), Point2D(0f, 1f)), closed = true))
        } to aspect
    }

    private fun extractPolylinesFromShape(
        path: java.awt.Shape,
        flatness: Double,
        boundsLeft: Float,
        boundsTop: Float,
        boundsWidth: Float,
        boundsHeight: Float
    ): List<Polyline2D> {
        val iter = FlatteningPathIterator(path.getPathIterator(null), flatness)
        val coords = DoubleArray(6)
        val result = mutableListOf<Polyline2D>()
        var current = mutableListOf<Point2D>()

        fun addNorm(x: Double, y: Double) {
            val nx = ((x.toFloat() - boundsLeft) / boundsWidth.coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
            val ny = ((y.toFloat() - boundsTop) / boundsHeight.coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
            current.add(Point2D(nx, ny))
        }

        while (!iter.isDone) {
            when (iter.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> {
                    if (current.size >= 2) result.add(Polyline2D(current.toList(), closed = false))
                    current = mutableListOf()
                    addNorm(coords[0], coords[1])
                }
                PathIterator.SEG_LINETO -> {
                    addNorm(coords[0], coords[1])
                }
                PathIterator.SEG_CLOSE -> {
                    if (current.size >= 3) {
                        result.add(Polyline2D(current.toList(), closed = true))
                    } else if (current.size >= 2) {
                        result.add(Polyline2D(current.toList(), closed = false))
                    }
                    current = mutableListOf()
                }
            }
            iter.next()
        }
        if (current.size >= 2) {
            result.add(Polyline2D(current.toList(), closed = false))
        }
        return result
    }

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
            Polyline2D(poly.points.map { Point2D(it.xMm / w, it.yMm / h) }, closed = poly.closed)
        }
        return element.copy(type = VectorShapeType.CUSTOM_PATH, normalizedPaths = normalized)
    }

    fun worldDeltaToNormalizedDelta(element: DesignElement, worldDxMm: Float, worldDyMm: Float): Point2D {
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

    fun booleanOpElements(elements: List<DesignElement>, op: VectorBooleanOp): DesignElement? {
        if (elements.isEmpty()) return null
        if (op != VectorBooleanOp.UNION && elements.size < 2) return null

        var combinedArea: Area? = null
        elements.forEach { el ->
            val path = Path2D.Float(Path2D.WIND_EVEN_ODD)
            buildWorldPolylines(el).forEach { poly ->
                if (poly.points.size >= 2) {
                    poly.points.forEachIndexed { idx, pt ->
                        if (idx == 0) path.moveTo(pt.xMm, pt.yMm) else path.lineTo(pt.xMm, pt.yMm)
                    }
                    path.closePath()
                }
            }
            val elArea = Area(path)
            val current = combinedArea
            if (current == null) {
                combinedArea = elArea
            } else {
                when (op) {
                    VectorBooleanOp.UNION -> current.add(elArea)
                    VectorBooleanOp.DIFFERENCE -> current.subtract(elArea)
                    VectorBooleanOp.INTERSECT -> current.intersect(elArea)
                }
            }
        }

        val finalArea = combinedArea ?: return null
        val bounds = finalArea.bounds2D
        if (bounds.width < 0.5 || bounds.height < 0.5) return null

        val polylines = extractPolylinesFromShape(
            path = finalArea,
            flatness = 0.25,
            boundsLeft = bounds.x.toFloat(),
            boundsTop = bounds.y.toFloat(),
            boundsWidth = bounds.width.toFloat(),
            boundsHeight = bounds.height.toFloat()
        ).filter { it.points.size >= 3 }

        if (polylines.isEmpty()) return null

        val label = when (op) {
            VectorBooleanOp.UNION -> "ملحوم (${elements.size})"
            VectorBooleanOp.DIFFERENCE -> "مفرّغ (${elements.first().name})"
            VectorBooleanOp.INTERSECT -> "تقاطع (${elements.size})"
        }

        return DesignElement(
            id = UUID.randomUUID().toString(),
            name = label,
            type = VectorShapeType.CUSTOM_PATH,
            xMm = bounds.x.toFloat(),
            yMm = bounds.y.toFloat(),
            widthMm = bounds.width.toFloat().coerceAtLeast(5f),
            heightMm = bounds.height.toFloat().coerceAtLeast(5f),
            normalizedPaths = polylines,
            strokeColorHex = elements.first().strokeColorHex,
            fillColorHex = elements.first().fillColorHex
        )
    }

    fun breakApartElement(element: DesignElement): List<DesignElement> {
        val worldPolys = buildWorldPolylines(element)
        if (worldPolys.size <= 1) return listOf(element.copy(groupId = null))

        return worldPolys.mapIndexedNotNull { idx, poly ->
            if (poly.points.size < 2) return@mapIndexedNotNull null
            val minX = poly.points.minOf { it.xMm }
            val minY = poly.points.minOf { it.yMm }
            val maxX = poly.points.maxOf { it.xMm }
            val maxY = poly.points.maxOf { it.yMm }
            val w = (maxX - minX).coerceAtLeast(2f)
            val h = (maxY - minY).coerceAtLeast(2f)
            val norm = poly.points.map { Point2D((it.xMm - minX) / w, (it.yMm - minY) / h) }
            DesignElement(
                id = UUID.randomUUID().toString(),
                name = "${element.name} (${idx + 1})",
                type = VectorShapeType.CUSTOM_PATH,
                xMm = minX,
                yMm = minY,
                widthMm = w,
                heightMm = h,
                normalizedPaths = listOf(Polyline2D(norm, closed = poly.closed)),
                strokeColorHex = element.strokeColorHex,
                fillColorHex = element.fillColorHex
            )
        }
    }

    fun createContourOffsetElement(source: DesignElement, offsetMm: Float): DesignElement? {
        val clamped = offsetMm.coerceIn(1f, 15f)
        val worldPolys = buildWorldPolylines(source).filter { it.points.size >= 2 }
        if (worldPolys.isEmpty()) return null

        val expandedArea = Area()
        worldPolys.forEach { poly ->
            val cleanPts = if (poly.points.size > 2 && poly.points.first() == poly.points.last()) {
                poly.points.dropLast(1)
            } else poly.points
            if (cleanPts.size < 2) return@forEach

            if (poly.closed && cleanPts.size >= 3) {
                val p = Path2D.Float(Path2D.WIND_EVEN_ODD)
                cleanPts.forEachIndexed { idx, pt ->
                    if (idx == 0) p.moveTo(pt.xMm, pt.yMm) else p.lineTo(pt.xMm, pt.yMm)
                }
                p.closePath()
                expandedArea.add(Area(p))
            }

            val segCount = if (poly.closed) cleanPts.size else cleanPts.lastIndex
            for (i in 0 until segCount) {
                val a = cleanPts[i]
                val b = cleanPts[(i + 1) % cleanPts.size]
                val dx = b.xMm - a.xMm
                val dy = b.yMm - a.yMm
                val len = hypot(dx, dy)
                if (len < 1e-4f) continue
                val nx = (-dy / len) * clamped
                val ny = (dx / len) * clamped
                val capsule = Path2D.Float()
                capsule.moveTo(a.xMm + nx, a.yMm + ny)
                capsule.lineTo(b.xMm + nx, b.yMm + ny)
                val endA = atan2(ny, nx)
                for (s in 1..6) {
                    val ang = endA - (PI.toFloat() * s / 6f)
                    capsule.lineTo(b.xMm + cos(ang) * clamped, b.yMm + sin(ang) * clamped)
                }
                capsule.lineTo(b.xMm - nx, b.yMm - ny)
                capsule.lineTo(a.xMm - nx, a.yMm - ny)
                val startA = atan2(-ny, -nx)
                for (s in 1..6) {
                    val ang = startA - (PI.toFloat() * s / 6f)
                    capsule.lineTo(a.xMm + cos(ang) * clamped, a.yMm + sin(ang) * clamped)
                }
                capsule.closePath()
                expandedArea.add(Area(capsule))
            }
        }

        val bounds = expandedArea.bounds2D
        if (bounds.width < 2.0 || bounds.height < 2.0) return null
        val polylines = extractPolylinesFromShape(
            path = expandedArea,
            flatness = 0.25,
            boundsLeft = bounds.x.toFloat(),
            boundsTop = bounds.y.toFloat(),
            boundsWidth = bounds.width.toFloat(),
            boundsHeight = bounds.height.toFloat()
        ).filter { it.points.size >= 3 }
        if (polylines.isEmpty()) return null

        return DesignElement(
            id = UUID.randomUUID().toString(),
            name = "حد خارجي: ${source.name}",
            type = VectorShapeType.CUSTOM_PATH,
            xMm = bounds.x.toFloat().coerceAtLeast(0f),
            yMm = bounds.y.toFloat().coerceAtLeast(0f),
            widthMm = bounds.width.toFloat().coerceAtLeast(5f),
            heightMm = bounds.height.toFloat().coerceAtLeast(5f),
            normalizedPaths = polylines,
            strokeColorHex = 0xFFFF4D6DL,
            fillColorHex = null
        )
    }

    fun simplifyElement(element: DesignElement, toleranceMm: Float): Pair<DesignElement, Pair<Int, Int>> {
        val baked = bakeToEditablePaths(element)
        val w = baked.widthMm.coerceAtLeast(1f)
        val h = baked.heightMm.coerceAtLeast(1f)
        var before = 0
        var after = 0
        val simplified = baked.normalizedPaths.map { poly ->
            before += poly.points.size
            val mmPts = poly.points.map { Point2D(it.xMm * w, it.yMm * h) }
            val simp = if (poly.closed && mmPts.size > 4) {
                val closedLoop = mmPts + mmPts.first()
                douglasPeucker(closedLoop, toleranceMm).dropLast(1).let { if (it.size >= 3) it else mmPts }
            } else if (mmPts.size >= 3) {
                douglasPeucker(mmPts, toleranceMm)
            } else mmPts
            after += simp.size
            Polyline2D(simp.map { Point2D(it.xMm / w, it.yMm / h) }, poly.closed)
        }
        return baked.copy(normalizedPaths = simplified) to (before to after)
    }

    fun smoothElement(element: DesignElement): DesignElement {
        val baked = bakeToEditablePaths(element)
        val next = baked.normalizedPaths.map { poly ->
            val pts = poly.points
            if (pts.size < 3) poly else {
                val out = mutableListOf<Point2D>()
                if (poly.closed) {
                    for (i in pts.indices) {
                        val a = pts[i]
                        val b = pts[(i + 1) % pts.size]
                        out.add(Point2D(0.75f * a.xMm + 0.25f * b.xMm, 0.75f * a.yMm + 0.25f * b.yMm))
                        out.add(Point2D(0.25f * a.xMm + 0.75f * b.xMm, 0.25f * a.yMm + 0.75f * b.yMm))
                    }
                } else {
                    out.add(pts.first())
                    for (i in 0 until pts.lastIndex) {
                        val a = pts[i]
                        val b = pts[i + 1]
                        out.add(Point2D(0.75f * a.xMm + 0.25f * b.xMm, 0.75f * a.yMm + 0.25f * b.yMm))
                        out.add(Point2D(0.25f * a.xMm + 0.75f * b.xMm, 0.25f * a.yMm + 0.75f * b.yMm))
                    }
                    out.add(pts.last())
                }
                poly.copy(points = out)
            }
        }
        return baked.copy(normalizedPaths = next)
    }

    fun closeOpenCurves(element: DesignElement): Pair<DesignElement, Int> {
        val baked = bakeToEditablePaths(element)
        var count = 0
        val next = baked.normalizedPaths.map { poly ->
            if (!poly.closed && poly.points.size >= 3) {
                count++
                poly.copy(closed = true)
            } else poly
        }
        return baked.copy(normalizedPaths = next) to count
    }

    private fun douglasPeucker(pts: List<Point2D>, tol: Float): List<Point2D> {
        if (pts.size < 3) return pts
        val a = pts.first()
        val b = pts.last()
        var maxD = 0f
        var idx = 0
        val dx = b.xMm - a.xMm
        val dy = b.yMm - a.yMm
        val len = hypot(dx, dy)
        for (i in 1 until pts.lastIndex) {
            val p = pts[i]
            val d = if (len < 1e-5f) hypot(p.xMm - a.xMm, p.yMm - a.yMm)
            else abs(dy * p.xMm - dx * p.yMm + b.xMm * a.yMm - b.yMm * a.xMm) / len
            if (d > maxD) {
                maxD = d
                idx = i
            }
        }
        return if (maxD > tol) {
            douglasPeucker(pts.subList(0, idx + 1), tol).dropLast(1) +
                douglasPeucker(pts.subList(idx, pts.size), tol)
        } else {
            listOf(a, b)
        }
    }

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
                Point2D(dx * cosA - dy * sinA + cx + element.xMm, dx * sinA + dy * cosA + cy + element.yMm)
            }
            Polyline2D(transformed, poly.closed)
        }
    }

    fun buildLocalPolylines(element: DesignElement): List<Polyline2D> {
        val w = element.widthMm.coerceAtLeast(1f)
        val h = element.heightMm.coerceAtLeast(1f)

        return when (element.type) {
            VectorShapeType.RECTANGLE -> listOf(
                Polyline2D(listOf(Point2D(0f, 0f), Point2D(w, 0f), Point2D(w, h), Point2D(0f, h)), closed = true)
            )
            VectorShapeType.ROUNDED_RECT -> {
                val r = element.cornerRadiusMm.coerceIn(0.5f, min(w, h) / 2f)
                val pts = mutableListOf<Point2D>()
                fun addArc(cx: Float, cy: Float, startDeg: Float, endDeg: Float) {
                    for (i in 0..8) {
                        val a = (startDeg + (endDeg - startDeg) * (i / 8f)) * (PI.toFloat() / 180f)
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
                val pts = (0 until 48).map { i ->
                    val t = (2f * PI.toFloat() * i) / 48f
                    Point2D(w / 2f + (w / 2f) * cos(t), h / 2f + (h / 2f) * sin(t))
                }
                listOf(Polyline2D(pts, closed = true))
            }
            VectorShapeType.STAR -> {
                val pc = element.starPoints.coerceIn(4, 12)
                val pts = (0 until pc * 2).map { i ->
                    val a = (-PI.toFloat() / 2f) + (i * PI.toFloat() / pc)
                    val ratio = if (i % 2 == 0) 1f else 0.42f
                    Point2D(w / 2f + (w / 2f) * ratio * cos(a), h / 2f + (h / 2f) * ratio * sin(a))
                }
                listOf(Polyline2D(pts, closed = true))
            }
            VectorShapeType.HEXAGON -> {
                val pts = (0 until 6).map { i ->
                    val a = (-PI.toFloat() / 2f) + (i * 2f * PI.toFloat() / 6f)
                    Point2D(w / 2f + (w / 2f) * cos(a), h / 2f + (h / 2f) * sin(a))
                }
                listOf(Polyline2D(pts, closed = true))
            }
            VectorShapeType.DIAMOND -> listOf(
                Polyline2D(listOf(Point2D(w / 2f, 0f), Point2D(w, h / 2f), Point2D(w / 2f, h), Point2D(0f, h / 2f)), closed = true)
            )
            VectorShapeType.HEART -> {
                val raw = (0 until 56).map { i ->
                    val t = (2f * PI.toFloat() * i) / 56f
                    val x = 16f * sin(t).pow(3)
                    val y = -(13f * cos(t) - 5f * cos(2 * t) - 2f * cos(3 * t) - cos(4 * t))
                    x to y
                }
                val minX = raw.minOf { it.first }; val maxX = raw.maxOf { it.first }
                val minY = raw.minOf { it.second }; val maxY = raw.maxOf { it.second }
                listOf(Polyline2D(raw.map { (x, y) ->
                    Point2D(((x - minX) / (maxX - minX)) * w, ((y - minY) / (maxY - minY)) * h)
                }, closed = true))
            }
            VectorShapeType.ISLAMIC_STAR -> {
                val outer = (0 until 16).map { i ->
                    val a = i * (2f * PI.toFloat() / 16f)
                    val rf = if (i % 2 == 0) 1f else 0.7071f
                    Point2D(w / 2f + (w / 2f) * rf * cos(a), h / 2f + (h / 2f) * rf * sin(a))
                }
                val inner = (0 until 8).map { i ->
                    val a = i * (2f * PI.toFloat() / 8f) + (PI.toFloat() / 8f)
                    Point2D(w / 2f + (w * 0.24f) * cos(a), h / 2f + (h * 0.24f) * sin(a))
                }
                listOf(Polyline2D(outer, true), Polyline2D(inner, true))
            }
            VectorShapeType.ARROW -> listOf(
                Polyline2D(
                    listOf(
                        Point2D(0f, h * 0.30f), Point2D(w * 0.60f, h * 0.30f),
                        Point2D(w * 0.60f, 0f), Point2D(w, h * 0.50f),
                        Point2D(w * 0.60f, h), Point2D(w * 0.60f, h * 0.70f), Point2D(0f, h * 0.70f)
                    ),
                    closed = true
                )
            )
            VectorShapeType.REGISTRATION_MARKS -> {
                val arm = min(w, h) * 0.18f
                listOf(
                    Polyline2D(listOf(Point2D(0f, arm), Point2D(0f, 0f), Point2D(arm, 0f)), false),
                    Polyline2D(listOf(Point2D(w - arm, 0f), Point2D(w, 0f), Point2D(w, arm)), false),
                    Polyline2D(listOf(Point2D(0f, h - arm), Point2D(0f, h), Point2D(arm, h)), false),
                    Polyline2D(listOf(Point2D(w - arm, h), Point2D(w, h), Point2D(w, h - arm)), false)
                )
            }
            VectorShapeType.TEXT,
            VectorShapeType.IMPORTED_SVG,
            VectorShapeType.TRACED_IMAGE,
            VectorShapeType.CUSTOM_PATH -> {
                val norm = if (element.normalizedPaths.isNotEmpty()) element.normalizedPaths
                else if (element.type == VectorShapeType.TEXT) vectorizeText(element.textContent, element.fontFamily).first
                else emptyList()
                norm.map { poly ->
                    Polyline2D(poly.points.map { Point2D(it.xMm * w, it.yMm * h) }, poly.closed)
                }
            }
        }
    }

    fun computeElementsBoundingBox(elements: List<DesignElement>): BoundingBoxMm? {
        if (elements.isEmpty()) return null
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        elements.forEach { el ->
            buildWorldPolylines(el).forEach { poly ->
                poly.points.forEach { pt ->
                    minX = min(minX, pt.xMm); minY = min(minY, pt.yMm)
                    maxX = max(maxX, pt.xMm); maxY = max(maxY, pt.yMm)
                }
            }
        }
        return if (minX == Float.MAX_VALUE) null else BoundingBoxMm(minX, minY, maxX, maxY)
    }

    fun alignSelection(
        elements: List<DesignElement>,
        selectedIds: Set<String>,
        matConfig: CuttingMatConfig,
        action: AlignmentAction
    ): List<DesignElement> {
        val targets = elements.filter { it.id in selectedIds && !it.isLocked }
        if (targets.isEmpty()) return elements

        if (action == AlignmentAction.DISTRIBUTE_HORIZONTAL || action == AlignmentAction.DISTRIBUTE_VERTICAL) {
            if (targets.size < 3) return elements
            val posById = mutableMapOf<String, Pair<Float, Float>>()
            if (action == AlignmentAction.DISTRIBUTE_HORIZONTAL) {
                val sorted = targets.sortedBy { it.xMm }
                val totalSpan = (sorted.last().xMm + sorted.last().widthMm) - sorted.first().xMm
                val sumW = sorted.sumOf { it.widthMm.toDouble() }.toFloat()
                val gap = (totalSpan - sumW) / (sorted.size - 1)
                var cur = sorted.first().xMm
                sorted.forEach { el ->
                    posById[el.id] = cur.coerceIn(0f, (matConfig.widthMm - el.widthMm).coerceAtLeast(0f)) to el.yMm
                    cur += el.widthMm + gap
                }
            } else {
                val sorted = targets.sortedBy { it.yMm }
                val totalSpan = (sorted.last().yMm + sorted.last().heightMm) - sorted.first().yMm
                val sumH = sorted.sumOf { it.heightMm.toDouble() }.toFloat()
                val gap = (totalSpan - sumH) / (sorted.size - 1)
                var cur = sorted.first().yMm
                sorted.forEach { el ->
                    posById[el.id] = el.xMm to cur.coerceIn(0f, (matConfig.heightMm - el.heightMm).coerceAtLeast(0f))
                    cur += el.heightMm + gap
                }
            }
            return elements.map { el -> posById[el.id]?.let { el.copy(xMm = it.first, yMm = it.second) } ?: el }
        }

        val refMinX = if (targets.size == 1 || action == AlignmentAction.CENTER_MAT) 0f else targets.minOf { it.xMm }
        val refMinY = if (targets.size == 1 || action == AlignmentAction.CENTER_MAT) 0f else targets.minOf { it.yMm }
        val refMaxX = if (targets.size == 1 || action == AlignmentAction.CENTER_MAT) matConfig.widthMm else targets.maxOf { it.xMm + it.widthMm }
        val refMaxY = if (targets.size == 1 || action == AlignmentAction.CENTER_MAT) matConfig.heightMm else targets.maxOf { it.yMm + it.heightMm }
        val cx = (refMinX + refMaxX) / 2f
        val cy = (refMinY + refMaxY) / 2f

        return elements.map { el ->
            if (el.id !in selectedIds || el.isLocked) el else {
                var nx = el.xMm; var ny = el.yMm
                when (action) {
                    AlignmentAction.ALIGN_LEFT -> nx = refMinX
                    AlignmentAction.CENTER_HORIZONTAL -> nx = cx - el.widthMm / 2f
                    AlignmentAction.ALIGN_RIGHT -> nx = refMaxX - el.widthMm
                    AlignmentAction.ALIGN_TOP -> ny = refMinY
                    AlignmentAction.CENTER_VERTICAL -> ny = cy - el.heightMm / 2f
                    AlignmentAction.ALIGN_BOTTOM -> ny = refMaxY - el.heightMm
                    AlignmentAction.CENTER_MAT -> {
                        val gminX = targets.minOf { it.xMm }; val gminY = targets.minOf { it.yMm }
                        val gw = targets.maxOf { it.xMm + it.widthMm } - gminX
                        val gh = targets.maxOf { it.yMm + it.heightMm } - gminY
                        nx = el.xMm + ((matConfig.widthMm - gw) / 2f - gminX)
                        ny = el.yMm + ((matConfig.heightMm - gh) / 2f - gminY)
                    }
                    else -> Unit
                }
                el.copy(
                    xMm = nx.coerceIn(0f, (matConfig.widthMm - el.widthMm).coerceAtLeast(0f)),
                    yMm = ny.coerceIn(0f, (matConfig.heightMm - el.heightMm).coerceAtLeast(0f))
                )
            }
        }
    }
}
