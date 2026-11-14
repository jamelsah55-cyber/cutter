package com.cutterpro.desktop

import com.cutterpro.desktop.model.CuttingMatConfig
import com.cutterpro.desktop.model.DefaultMaterialPresets
import com.cutterpro.desktop.model.DesignElement
import com.cutterpro.desktop.model.PlotterProtocol
import com.cutterpro.desktop.model.VectorBooleanOp
import com.cutterpro.desktop.model.VectorShapeType
import com.cutterpro.desktop.vector.DesktopPlotterCommandGenerator
import com.cutterpro.desktop.vector.DesktopSvgPathParser
import com.cutterpro.desktop.vector.DesktopVectorEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopEngineTest {

    private val engine = DesktopVectorEngine()
    private val generator = DesktopPlotterCommandGenerator(engine)

    @Test
    fun booleanOperationsWorkWithAwtArea() {
        val a = DesignElement("a", "A", VectorShapeType.RECTANGLE, 10f, 10f, 40f, 40f)
        val b = DesignElement("b", "B", VectorShapeType.RECTANGLE, 30f, 10f, 40f, 40f)

        val union = engine.booleanOpElements(listOf(a, b), VectorBooleanOp.UNION)
        assertNotNull(union)
        assertEquals(60f, union.widthMm, 1.0f)

        val diff = engine.booleanOpElements(listOf(a, b), VectorBooleanOp.DIFFERENCE)
        assertNotNull(diff)
        assertEquals(20f, diff.widthMm, 1.0f)

        val inter = engine.booleanOpElements(listOf(a, b), VectorBooleanOp.INTERSECT)
        assertNotNull(inter)
        assertEquals(30f, inter.xMm, 1.0f)
        assertEquals(20f, inter.widthMm, 1.0f)
    }

    @Test
    fun cutByColorAndHpglExportGenerateValidPlotterPlan() {
        val r = DesignElement("r", "Red", VectorShapeType.RECTANGLE, 10f, 10f, 20f, 20f, fillColorHex = 0xFFFF0000L)
        val b = DesignElement("b", "Blue", VectorShapeType.RECTANGLE, 50f, 10f, 20f, 20f, fillColorHex = 0xFF0000FFL)
        val mat = CuttingMatConfig(weedingBoxEnabled = false, overcutMm = 0f, cutColorFilterHex = 0xFFFF0000L)
        val plan = generator.compileCutPlan(listOf(r, b), mat, DefaultMaterialPresets.all.first(), PlotterProtocol.HPGL)
        assertEquals(80f, plan.cutDistanceMm, 0.5f)
        assertTrue(plan.commands.first() == "IN;")

        val parsed = DesktopSvgPathParser.parseFileContent("t.svg", """<svg><rect x="0" y="0" width="50" height="50"/></svg>""")
        assertNotNull(parsed)
        assertEquals(1, parsed.normalizedPaths.size)
    }
}
