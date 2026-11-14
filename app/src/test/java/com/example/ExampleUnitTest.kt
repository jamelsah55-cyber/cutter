package com.example

import com.example.domain.model.AlignmentAction
import com.example.domain.model.CuttingMatConfig
import com.example.domain.model.DesignElement
import com.example.domain.model.MaterialPreset
import com.example.domain.model.PlotterProtocol
import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import com.example.domain.model.VectorShapeType
import com.example.domain.usecase.DesignTransformUseCases
import com.example.domain.vector.ImageContourTracer
import com.example.domain.vector.PathEditingEngine
import com.example.domain.vector.PlotterCommandGenerator
import com.example.domain.vector.SvgPathParser
import com.example.domain.vector.VectorGeometryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val engine = VectorGeometryEngine(null)
    private val generator = PlotterCommandGenerator(engine)

    @Test
    fun hpglGenerationProducesValidScaleAndWeedingBox() {
        val rect = DesignElement(
            id = "r1",
            name = "مربع",
            type = VectorShapeType.RECTANGLE,
            xMm = 10f,
            yMm = 10f,
            widthMm = 20f,
            heightMm = 20f
        )
        val mat = CuttingMatConfig(
            widthMm = 300f,
            heightMm = 300f,
            weedingBoxEnabled = true,
            weedingMarginMm = 5f,
            overcutMm = 0f
        )
        val material = MaterialPreset(nameAr = "فينيل", categoryAr = "لافتات", forceGrams = 95, speedMmPerSec = 250)

        val plan = generator.compileCutPlan(listOf(rect), mat, material, PlotterProtocol.HPGL)

        assertTrue(plan.commands.first() == "IN;")
        assertTrue(plan.commands.any { it.startsWith("PU400,400;") })
        assertTrue(plan.commands.any { it.startsWith("PD1200,400,1200,1200,400,1200,400,400;") })
        assertEquals(200f, plan.cutDistanceMm, 0.5f)

        val overcutPlan = generator.compileCutPlan(
            listOf(rect),
            mat.copy(overcutMm = 0.4f),
            material,
            PlotterProtocol.HPGL
        )
        assertTrue(overcutPlan.commands.any { it.contains("400,400,416,400;") })
    }

    @Test
    fun svgPathParserImportsPathAndHpgl() {
        val svg = """<svg viewBox="0 0 100 100"><path d="M 10 10 L 90 10 L 90 90 Z" /></svg>"""
        val parsedSvg = SvgPathParser.parseFileContent("logo.svg", svg)
        assertNotNull(parsedSvg)
        assertEquals(1, parsedSvg!!.normalizedPaths.size)
        assertTrue(parsedSvg.normalizedPaths.first().closed)

        val hpgl = "IN;PU400,400;PD1200,400,1200,1200,400,1200,400,400;PU0,0;"
        val parsedPlt = SvgPathParser.parseFileContent("cut.plt", hpgl)
        assertNotNull(parsedPlt)
        assertEquals(1, parsedPlt!!.normalizedPaths.size)
    }

    @Test
    fun imageContourTracerExtractsOuterAndInnerBoundaries() {
        val w = 24
        val h = 24
        val mask = BooleanArray(w * h)
        for (y in 4 until 20) {
            for (x in 4 until 20) {
                val isHole = x in 9..14 && y in 9..14
                mask[y * w + x] = !isHole
            }
        }
        val traced = ImageContourTracer.traceMask(mask, w, h, simplifyEpsilonPx = 0.8f)
        assertEquals(2, traced.normalizedPaths.size)
        assertTrue(traced.normalizedPaths.all { it.closed && it.points.size >= 4 })
    }

    @Test
    fun bakeToEditablePathsPreservesNodeGeometry() {
        val rect = DesignElement(
            id = "rect_bake",
            name = "مربع",
            type = VectorShapeType.RECTANGLE,
            xMm = 20f,
            yMm = 30f,
            widthMm = 50f,
            heightMm = 40f
        )
        val baked = engine.bakeToEditablePaths(rect)
        assertEquals(VectorShapeType.CUSTOM_PATH, baked.type)
        assertEquals(1, baked.normalizedPaths.size)
        assertEquals(4, baked.normalizedPaths.first().points.size)
    }

    @Test
    fun breakApartMultiContourElementProducesIndependentElements() {
        val star = DesignElement(
            id = "star_1",
            name = "نجمة إسلامية",
            type = VectorShapeType.ISLAMIC_STAR,
            xMm = 10f,
            yMm = 20f,
            widthMm = 80f,
            heightMm = 80f,
            groupId = "g1"
        )
        val parts = engine.breakApartElement(star)
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.type == VectorShapeType.CUSTOM_PATH && it.groupId == null })
        assertTrue(parts.all { it.normalizedPaths.size == 1 })
    }

    @Test
    fun imageTracerFiltersTinySpecksWhenMinPerimeterIsIncreased() {
        val w = 32
        val h = 32
        val mask = BooleanArray(w * h)
        // Large 16x16 square
        for (y in 4 until 20) {
            for (x in 4 until 20) {
                mask[y * w + x] = true
            }
        }
        // Tiny 2x2 speck
        mask[26 * w + 26] = true
        mask[26 * w + 27] = true
        mask[27 * w + 26] = true
        mask[27 * w + 27] = true

        val traced = ImageContourTracer.traceMask(mask, w, h, simplifyEpsilonPx = 0.6f, minPerimeterNodes = 6)
        assertEquals(1, traced.normalizedPaths.size)
    }

    @Test
    fun svgExportPreservesFillAndCutLineColors() {
        val el = DesignElement(
            id = "colored",
            name = "ملصق ملون",
            type = VectorShapeType.RECTANGLE,
            xMm = 10f,
            yMm = 10f,
            widthMm = 30f,
            heightMm = 20f,
            strokeColorHex = 0xFFFF4D6DL,
            fillColorHex = 0xFF3A86FFL
        )
        val svg = generator.exportAsSvg(listOf(el), CuttingMatConfig())
        assertTrue(svg.contains("""fill="#3A86FF""""))
        assertTrue(svg.contains("""stroke="#FF4D6D""""))
    }

    @Test
    fun pathEditingEngineSimplifiesSmoothsAndClosesOpenCurves() {
        val pathEditor = PathEditingEngine(engine)

        // 1. Simplify dense circle (48 points -> far fewer at 1.2mm tolerance)
        val circle = DesignElement(
            id = "c1",
            name = "دائرة",
            type = VectorShapeType.CIRCLE,
            xMm = 10f,
            yMm = 10f,
            widthMm = 60f,
            heightMm = 60f
        )
        val simplified = pathEditor.simplifyElement(circle, toleranceMm = 1.2f)
        assertTrue(
            "Expected node reduction: before=${simplified.nodesBefore} after=${simplified.nodesAfter}",
            simplified.nodesAfter < simplified.nodesBefore
        )

        // 2. Smooth 4-corner square -> 8 Chaikin vertices
        val rect = DesignElement(
            id = "r_smooth",
            name = "مربع",
            type = VectorShapeType.RECTANGLE,
            xMm = 10f,
            yMm = 10f,
            widthMm = 40f,
            heightMm = 40f
        )
        val (smoothed, smoothedCount) = pathEditor.smoothElement(rect)
        assertEquals(8, smoothedCount)
        assertTrue(smoothed.normalizedPaths.first().closed)

        // 3. Close open curve with nearly matching trailing vertex
        val openPoly = Polyline2D(
            points = listOf(
                Point2D(0f, 0f),
                Point2D(1f, 0f),
                Point2D(1f, 1f),
                Point2D(0f, 1f),
                Point2D(0.01f, 0.01f)
            ),
            closed = false
        )
        val openEl = DesignElement(
            id = "open1",
            name = "منحنى مفتوح",
            type = VectorShapeType.CUSTOM_PATH,
            xMm = 0f,
            yMm = 0f,
            widthMm = 50f,
            heightMm = 50f,
            normalizedPaths = listOf(openPoly)
        )
        val (closedEl, newlyClosed) = pathEditor.closeOpenCurves(openEl)
        assertEquals(1, newlyClosed)
        assertTrue(closedEl.normalizedPaths.first().closed)
        assertEquals(4, closedEl.normalizedPaths.first().points.size)
    }

    @Test
    fun freehandAutoClosesLoopAndHorizontalDistributionSpacesEvenly() {
        val pathEditor = PathEditingEngine(engine)
        val loopStroke = listOf(
            Point2D(20f, 20f),
            Point2D(60f, 22f),
            Point2D(58f, 65f),
            Point2D(21f, 62f),
            Point2D(22f, 23f)
        )
        val freehandEl = pathEditor.buildFreehandElement(loopStroke)
        assertNotNull(freehandEl)
        assertTrue(freehandEl!!.normalizedPaths.first().closed)

        val e1 = DesignElement("d1", "1", VectorShapeType.RECTANGLE, xMm = 10f, yMm = 10f, widthMm = 20f, heightMm = 20f)
        val e2 = DesignElement("d2", "2", VectorShapeType.RECTANGLE, xMm = 25f, yMm = 10f, widthMm = 20f, heightMm = 20f)
        val e3 = DesignElement("d3", "3", VectorShapeType.RECTANGLE, xMm = 90f, yMm = 10f, widthMm = 20f, heightMm = 20f)
        val distributed = DesignTransformUseCases().alignSelection(
            listOf(e1, e2, e3),
            setOf("d1", "d2", "d3"),
            CuttingMatConfig(),
            AlignmentAction.DISTRIBUTE_HORIZONTAL
        )
        assertEquals(50f, distributed[1].xMm, 0.2f)
    }

    @Test
    fun cutByColorAndVisibilityFilterExcludedHiddenAndUnmatchedElements() {
        val red = DesignElement(
            id = "red",
            name = "أحمر",
            type = VectorShapeType.RECTANGLE,
            xMm = 10f,
            yMm = 10f,
            widthMm = 20f,
            heightMm = 20f,
            fillColorHex = 0xFFFF0000L,
            isVisible = true
        )
        val hiddenGreen = DesignElement(
            id = "green",
            name = "أخضر مخفي",
            type = VectorShapeType.RECTANGLE,
            xMm = 50f,
            yMm = 10f,
            widthMm = 20f,
            heightMm = 20f,
            fillColorHex = 0xFF00FF00L,
            isVisible = false
        )
        val blue = DesignElement(
            id = "blue",
            name = "أزرق",
            type = VectorShapeType.RECTANGLE,
            xMm = 90f,
            yMm = 10f,
            widthMm = 20f,
            heightMm = 20f,
            fillColorHex = 0xFF0000FFL,
            isVisible = true
        )
        val material = MaterialPreset(nameAr = "فينيل", categoryAr = "لافتات", forceGrams = 95, speedMmPerSec = 250)
        val mat = CuttingMatConfig(weedingBoxEnabled = false, overcutMm = 0f)

        val allVisiblePlan = generator.compileCutPlan(listOf(red, hiddenGreen, blue), mat, material, PlotterProtocol.HPGL)
        assertEquals(160f, allVisiblePlan.cutDistanceMm, 0.5f)

        val redOnlyPlan = generator.compileCutPlan(
            listOf(red, hiddenGreen, blue),
            mat.copy(cutColorFilterHex = 0xFFFF0000L),
            material,
            PlotterProtocol.HPGL
        )
        assertEquals(80f, redOnlyPlan.cutDistanceMm, 0.5f)
    }
}
