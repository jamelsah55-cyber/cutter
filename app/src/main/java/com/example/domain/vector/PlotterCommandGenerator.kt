package com.example.domain.vector

import com.example.domain.model.BoundingBoxMm
import com.example.domain.model.CuttingMatConfig
import com.example.domain.model.DesignElement
import com.example.domain.model.MaterialPreset
import com.example.domain.model.PlotterProtocol
import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

data class CutPlan(
    val commands: List<String>,
    val cutDistanceMm: Float,
    val travelDistanceMm: Float,
    val estimatedSeconds: Int,
    val previewPolylines: List<Polyline2D>,
    val rawTravelDistanceMm: Float = travelDistanceMm,
    val savedTravelPercent: Int = 0,
    val boundingBoxMm: BoundingBoxMm? = null
) {
    val totalDistanceMm: Float get() = cutDistanceMm + travelDistanceMm
}

class PlotterCommandGenerator(private val geometryEngine: VectorGeometryEngine) {

    fun compileCutPlan(
        elements: List<DesignElement>,
        matConfig: CuttingMatConfig,
        material: MaterialPreset,
        protocol: PlotterProtocol,
        selectedIds: Set<String> = emptySet()
    ): CutPlan {
        val visibleElements = elements.filter { it.isVisible }
        val selectedFiltered = if (matConfig.cutOnlySelected && selectedIds.isNotEmpty()) {
            visibleElements.filter { it.id in selectedIds }.ifEmpty { visibleElements }
        } else {
            visibleElements
        }
        val colorFilter = matConfig.cutColorFilterHex
        val activeElements = if (colorFilter != null) {
            selectedFiltered.filter { (it.fillColorHex ?: it.strokeColorHex) == colorFilter }
        } else {
            selectedFiltered
        }

        val rawPolylines = mutableListOf<Polyline2D>()
        activeElements.forEach { el ->
            rawPolylines.addAll(geometryEngine.buildWorldPolylines(el))
        }

        val boundingBox = geometryEngine.computeElementsBoundingBox(activeElements)
        if (matConfig.weedingBoxEnabled && boundingBox != null) {
            val m = matConfig.weedingMarginMm.coerceAtLeast(1f)
            val x0 = (boundingBox.minX - m).coerceAtLeast(0f)
            val y0 = (boundingBox.minY - m).coerceAtLeast(0f)
            val x1 = (boundingBox.maxX + m).coerceAtMost(matConfig.widthMm)
            val y1 = (boundingBox.maxY + m).coerceAtMost(matConfig.heightMm)
            rawPolylines.add(
                Polyline2D(
                    listOf(Point2D(x0, y0), Point2D(x1, y0), Point2D(x1, y1), Point2D(x0, y1)),
                    closed = true
                )
            )
        }

        val opt = PathOptimizer.optimizeAndCompensate(
            input = rawPolylines,
            optimizeOrder = matConfig.optimizeCutOrder,
            overcutMm = matConfig.overcutMm
        )
        val allPolylines = opt.polylines

        var cutDist = 0f
        allPolylines.forEach { poly ->
            if (poly.points.size >= 2) {
                val first = poly.points.first()
                var prev = first
                for (i in 1 until poly.points.size) {
                    val cur = poly.points[i]
                    cutDist += hypot(cur.xMm - prev.xMm, cur.yMm - prev.yMm)
                    prev = cur
                }
                if (poly.closed) {
                    cutDist += hypot(first.xMm - prev.xMm, first.yMm - prev.yMm)
                }
            }
        }

        val travelDist = opt.optimizedTravelDistanceMm
        val passes = material.passes.coerceIn(1, 5)
        cutDist *= passes

        val commands = when (protocol) {
            PlotterProtocol.HPGL -> buildHpgl(allPolylines, material, passes)
            PlotterProtocol.GPGL -> buildGpgl(allPolylines, material, passes)
            PlotterProtocol.GRBL -> buildGrbl(allPolylines, material, passes)
        }

        val speedMmSec = material.speedMmPerSec.coerceAtLeast(10).toFloat()
        val estSec = ((cutDist / speedMmSec) + (travelDist / (speedMmSec * 1.6f)) + 2f).roundToInt()

        return CutPlan(
            commands = commands,
            cutDistanceMm = cutDist,
            travelDistanceMm = travelDist,
            estimatedSeconds = estSec,
            previewPolylines = allPolylines,
            rawTravelDistanceMm = opt.rawTravelDistanceMm,
            savedTravelPercent = opt.savedTravelPercent,
            boundingBoxMm = boundingBox
        )
    }

