package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AlignmentAction
import com.example.domain.model.CuttingMatConfig
import com.example.domain.model.DesignElement
import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import com.example.domain.model.VectorBooleanOp
import com.example.domain.model.VectorShapeType
import com.example.domain.usecase.DesignTransformUseCases
import com.example.domain.vector.ImageContourTracer
import com.example.domain.vector.ImageTraceMode
import com.example.domain.vector.PathEditingEngine
import com.example.domain.vector.PathOptimizer
import com.example.domain.vector.VectorGeometryEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appNameMatchesArabicTitle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("كتر برو", appName)
    }

    @Test
    fun weldOverlappingElementsMergesBounds() {
        val engine = VectorGeometryEngine(ApplicationProvider.getApplicationContext())
        val left = DesignElement(
            id = "a",
            name = "مربع 1",
            type = VectorShapeType.RECTANGLE,
            xMm = 10f,
            yMm = 10f,
            widthMm = 30f,
            heightMm = 20f
        )
        val right = DesignElement(
            id = "b",
            name = "مربع 2",
            type = VectorShapeType.RECTANGLE,
            xMm = 25f,
            yMm = 10f,
            widthMm = 30f,
            heightMm = 20f
        )
        val welded = engine.weldElements(listOf(left, right))
        assertNotNull(welded)
        assertEquals(10f, welded!!.xMm, 1.5f)
        assertEquals(45f, welded.widthMm, 1.5f)
        assertEquals(20f, welded.heightMm, 1.5f)
    }

    @Test
    fun pathOptimizerReducesTravelAndAppliesOvercut() {
        // Unsorted zigzag diagonal jumps vs perimeter shortest-neighbor loop
        val s1 = Polyline2D(
            points = listOf(Point2D(200f, 10f), Point2D(220f, 10f), Point2D(220f, 30f), Point2D(200f, 30f)),
            closed = true
        )
        val s2 = Polyline2D(
            points = listOf(Point2D(10f, 200f), Point2D(30f, 200f), Point2D(30f, 220f), Point2D(10f, 220f)),
            closed = true
        )
        val s3 = Polyline2D(
            points = listOf(Point2D(200f, 200f), Point2D(220f, 200f), Point2D(220f, 220f), Point2D(200f, 220f)),
            closed = true
        )
        val s4 = Polyline2D(
            points = listOf(Point2D(10f, 10f), Point2D(30f, 10f), Point2D(30f, 30f), Point2D(10f, 30f)),
            closed = true
        )

        val result = PathOptimizer.optimizeAndCompensate(
            input = listOf(s1, s2, s3, s4),
            optimizeOrder = true,
            overcutMm = 0.5f
        )
        assertTrue(
            "Expected travel savings but raw=${result.rawTravelDistanceMm} opt=${result.optimizedTravelDistanceMm}",
            result.optimizedTravelDistanceMm < result.rawTravelDistanceMm
        )
        assertTrue(result.savedTravelPercent > 10)
        // Closed squares with overcut expand from 4 vertices -> 6 vertices (wrap to p0 + overcut past p0)
        assertEquals(6, result.polylines.first().points.size)
        assertEquals(10.5f, result.polylines.first().points.last().xMm, 0.05f)
    }

    @Test
    fun designTransformsCenterAndGenerateStickerMatrix() {
        val useCases = DesignTransformUseCases()
        val mat = CuttingMatConfig(widthMm = 300f, heightMm = 300f)
        val el = DesignElement(
            id = "s1",
            name = "ملصق",
            type = VectorShapeType.ROUNDED_RECT,
            xMm = 10f,
            yMm = 10f,
            widthMm = 40f,
            heightMm = 40f
        )
        val centered = useCases.alignSelection(listOf(el), setOf("s1"), mat, AlignmentAction.CENTER_MAT).first()
        assertEquals(130f, centered.xMm, 0.5f)
        assertEquals(130f, centered.yMm, 0.5f)

        val copies = useCases.generateArrayCopies(el, rows = 2, cols = 3, gapXMm = 5f, gapYMm = 5f, matConfig = mat)
        assertEquals(5, copies.size)
    }

    @Test
    fun otsuAndSobelImageTracingExtractsClearContours() {
        val w = 120
        val h = 120
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h) { Color.WHITE }
        for (y in 25 until 95) {
            for (x in 25 until 95) {
                px[y * w + x] = Color.BLACK
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)

        val otsu = ImageContourTracer.computeOtsuThreshold(bmp)
        assertTrue("Expected otsu in 40..220 but was $otsu", otsu in 40..220)

        val silhouette = ImageContourTracer.traceBitmap(
            bitmap = bmp,
            threshold = otsu,
            traceMode = ImageTraceMode.SILHOUETTE
        )
        assertEquals(1, silhouette.normalizedPaths.size)

        val edges = ImageContourTracer.traceBitmap(
            bitmap = bmp,
            threshold = otsu,
            traceMode = ImageTraceMode.OUTLINE_EDGES
        )
        assertTrue(
            "Expected outline edges for otsu=$otsu, edges=${edges.normalizedPaths.size}",
            edges.normalizedPaths.isNotEmpty()
        )
    }

    @Test
    fun contourOffsetExpandsStickerDimensionsBeyondOriginal() {
        val engine = VectorGeometryEngine(ApplicationProvider.getApplicationContext())
        val editor = PathEditingEngine(engine)
        val rect = DesignElement(
            id = "sticker",
            name = "ملصق",
            type = VectorShapeType.ROUNDED_RECT,
            xMm = 40f,
            yMm = 40f,
            widthMm = 50f,
            heightMm = 30f
        )
        val offsetEl = editor.createContourOffsetElement(rect, offsetMm = 5f)
        assertNotNull(offsetEl)
        assertTrue(offsetEl!!.widthMm > rect.widthMm + 6f)
        assertTrue(offsetEl.heightMm > rect.heightMm + 6f)
        assertTrue(offsetEl.normalizedPaths.isNotEmpty())
    }

    @Test
    fun booleanDifferenceAndIntersectComputeAccurateBounds() {
        val engine = VectorGeometryEngine(ApplicationProvider.getApplicationContext())
        val base = DesignElement(
            id = "base",
            name = "أساس",
            type = VectorShapeType.RECTANGLE,
            xMm = 10f,
            yMm = 10f,
            widthMm = 40f,
            heightMm = 40f
        )
        val cutter = DesignElement(
            id = "cutter",
            name = "قاطع",
            type = VectorShapeType.RECTANGLE,
            xMm = 30f,
            yMm = 10f,
            widthMm = 40f,
            heightMm = 40f
        )

        val punched = engine.booleanOpElements(listOf(base, cutter), VectorBooleanOp.DIFFERENCE)
        assertNotNull(punched)
        assertEquals(10f, punched!!.xMm, 1.5f)
        assertEquals(20f, punched.widthMm, 1.5f)
        assertEquals(40f, punched.heightMm, 1.5f)

        val intersected = engine.booleanOpElements(listOf(base, cutter), VectorBooleanOp.INTERSECT)
        assertNotNull(intersected)
        assertEquals(30f, intersected!!.xMm, 1.5f)
        assertEquals(20f, intersected.widthMm, 1.5f)
        assertEquals(40f, intersected.heightMm, 1.5f)
    }

    @Test
    fun mainNavigationMatSizeWeldGroupAndNodeEditFlowRendersCorrectly() {
        composeRule.onNodeWithTag("cutting_mat_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("mat_size_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("freehand_draw_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("multi_select_chip").assertIsDisplayed()
        composeRule.onNodeWithTag("signmaster_side_toolbox").assertIsDisplayed()
        composeRule.onNodeWithTag("signmaster_color_strip").assertIsDisplayed()
        composeRule.onNodeWithTag("wireframe_toggle_chip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("object_manager_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("add_text_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trace_image_button").performScrollTo().assertIsDisplayed()

        // SignMaster Wireframe Toggle & Object Manager
        composeRule.onNodeWithTag("wireframe_toggle_chip").performScrollTo().performClick()
        composeRule.onNodeWithTag("object_manager_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("close_object_manager_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("close_object_manager_button").performClick()
        composeRule.waitForIdle()

        // Open Mat Dimensions dialog and confirm
        composeRule.onNodeWithTag("mat_size_chip").performClick()
        composeRule.onNodeWithTag("mat_width_input").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_mat_size_button").performClick()

        // Curve & Design Lab bottom sheet: simplify, smooth, close, contour offset, flips
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasTestTag("enter_node_edit_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("curve_tools_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apply_simplify_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_smooth_curves_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_close_curves_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("create_contour_offset_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_flip_vertical_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("close_path_tools_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Color Palette sheet
        composeRule.onNodeWithTag("color_palette_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("fill_none_button").assertIsDisplayed()
        composeRule.onNodeWithTag("fill_swatch_1").performClick()
        composeRule.onNodeWithTag("stroke_swatch_1").performClick()
        composeRule.onNodeWithTag("close_color_palette_button").performClick()

        composeRule.onNodeWithTag("array_clone_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("align_arrange_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("weld_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("group_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ungroup_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("lock_element_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("enter_node_edit_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("insert_node_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("delete_node_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("node_simplify_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("node_smooth_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("exit_node_edit_button").performScrollTo().performClick()

        composeRule.onNodeWithTag("nav_machine").performClick()
        composeRule.onNodeWithTag("transport_chip_usb_otg_cdc").assertIsDisplayed()
        composeRule.onNodeWithTag("transport_chip_wifi_tcp").assertIsDisplayed()
        composeRule.onNodeWithTag("transport_chip_bluetooth_spp").assertIsDisplayed()
        composeRule.onNodeWithTag("otg_scan_button").assertIsDisplayed()

        // Switch to Wi-Fi transport while still at top of scroll container
        composeRule.onNodeWithTag("transport_chip_wifi_tcp").performClick()
        composeRule.onNodeWithTag("wifi_host_input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("connect_wifi_button").performScrollTo().assertIsDisplayed()

        // Switch to Bluetooth transport and verify scan control
        composeRule.onNodeWithTag("transport_chip_bluetooth_spp").performClick()
        composeRule.onNodeWithTag("bluetooth_scan_button").performScrollTo().assertIsDisplayed()

        // Scroll down to verify pro cut, Cut-by-Color spooler & frame test controls
        composeRule.onNodeWithTag("test_frame_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("optimize_cut_switch").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("overcut_slider").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("cut_by_color_all_chip").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("start_otg_cut_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.onNodeWithTag("add_custom_material_button").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("nav_about").performClick()
        composeRule.onNodeWithTag("developer_name_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("developer_name_text").assertTextContains("جميل السيد احمد")
    }
}
