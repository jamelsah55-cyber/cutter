package com.cutterpro.desktop.vector

import com.cutterpro.desktop.model.BoundingBoxMm
import com.cutterpro.desktop.model.CuttingMatConfig
import com.cutterpro.desktop.model.DesignElement
import com.cutterpro.desktop.model.MaterialPreset
import com.cutterpro.desktop.model.PlotterProtocol
import com.cutterpro.desktop.model.Point2D
import com.cutterpro.desktop.model.Polyline2D
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.awt.image.BufferedImage
import java.io.File
import java.io.StringReader
import java.util.Locale
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class OptimizedPathsResult(
    val polylines: List<Polyline2D>,
    val rawTravelDistanceMm: Float,
    val optimizedTravelDistanceMm: Float,
    val savedTravelPercent: Int
)

data class CutPlan(
    val commands: List<String>,
    val cutDistanceMm: Float,
    val travelDistanceMm: Float,
    val estimatedSeconds: Int,
    val previewPolylines: List<Polyline2D>,
    val rawTravelDistanceMm: Float = travelDistanceMm,
    val savedTravelPercent: Int = 0,
    val boundingBoxMm: BoundingBoxMm? = null
)

object DesktopPathOptimizer {
    fun optimizeAndCompensate(
        input: List<Polyline2D>,
        optimizeOrder: Boolean,
        overcutMm: Float
    ): OptimizedPathsResult {
        val valid = input.filter { it.points.size >= 2 }
        if (valid.isEmpty()) return OptimizedPathsResult(emptyList(), 0f, 0f, 0)
        val rawTravel = computeTravelDistance(valid)
        val ordered = if (optimizeOrder) sortNearestNeighbor(valid) else valid
        val optTravel = computeTravelDistance(ordered)
        val compensated = if (overcutMm > 0.05f) {
            ordered.map { applyOvercut(it, overcutMm.coerceIn(0f, 2f)) }
        } else ordered
        val savedPct = if (rawTravel > 1f && optTravel < rawTravel) {
            (((rawTravel - optTravel) / rawTravel) * 100f).roundToInt().coerceIn(0, 99)
        } else 0
        return OptimizedPathsResult(compensated, rawTravel, optTravel, savedPct)
    }

    private fun sortNearestNeighbor(polylines: List<Polyline2D>): List<Polyline2D> {
        if (polylines.size <= 1) return polylines
        val remaining = polylines.toMutableList()
        val result = ArrayList<Polyline2D>(polylines.size)
        var headX = 0f; var headY = 0f
        while (remaining.isNotEmpty()) {
            var bestIdx = 0; var bestRev = false; var bestD = Float.MAX_VALUE
            for (i in remaining.indices) {
                val p = remaining[i]
                val ds = hypot(p.points.first().xMm - headX, p.points.first().yMm - headY)
                if (ds < bestD) { bestD = ds; bestIdx = i; bestRev = false }
                if (!p.closed) {
                    val de = hypot(p.points.last().xMm - headX, p.points.last().yMm - headY)
                    if (de < bestD) { bestD = de; bestIdx = i; bestRev = true }
                }
            }
            val chosen = remaining.removeAt(bestIdx)
            val oriented = if (bestRev) chosen.copy(points = chosen.points.asReversed()) else chosen
            result.add(oriented)
            val endPt = if (oriented.closed) oriented.points.first() else oriented.points.last()
            headX = endPt.xMm; headY = endPt.yMm
        }
        return result
    }

    private fun applyOvercut(poly: Polyline2D, overcutMm: Float): Polyline2D {
        if (!poly.closed || poly.points.size < 3 || overcutMm <= 0.05f) return poly
        val p0 = poly.points[0]; val p1 = poly.points[1]
        val len = hypot(p1.xMm - p0.xMm, p1.yMm - p0.yMm)
        if (len < 0.2f) return poly
        val ratio = minOf(overcutMm, len * 0.45f) / len
        val overPt = Point2D(p0.xMm + (p1.xMm - p0.xMm) * ratio, p0.yMm + (p1.yMm - p0.yMm) * ratio)
        return Polyline2D(poly.points + p0 + overPt, closed = false)
    }

