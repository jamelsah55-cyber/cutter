package com.example.domain.vector

import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class ParsedVectorImport(
    val normalizedPaths: List<Polyline2D>,
    val widthMm: Float,
    val heightMm: Float
)

/**
 * Parses standard SVG vector files and HPGL (.plt) files into normalized [0..1] polylines
 * ready for the interactive cutting mat and OTG plotter engine.
 */
object SvgPathParser {

    fun parseFileContent(fileName: String, content: String): ParsedVectorImport? {
        val trimmed = content.trim()
        return if (fileName.endsWith(".plt", ignoreCase = true) ||
            trimmed.startsWith("IN;") ||
            trimmed.startsWith("PU") ||
            trimmed.startsWith("PD")
        ) {
            parseHpgl(trimmed)
        } else {
            parseSvg(trimmed)
        }
    }

    private fun parseHpgl(hpgl: String): ParsedVectorImport? {
        val polylines = mutableListOf<Polyline2D>()
        var current = mutableListOf<Point2D>()
        var penDown = false
        val tokens = hpgl.split(';', '\n', '\r')
        for (raw in tokens) {
            val cmd = raw.trim().uppercase()
            if (cmd.startsWith("PU")) {
                if (current.size >= 2) {
                    polylines.add(Polyline2D(current.toList(), closed = false))
                }
                current = mutableListOf()
                penDown = false
                extractCoords(cmd.drop(2)).lastOrNull()?.let { current.add(it) }
            } else if (cmd.startsWith("PD")) {
                penDown = true
                val coords = extractCoords(cmd.drop(2))
                current.addAll(coords)
            } else if (cmd.startsWith("PA") && penDown) {
                current.addAll(extractCoords(cmd.drop(2)))
            }
        }
        if (current.size >= 2) {
            polylines.add(Polyline2D(current.toList(), closed = false))
        }
        return normalizePolylines(polylines, unitsPerMm = 40f)
    }

    private fun extractCoords(arg: String): List<Point2D> {
        val nums = Regex("""[-+]?\d*\.?\d+""").findAll(arg).map { it.value.toFloat() }.toList()
        val pts = mutableListOf<Point2D>()
        var i = 0
        while (i + 1 < nums.size) {
            pts.add(Point2D(nums[i], nums[i + 1]))
            i += 2
        }
        return pts
    }