    private fun buildHpgl(
        polylines: List<Polyline2D>,
        material: MaterialPreset,
        passes: Int
    ): List<String> {
        val cmds = mutableListOf<String>()
        // Standard HPGL: 40 plotter units = 1 mm
        val scale = 40f
        val vsSpeed = (material.speedMmPerSec / 10).coerceIn(1, 40)
        cmds.add("IN;")
        cmds.add("SP1;")
        cmds.add("VS$vsSpeed;")
        cmds.add("FS${material.forceGrams.coerceIn(10, 500)};")

        repeat(passes) {
            polylines.forEach { poly ->
                if (poly.points.size >= 2) {
                    val p0 = poly.points.first()
                    cmds.add("PU${(p0.xMm * scale).roundToInt()},${(p0.yMm * scale).roundToInt()};")
                    val pathPoints = if (poly.closed) poly.points + poly.points.first() else poly.points
                    val pdCoords = pathPoints.drop(1).joinToString(",") { pt ->
                        "${(pt.xMm * scale).roundToInt()},${(pt.yMm * scale).roundToInt()}"
                    }
                    if (pdCoords.isNotEmpty()) {
                        cmds.add("PD$pdCoords;")
                    }
                }
            }
        }
        cmds.add("PU0,0;")
        cmds.add("SP0;")
        return cmds
    }

    private fun buildGpgl(
        polylines: List<Polyline2D>,
        material: MaterialPreset,
        passes: Int
    ): List<String> {
        val cmds = mutableListOf<String>()
        // GPGL: 20 units = 1 mm
        val scale = 20f
        val speedCode = (material.speedMmPerSec / 15).coerceIn(1, 30)
        cmds.add("!;")
        cmds.add("FX$speedCode,1")
        repeat(passes) {
            polylines.forEach { poly ->
                if (poly.points.size >= 2) {
                    val p0 = poly.points.first()
                    cmds.add("M${(p0.xMm * scale).roundToInt()},${(p0.yMm * scale).roundToInt()}")
                    val pathPoints = if (poly.closed) poly.points + poly.points.first() else poly.points
                    pathPoints.drop(1).forEach { pt ->
                        cmds.add("D${(pt.xMm * scale).roundToInt()},${(pt.yMm * scale).roundToInt()}")
                    }
                }
            }
        }
        cmds.add("M0,0")
        return cmds
    }

    private fun buildGrbl(
        polylines: List<Polyline2D>,
        material: MaterialPreset,
        passes: Int
    ): List<String> {
        val cmds = mutableListOf<String>()
        val feedRate = (material.speedMmPerSec * 60).coerceIn(100, 6000)
        cmds.add("G21 ; Millimeters")
        cmds.add("G90 ; Absolute positioning")
        cmds.add("G0 Z5.000")
        cmds.add("F$feedRate")
        repeat(passes) {
            polylines.forEach { poly ->
                if (poly.points.size >= 2) {
                    val p0 = poly.points.first()
                    cmds.add(String.format(Locale.US, "G0 X%.2f Y%.2f", p0.xMm, p0.yMm))
                    cmds.add("G1 Z-0.400")
                    val pathPoints = if (poly.closed) poly.points + poly.points.first() else poly.points
                    pathPoints.drop(1).forEach { pt ->
                        cmds.add(String.format(Locale.US, "G1 X%.2f Y%.2f", pt.xMm, pt.yMm))
                    }
                    cmds.add("G0 Z5.000")
                }
            }
        }
        cmds.add("G0 X0.00 Y0.00")
        cmds.add("M2")
        return cmds
    }