    private fun computeTravelDistance(polylines: List<Polyline2D>): Float {
        var travel = 0f; var lx = 0f; var ly = 0f
        polylines.forEach { p ->
            if (p.points.size >= 2) {
                val f = p.points.first()
                travel += hypot(f.xMm - lx, f.yMm - ly)
                val e = if (p.closed) f else p.points.last()
                lx = e.xMm; ly = e.yMm
            }
        }
        return travel + hypot(lx, ly)
    }
}

class DesktopPlotterCommandGenerator(private val geometryEngine: DesktopVectorEngine) {

    fun compileCutPlan(
        elements: List<DesignElement>,
        matConfig: CuttingMatConfig,
        material: MaterialPreset,
        protocol: PlotterProtocol,
        selectedIds: Set<String> = emptySet()
    ): CutPlan {
        val visible = elements.filter { it.isVisible }
        val selFiltered = if (matConfig.cutOnlySelected && selectedIds.isNotEmpty()) {
            visible.filter { it.id in selectedIds }.ifEmpty { visible }
        } else visible
        val colorFilter = matConfig.cutColorFilterHex
        val active = if (colorFilter != null) {
            selFiltered.filter { (it.fillColorHex ?: it.strokeColorHex) == colorFilter }
        } else selFiltered

        val raw = mutableListOf<Polyline2D>()
        active.forEach { raw.addAll(geometryEngine.buildWorldPolylines(it)) }

        val bbox = geometryEngine.computeElementsBoundingBox(active)
        if (matConfig.weedingBoxEnabled && bbox != null) {
            val m = matConfig.weedingMarginMm.coerceAtLeast(1f)
            val x0 = (bbox.minX - m).coerceAtLeast(0f)
            val y0 = (bbox.minY - m).coerceAtLeast(0f)
            val x1 = (bbox.maxX + m).coerceAtMost(matConfig.widthMm)
            val y1 = (bbox.maxY + m).coerceAtMost(matConfig.heightMm)
            raw.add(Polyline2D(listOf(Point2D(x0, y0), Point2D(x1, y0), Point2D(x1, y1), Point2D(x0, y1)), true))
        }

        val opt = DesktopPathOptimizer.optimizeAndCompensate(raw, matConfig.optimizeCutOrder, matConfig.overcutMm)
        var cutDist = 0f
        opt.polylines.forEach { poly ->
            if (poly.points.size >= 2) {
                var prev = poly.points.first()
                for (i in 1 until poly.points.size) {
                    val cur = poly.points[i]
                    cutDist += hypot(cur.xMm - prev.xMm, cur.yMm - prev.yMm)
                    prev = cur
                }
                if (poly.closed) cutDist += hypot(poly.points.first().xMm - prev.xMm, poly.points.first().yMm - prev.yMm)
            }
        }
        val passes = material.passes.coerceIn(1, 5)
        cutDist *= passes

        val commands = when (protocol) {
            PlotterProtocol.HPGL -> buildHpgl(opt.polylines, material, passes)
            PlotterProtocol.GPGL -> buildGpgl(opt.polylines, material, passes)
            PlotterProtocol.GRBL -> buildGrbl(opt.polylines, material, passes)
        }
        val spd = material.speedMmPerSec.coerceAtLeast(10).toFloat()
        val estSec = ((cutDist / spd) + (opt.optimizedTravelDistanceMm / (spd * 1.6f)) + 2f).roundToInt()

        return CutPlan(commands, cutDist, opt.optimizedTravelDistanceMm, estSec, opt.polylines, opt.rawTravelDistanceMm, opt.savedTravelPercent, bbox)
    }