    private fun parseSvg(svgText: String): ParsedVectorImport? {
        val polylines = mutableListOf<Polyline2D>()
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(svgText)))
            val nodes = doc.getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                when (el.tagName.lowercase()) {
                    "path" -> {
                        val d = el.getAttribute("d")
                        if (d.isNotBlank()) polylines.addAll(parsePathData(d))
                    }
                    "rect" -> {
                        val x = el.getAttribute("x").toFloatOrNull() ?: 0f
                        val y = el.getAttribute("y").toFloatOrNull() ?: 0f
                        val w = el.getAttribute("width").toFloatOrNull() ?: 0f
                        val h = el.getAttribute("height").toFloatOrNull() ?: 0f
                        if (w > 0f && h > 0f) {
                            polylines.add(
                                Polyline2D(
                                    listOf(Point2D(x, y), Point2D(x + w, y), Point2D(x + w, y + h), Point2D(x, y + h)),
                                    closed = true
                                )
                            )
                        }
                    }
                    "circle" -> {
                        val cx = el.getAttribute("cx").toFloatOrNull() ?: 0f
                        val cy = el.getAttribute("cy").toFloatOrNull() ?: 0f
                        val r = el.getAttribute("r").toFloatOrNull() ?: 0f
                        if (r > 0f) {
                            val pts = (0 until 48).map { idx ->
                                val a = (2f * PI.toFloat() * idx) / 48f
                                Point2D(cx + r * cos(a), cy + r * sin(a))
                            }
                            polylines.add(Polyline2D(pts, closed = true))
                        }
                    }
                    "ellipse" -> {
                        val cx = el.getAttribute("cx").toFloatOrNull() ?: 0f
                        val cy = el.getAttribute("cy").toFloatOrNull() ?: 0f
                        val rx = el.getAttribute("rx").toFloatOrNull() ?: 0f
                        val ry = el.getAttribute("ry").toFloatOrNull() ?: 0f
                        if (rx > 0f && ry > 0f) {
                            val pts = (0 until 48).map { idx ->
                                val a = (2f * PI.toFloat() * idx) / 48f
                                Point2D(cx + rx * cos(a), cy + ry * sin(a))
                            }
                            polylines.add(Polyline2D(pts, closed = true))
                        }
                    }
                    "line" -> {
                        val x1 = el.getAttribute("x1").toFloatOrNull() ?: 0f
                        val y1 = el.getAttribute("y1").toFloatOrNull() ?: 0f
                        val x2 = el.getAttribute("x2").toFloatOrNull() ?: 0f
                        val y2 = el.getAttribute("y2").toFloatOrNull() ?: 0f
                        polylines.add(Polyline2D(listOf(Point2D(x1, y1), Point2D(x2, y2)), closed = false))
                    }
                    "polygon", "polyline" -> {
                        val ptsStr = el.getAttribute("points")
                        val pts = extractCoords(ptsStr)
                        if (pts.size >= 2) {
                            polylines.add(Polyline2D(pts, closed = el.tagName.equals("polygon", true)))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }

        return normalizePolylines(polylines, unitsPerMm = 3.7795f)
    }

    private fun normalizePolylines(
        raw: List<Polyline2D>,
        unitsPerMm: Float
    ): ParsedVectorImport? {
        if (raw.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        raw.forEach { poly ->
            poly.points.forEach { p ->
                minX = min(minX, p.xMm)
                minY = min(minY, p.yMm)
                maxX = max(maxX, p.xMm)
                maxY = max(maxY, p.yMm)
            }
        }

        val rawW = (maxX - minX).coerceAtLeast(1f)
        val rawH = (maxY - minY).coerceAtLeast(1f)
        val normalized = raw.map { poly ->
            Polyline2D(
                points = poly.points.map { p ->
                    Point2D((p.xMm - minX) / rawW, (p.yMm - minY) / rawH)
                },
                closed = poly.closed
            )
        }

        val mmW = (rawW / unitsPerMm).coerceIn(30f, 240f)
        val aspect = rawH / rawW
        val mmH = (mmW * aspect).coerceIn(20f, 240f)
        return ParsedVectorImport(normalized, mmW, mmH)
    }

    /**
     * Parses SVG path `d` commands (M/m, L/l, H/h, V/v, C/c, Q/q, Z/z) into flattened polylines.
     */
    fun parsePathData(d: String): List<Polyline2D> {
        val result = mutableListOf<Polyline2D>()
        val tokenRegex = Regex("""[MmLlHhVvCcQqZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?""")
        val tokens = tokenRegex.findAll(d).map { it.value }.toList()

        var idx = 0
        var cmd = ' '
        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var currentPoly = mutableListOf<Point2D>()

        fun hasNumber(): Boolean = idx < tokens.size && tokens[idx][0].let { it.isDigit() || it == '-' || it == '+' || it == '.' }
        fun nextFloat(): Float = tokens[idx++].toFloatOrNull() ?: 0f

        while (idx < tokens.size) {
            val t = tokens[idx]
            if (t.length == 1 && t[0].isLetter()) {
                cmd = t[0]
                idx++
            }
            when (cmd) {
                'M', 'm' -> {
                    if (currentPoly.size >= 2) result.add(Polyline2D(currentPoly.toList(), closed = false))
                    currentPoly = mutableListOf()
                    val rel = cmd == 'm'
                    if (hasNumber()) {
                        val x = nextFloat() + if (rel) curX else 0f
                        val y = nextFloat() + if (rel) curY else 0f
                        curX = x; curY = y; startX = x; startY = y
                        currentPoly.add(Point2D(curX, curY))
                        cmd = if (rel) 'l' else 'L'
                    }
                }
                'L', 'l' -> {
                    val rel = cmd == 'l'
                    while (hasNumber()) {
                        curX = nextFloat() + if (rel) curX else 0f
                        curY = nextFloat() + if (rel) curY else 0f
                        currentPoly.add(Point2D(curX, curY))
                    }
                }
                'H', 'h' -> {
                    val rel = cmd == 'h'
                    while (hasNumber()) {
                        curX = nextFloat() + if (rel) curX else 0f
                        currentPoly.add(Point2D(curX, curY))
                    }
                }
                'V', 'v' -> {
                    val rel = cmd == 'v'
                    while (hasNumber()) {
                        curY = nextFloat() + if (rel) curY else 0f
                        currentPoly.add(Point2D(curX, curY))
                    }
                }
                'C', 'c' -> {
                    val rel = cmd == 'c'
                    while (hasNumber()) {
                        val bx = if (rel) curX else 0f
                        val by = if (rel) curY else 0f
                        val x1 = nextFloat() + bx
                        val y1 = nextFloat() + by
                        val x2 = nextFloat() + bx
                        val y2 = nextFloat() + by
                        val x3 = nextFloat() + bx
                        val y3 = nextFloat() + by
                        val steps = 10
                        for (s in 1..steps) {
                            val u = s.toFloat() / steps
                            val inv = 1f - u
                            val px = inv * inv * inv * curX + 3 * inv * inv * u * x1 + 3 * inv * u * u * x2 + u * u * u * x3
                            val py = inv * inv * inv * curY + 3 * inv * inv * u * y1 + 3 * inv * u * u * y2 + u * u * u * y3
                            currentPoly.add(Point2D(px, py))
                        }
                        curX = x3; curY = y3
                    }
                }
                'Q', 'q' -> {
                    val rel = cmd == 'q'
                    while (hasNumber()) {
                        val bx = if (rel) curX else 0f
                        val by = if (rel) curY else 0f
                        val x1 = nextFloat() + bx
                        val y1 = nextFloat() + by
                        val x2 = nextFloat() + bx
                        val y2 = nextFloat() + by
                        val steps = 8
                        for (s in 1..steps) {
                            val u = s.toFloat() / steps
                            val inv = 1f - u
                            val px = inv * inv * curX + 2 * inv * u * x1 + u * u * x2
                            val py = inv * inv * curY + 2 * inv * u * y1 + u * u * y2
                            currentPoly.add(Point2D(px, py))
                        }
                        curX = x2; curY = y2
                    }
                }
                'Z', 'z' -> {
                    if (currentPoly.size >= 2) {
                        result.add(Polyline2D(currentPoly.toList(), closed = true))
                    }
                    currentPoly = mutableListOf()
                    curX = startX
                    curY = startY
                }
                else -> if (hasNumber()) nextFloat() else idx++
            }
        }
        if (currentPoly.size >= 2) {
            result.add(Polyline2D(currentPoly.toList(), closed = false))
        }
        return result
    }
}