    fun buildJogCommand(protocol: PlotterProtocol, dxMm: Float, dyMm: Float): List<String> {
        return when (protocol) {
            PlotterProtocol.HPGL -> {
                val ux = (dxMm * 40f).roundToInt()
                val uy = (dyMm * 40f).roundToInt()
                listOf("IN;", "PR$ux,$uy;")
            }
            PlotterProtocol.GPGL -> {
                val ux = (dxMm * 20f).roundToInt()
                val uy = (dyMm * 20f).roundToInt()
                listOf("O$ux,$uy")
            }
            PlotterProtocol.GRBL -> listOf(
                "G91",
                String.format(Locale.US, "G0 X%.2f Y%.2f", dxMm, dyMm),
                "G90"
            )
        }
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

    fun buildBoundingFrameCommands(protocol: PlotterProtocol, box: BoundingBoxMm): List<String> {
        val corners = listOf(
            Point2D(box.minX, box.minY),
            Point2D(box.maxX, box.minY),
            Point2D(box.maxX, box.maxY),
            Point2D(box.minX, box.maxY),
            Point2D(box.minX, box.minY)
        )
        return when (protocol) {
            PlotterProtocol.HPGL -> buildList {
                add("IN;")
                corners.forEach { pt ->
                    add("PU${(pt.xMm * 40f).roundToInt()},${(pt.yMm * 40f).roundToInt()};")
                }
            }
            PlotterProtocol.GPGL -> buildList {
                add("!;")
                corners.forEach { pt ->
                    add("M${(pt.xMm * 20f).roundToInt()},${(pt.yMm * 20f).roundToInt()}")
                }
            }
            PlotterProtocol.GRBL -> buildList {
                add("G21")
                add("G90")
                add("G0 Z5.000")
                corners.forEach { pt ->
                    add(String.format(Locale.US, "G0 X%.2f Y%.2f", pt.xMm, pt.yMm))
                }
            }
        }
    }

    fun buildTestSquareCommand(protocol: PlotterProtocol, material: MaterialPreset): List<String> {
        val square = Polyline2D(
            listOf(Point2D(0f, 0f), Point2D(20f, 0f), Point2D(20f, 20f), Point2D(0f, 20f)),
            closed = true
        )
        return when (protocol) {
            PlotterProtocol.HPGL -> buildHpgl(listOf(square), material, passes = 1)
            PlotterProtocol.GPGL -> buildGpgl(listOf(square), material, passes = 1)
            PlotterProtocol.GRBL -> buildGrbl(listOf(square), material, passes = 1)
        }
    }

    fun exportAsSvg(elements: List<DesignElement>, matConfig: CuttingMatConfig): String {
        val sb = StringBuilder()
        sb.append(
            """<svg xmlns="http://www.w3.org/2000/svg" width="${matConfig.widthMm}mm" height="${matConfig.heightMm}mm" viewBox="0 0 ${matConfig.widthMm} ${matConfig.heightMm}">"""
        ).append('\n')
        elements.forEach { el ->
            val strokeHex = String.format(Locale.US, "#%06X", (el.strokeColorHex and 0xFFFFFFL))
            val fillHex = el.fillColorHex?.let {
                String.format(Locale.US, "#%06X", (it and 0xFFFFFFL))
            } ?: "none"
            val combinedD = buildString {
                geometryEngine.buildWorldPolylines(el).forEach { poly ->
                    if (poly.points.size >= 2) {
                        poly.points.forEachIndexed { i, pt ->
                            append(if (i == 0) "M " else "L ")
                            append(String.format(Locale.US, "%.2f %.2f ", pt.xMm, pt.yMm))
                        }
                        if (poly.closed) append("Z ")
                    }
                }
            }.trim()
            if (combinedD.isNotEmpty()) {
                sb.append(
                    """  <path d="$combinedD" fill="$fillHex" fill-rule="evenodd" stroke="$strokeHex" stroke-width="0.4" />"""
                ).append('\n')
            }
        }
        sb.append("</svg>\n")
        return sb.toString()
    }
}