    private fun buildHpgl(polys: List<Polyline2D>, mat: MaterialPreset, passes: Int): List<String> {
        val cmds = mutableListOf("IN;", "SP1;", "VS${(mat.speedMmPerSec / 10).coerceIn(1, 40)};", "FS${mat.forceGrams.coerceIn(10, 500)};")
        repeat(passes) {
            polys.forEach { poly ->
                if (poly.points.size >= 2) {
                    val p0 = poly.points.first()
                    cmds.add("PU${(p0.xMm * 40f).roundToInt()},${(p0.yMm * 40f).roundToInt()};")
                    val pathPts = if (poly.closed) poly.points + poly.points.first() else poly.points
                    val pd = pathPts.drop(1).joinToString(",") { "${(it.xMm * 40f).roundToInt()},${(it.yMm * 40f).roundToInt()}" }
                    if (pd.isNotEmpty()) cmds.add("PD$pd;")
                }
            }
        }
        cmds.add("PU0,0;"); cmds.add("SP0;")
        return cmds
    }

    private fun buildGpgl(polys: List<Polyline2D>, mat: MaterialPreset, passes: Int): List<String> {
        val cmds = mutableListOf("!;", "FX${(mat.speedMmPerSec / 15).coerceIn(1, 30)},1")
        repeat(passes) {
            polys.forEach { poly ->
                if (poly.points.size >= 2) {
                    val p0 = poly.points.first()
                    cmds.add("M${(p0.xMm * 20f).roundToInt()},${(p0.yMm * 20f).roundToInt()}")
                    val pathPts = if (poly.closed) poly.points + poly.points.first() else poly.points
                    pathPts.drop(1).forEach { cmds.add("D${(it.xMm * 20f).roundToInt()},${(it.yMm * 20f).roundToInt()}") }
                }
            }
        }
        cmds.add("M0,0")
        return cmds
    }

    private fun buildGrbl(polys: List<Polyline2D>, mat: MaterialPreset, passes: Int): List<String> {
        val cmds = mutableListOf("G21", "G90", "G0 Z5.000", "F${(mat.speedMmPerSec * 60).coerceIn(100, 6000)}")
        repeat(passes) {
            polys.forEach { poly ->
                if (poly.points.size >= 2) {
                    val p0 = poly.points.first()
                    cmds.add(String.format(Locale.US, "G0 X%.2f Y%.2f", p0.xMm, p0.yMm))
                    cmds.add("G1 Z-0.400")
                    val pathPts = if (poly.closed) poly.points + poly.points.first() else poly.points
                    pathPts.drop(1).forEach { cmds.add(String.format(Locale.US, "G1 X%.2f Y%.2f", it.xMm, it.yMm)) }
                    cmds.add("G0 Z5.000")
                }
            }
        }
        cmds.add("G0 X0.00 Y0.00"); cmds.add("M2")
        return cmds
    }

    fun buildJogCommand(protocol: PlotterProtocol, dxMm: Float, dyMm: Float): List<String> = when (protocol) {
        PlotterProtocol.HPGL -> listOf("IN;", "PR${(dxMm * 40f).roundToInt()},${(dyMm * 40f).roundToInt()};")
        PlotterProtocol.GPGL -> listOf("O${(dxMm * 20f).roundToInt()},${(dyMm * 20f).roundToInt()}")
        PlotterProtocol.GRBL -> listOf("G91", String.format(Locale.US, "G0 X%.2f Y%.2f", dxMm, dyMm), "G90")
    }

    fun buildHomeCommand(protocol: PlotterProtocol): List<String> = when (protocol) {
        PlotterProtocol.HPGL -> listOf("PU0,0;")
        PlotterProtocol.GPGL -> listOf("M0,0")
        PlotterProtocol.GRBL -> listOf("G90", "G0 Z5.00", "G0 X0.00 Y0.00")
    }

    fun buildSetOriginCommand(protocol: PlotterProtocol): List<String> = when (protocol) {
        PlotterProtocol.HPGL -> listOf("IN;")
        PlotterProtocol.GPGL -> listOf("!;")
        PlotterProtocol.GRBL -> listOf("G92 X0 Y0 Z0")
    }

    fun buildEmergencyStopCommand(protocol: PlotterProtocol): List<String> = when (protocol) {
        PlotterProtocol.HPGL -> listOf("PU;", "IN;", "SP0;")
        PlotterProtocol.GPGL -> listOf("!;")
        PlotterProtocol.GRBL -> listOf("!", "\u0018", "M5")
    }

    fun buildTestSquareCommand(protocol: PlotterProtocol, mat: MaterialPreset): List<String> {
        val sq = Polyline2D(listOf(Point2D(0f, 0f), Point2D(20f, 0f), Point2D(20f, 20f), Point2D(0f, 20f)), true)
        return when (protocol) {
            PlotterProtocol.HPGL -> buildHpgl(listOf(sq), mat, 1)
            PlotterProtocol.GPGL -> buildGpgl(listOf(sq), mat, 1)
            PlotterProtocol.GRBL -> buildGrbl(listOf(sq), mat, 1)
        }
    }

    fun exportAsSvg(elements: List<DesignElement>, matConfig: CuttingMatConfig): String {
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${matConfig.widthMm}mm" height="${matConfig.heightMm}mm" viewBox="0 0 ${matConfig.widthMm} ${matConfig.heightMm}">""").append('\n')
        elements.filter { it.isVisible }.forEach { el ->
            val strokeHex = String.format(Locale.US, "#%06X", el.strokeColorHex and 0xFFFFFFL)
            val fillHex = el.fillColorHex?.let { String.format(Locale.US, "#%06X", it and 0xFFFFFFL) } ?: "none"
            val d = buildString {
                geometryEngine.buildWorldPolylines(el).forEach { poly ->
                    if (poly.points.size >= 2) {
                        poly.points.forEachIndexed { idx, pt ->
                            append(if (idx == 0) "M " else "L ")
                            append(String.format(Locale.US, "%.2f %.2f ", pt.xMm, pt.yMm))
                        }
                        if (poly.closed) append("Z ")
                    }
                }
            }.trim()
            if (d.isNotEmpty()) {
                sb.append("""  <path d="$d" fill="$fillHex" fill-rule="evenodd" stroke="$strokeHex" stroke-width="0.4" />""").append('\n')
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
    }
}

data class ParsedVectorImport(
    val normalizedPaths: List<Polyline2D>,
    val widthMm: Float,
    val heightMm: Float
)

object DesktopSvgPathParser {
    fun parseFileContent(fileName: String, content: String): ParsedVectorImport? {
        val trimmed = content.trim()
        return if (fileName.endsWith(".plt", true) || trimmed.startsWith("IN;") || trimmed.startsWith("PU") || trimmed.startsWith("PD")) {
            parseHpgl(trimmed)
        } else {
            parseSvg(trimmed)
        }
    }

    private fun parseHpgl(hpgl: String): ParsedVectorImport? {
        val polys = mutableListOf<Polyline2D>()
        var cur = mutableListOf<Point2D>()
        var penDown = false
        hpgl.split(';', '\n', '\r').forEach { raw ->
            val cmd = raw.trim().uppercase()
            when {
                cmd.startsWith("PU") -> {
                    if (cur.size >= 2) polys.add(Polyline2D(cur.toList(), false))
                    cur = mutableListOf(); penDown = false
                    extractCoords(cmd.drop(2)).lastOrNull()?.let { cur.add(it) }
                }
                cmd.startsWith("PD") -> { penDown = true; cur.addAll(extractCoords(cmd.drop(2))) }
                cmd.startsWith("PA") && penDown -> cur.addAll(extractCoords(cmd.drop(2)))
            }
        }
        if (cur.size >= 2) polys.add(Polyline2D(cur.toList(), false))
        return normalize(polys, 40f)
    }

    private fun extractCoords(arg: String): List<Point2D> {
        val nums = Regex("""[-+]?\d*\.?\d+""").findAll(arg).map { it.value.toFloat() }.toList()
        return buildList {
            var i = 0
            while (i + 1 < nums.size) { add(Point2D(nums[i], nums[i + 1])); i += 2 }
        }
    }

    private fun parseSvg(svgText: String): ParsedVectorImport? {
        val polys = mutableListOf<Polyline2D>()
        runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; isValidating = false }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(svgText)))
            val nodes = doc.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                when (el.tagName.lowercase()) {
                    "path" -> el.getAttribute("d").takeIf { it.isNotBlank() }?.let { polys.addAll(parsePathData(it)) }
                    "rect" -> {
                        val x = el.getAttribute("x").toFloatOrNull() ?: 0f
                        val y = el.getAttribute("y").toFloatOrNull() ?: 0f
                        val w = el.getAttribute("width").toFloatOrNull() ?: 0f
                        val h = el.getAttribute("height").toFloatOrNull() ?: 0f
                        if (w > 0f && h > 0f) polys.add(Polyline2D(listOf(Point2D(x, y), Point2D(x + w, y), Point2D(x + w, y + h), Point2D(x, y + h)), true))
                    }
                    "circle" -> {
                        val cx = el.getAttribute("cx").toFloatOrNull() ?: 0f; val cy = el.getAttribute("cy").toFloatOrNull() ?: 0f
                        val r = el.getAttribute("r").toFloatOrNull() ?: 0f
                        if (r > 0f) polys.add(Polyline2D((0 until 48).map { val a = (2f * PI.toFloat() * it) / 48f; Point2D(cx + r * cos(a), cy + r * sin(a)) }, true))
                    }
                    "polygon", "polyline" -> {
                        val pts = extractCoords(el.getAttribute("points"))
                        if (pts.size >= 2) polys.add(Polyline2D(pts, el.tagName.equals("polygon", true)))
                    }
                }
            }
        }.onFailure { return null }
        return normalize(polys, 3.7795f)
    }

    private fun normalize(raw: List<Polyline2D>, unitsPerMm: Float): ParsedVectorImport? {
        if (raw.isEmpty()) return null
        val minX = raw.minOf { p -> p.points.minOf { it.xMm } }
        val minY = raw.minOf { p -> p.points.minOf { it.yMm } }
        val maxX = raw.maxOf { p -> p.points.maxOf { it.xMm } }
        val maxY = raw.maxOf { p -> p.points.maxOf { it.yMm } }
        val rw = (maxX - minX).coerceAtLeast(1f)
        val rh = (maxY - minY).coerceAtLeast(1f)
        val norm = raw.map { poly -> Polyline2D(poly.points.map { Point2D((it.xMm - minX) / rw, (it.yMm - minY) / rh) }, poly.closed) }
        val mmW = (rw / unitsPerMm).coerceIn(30f, 240f)
        val mmH = (mmW * (rh / rw)).coerceIn(20f, 240f)
        return ParsedVectorImport(norm, mmW, mmH)
    }

    fun parsePathData(d: String): List<Polyline2D> {
        val res = mutableListOf<Polyline2D>()
        val tokens = Regex("""[MmLlHhVvCcQqZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?""").findAll(d).map { it.value }.toList()
        var idx = 0; var cmd = ' '; var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
        var cur = mutableListOf<Point2D>()
        fun hasNum() = idx < tokens.size && tokens[idx][0].let { it.isDigit() || it == '-' || it == '+' || it == '.' }
        fun nextF() = tokens[idx++].toFloatOrNull() ?: 0f
        while (idx < tokens.size) {
            val t = tokens[idx]
            if (t.length == 1 && t[0].isLetter()) { cmd = t[0]; idx++ }
            when (cmd) {
                'M', 'm' -> {
                    if (cur.size >= 2) res.add(Polyline2D(cur.toList(), false))
                    cur = mutableListOf()
                    val rel = cmd == 'm'
                    if (hasNum()) {
                        cx = nextF() + if (rel) cx else 0f; cy = nextF() + if (rel) cy else 0f
                        sx = cx; sy = cy; cur.add(Point2D(cx, cy)); cmd = if (rel) 'l' else 'L'
                    }
                }
                'L', 'l' -> { val r = cmd == 'l'; while (hasNum()) { cx = nextF() + if (r) cx else 0f; cy = nextF() + if (r) cy else 0f; cur.add(Point2D(cx, cy)) } }
                'H', 'h' -> { val r = cmd == 'h'; while (hasNum()) { cx = nextF() + if (r) cx else 0f; cur.add(Point2D(cx, cy)) } }
                'V', 'v' -> { val r = cmd == 'v'; while (hasNum()) { cy = nextF() + if (r) cy else 0f; cur.add(Point2D(cx, cy)) } }
                'C', 'c' -> {
                    val r = cmd == 'c'
                    while (hasNum()) {
                        val bx = if (r) cx else 0f; val by = if (r) cy else 0f
                        val x1 = nextF() + bx; val y1 = nextF() + by
                        val x2 = nextF() + bx; val y2 = nextF() + by
                        val x3 = nextF() + bx; val y3 = nextF() + by
                        for (s in 1..10) {
                            val u = s / 10f; val inv = 1f - u
                            cur.add(Point2D(inv*inv*inv*cx + 3*inv*inv*u*x1 + 3*inv*u*u*x2 + u*u*u*x3, inv*inv*inv*cy + 3*inv*inv*u*y1 + 3*inv*u*u*y2 + u*u*u*y3))
                        }
                        cx = x3; cy = y3
                    }
                }
                'Z', 'z' -> { if (cur.size >= 2) res.add(Polyline2D(cur.toList(), true)); cur = mutableListOf(); cx = sx; cy = sy }
                else -> if (hasNum()) nextF() else idx++
            }
        }
        if (cur.size >= 2) res.add(Polyline2D(cur.toList(), false))
        return res
    }
}

object DesktopImageTracer {
    fun loadAndTraceImageFile(file: File, threshold: Int = 128, invert: Boolean = false): Pair<List<Polyline2D>, Float>? {
        val raw = runCatching { ImageIO.read(file) }.getOrNull() ?: return null
        val maxSide = 320
        val scale = min(1f, maxSide.toFloat() / max(raw.width, raw.height).coerceAtLeast(1))
        val w = (raw.width * scale).toInt().coerceIn(24, maxSide)
        val h = (raw.height * scale).toInt().coerceIn(24, maxSide)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.drawImage(raw, 0, 0, w, h, null)
        g.dispose()

        val mask = BooleanArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val argb = img.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val gr = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val lum = (0.299f * r + 0.587f * gr + 0.114f * b).toInt()
                mask[y * w + x] = a >= 64 && if (invert) lum >= threshold else lum < threshold
            }
        }
        val adj = HashMap<Long, MutableList<Long>>()
        fun key(x: Int, y: Int) = (x.toLong() shl 16) or (y.toLong() and 0xFFFFL)
        fun solid(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]
        fun addE(x0: Int, y0: Int, x1: Int, y1: Int) { adj.getOrPut(key(x0, y0)) { mutableListOf() }.add(key(x1, y1)) }
        for (y in 0 until h) for (x in 0 until w) {
            if (!solid(x, y)) continue
            if (!solid(x, y - 1)) addE(x, y, x + 1, y)
            if (!solid(x + 1, y)) addE(x + 1, y, x + 1, y + 1)
            if (!solid(x, y + 1)) addE(x + 1, y + 1, x, y + 1)
            if (!solid(x - 1, y)) addE(x, y + 1, x, y)
        }
        val loops = mutableListOf<List<Point2D>>()
        for (sk in adj.keys.toList()) {
            while (true) {
                val list = adj[sk]
                if (list.isNullOrEmpty()) break
                var ck = list.removeAt(list.lastIndex)
                val loop = mutableListOf(Point2D((sk shr 16).toFloat(), (sk and 0xFFFFL).toFloat()))
                var guard = 0
                while (ck != sk && guard++ < (w + 2) * (h + 2) * 4) {
                    loop.add(Point2D((ck shr 16).toFloat(), (ck and 0xFFFFL).toFloat()))
                    val nextList = adj[ck]
                    if (nextList.isNullOrEmpty()) break
                    ck = nextList.removeAt(0)
                }
                if (loop.size >= 8) loops.add(loop)
            }
        }
        if (loops.isEmpty()) return null
        val minX = loops.minOf { l -> l.minOf { it.xMm } }
        val minY = loops.minOf { l -> l.minOf { it.yMm } }
        val sx = (loops.maxOf { l -> l.maxOf { it.xMm } } - minX).coerceAtLeast(1f)
        val sy = (loops.maxOf { l -> l.maxOf { it.yMm } } - minY).coerceAtLeast(1f)
        val norm = loops.map { l -> Polyline2D(l.map { Point2D((it.xMm - minX) / sx, (it.yMm - minY) / sy) }, true) }
        return norm to (sx / sy).coerceIn(0.2f, 5f)
    }
}
