package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BoundingBoxMm
import com.example.domain.model.CuttingMatConfig
import com.example.domain.model.DesignElement
import com.example.domain.model.NodeRef
import com.example.domain.model.Point2D
import com.example.domain.vector.VectorGeometryEngine
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private enum class ResizeHandle {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MID_LEFT, MID_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

private sealed class DragTarget {
    data object None : DragTarget()
    data object PanMat : DragTarget()
    data class MoveElement(val id: String) : DragTarget()
    data class Resize(val singleId: String?, val handle: ResizeHandle) : DragTarget()
    data class Rotate(val id: String) : DragTarget()
    data class MoveNode(val node: NodeRef) : DragTarget()
}

@Composable
fun CuttingMatCanvas(
    elements: List<DesignElement>,
    selectedElementId: String?,
    matConfig: CuttingMatConfig,
    geometryEngine: VectorGeometryEngine,
    simulatedHeadMm: Point2D?,
    zoomScale: Float,
    panOffset: Offset,
    onZoomPanChange: (Float, Offset) -> Unit,
    onSelectElement: (String?) -> Unit,
    onDragElementDelta: (String, Float, Float, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selectedElementIds: Set<String> = emptySet(),
    isNodeEditMode: Boolean = false,
    selectedNode: NodeRef? = null,
    onSelectNode: (NodeRef?) -> Unit = {},
    onDragNodeDelta: (Int, Int, Float, Float, Boolean) -> Unit = { _, _, _, _, _ -> },
    onResizeElementBounds: (String, Float, Float, Float, Float, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onResizeSelectionBounds: (Float, Float, Float, Float, Boolean) -> Unit = { _, _, _, _, _ -> },
    onRotateElement: (String, Float, Boolean) -> Unit = { _, _, _ -> },
    isFreehandDrawMode: Boolean = false,
    onFinishFreehandStroke: (List<Point2D>) -> Unit = {},
    isWireframeMode: Boolean = false,
    interactive: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val handleHitRadiusPx = with(density) { 28.dp.toPx() }
    val nodeHitRadiusPx = with(density) { 22.dp.toPx() }
    val rotateHandleOffsetPx = with(density) { 28.dp.toPx() }

    val effectiveSelectedIds = remember(selectedElementId, selectedElementIds) {
        if (selectedElementIds.isNotEmpty()) selectedElementIds
        else selectedElementId?.let { setOf(it) } ?: emptySet()
    }

    val rulerStyle = remember(colorScheme.onSurfaceVariant) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
    val dimBadgeStyle = remember(colorScheme.onPrimary) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            color = colorScheme.onPrimary
        )
    }
    val groupBadgeStyle = remember(colorScheme.onSecondary) {
        TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = colorScheme.onSecondary
        )
    }

    val boundingBox = remember(elements, matConfig.weedingBoxEnabled) {
        if (matConfig.weedingBoxEnabled) geometryEngine.computeElementsBoundingBox(elements.filter { it.isVisible }) else null
    }

    val compiledPaths = remember(elements) {
        elements.associate { el ->
            el.id to geometryEngine.buildWorldPolylines(el)
        }
    }

    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var activeDragTarget by remember { mutableStateOf<DragTarget>(DragTarget.None) }
    var freehandScreenPts by remember { mutableStateOf<List<Offset>>(emptyList()) }

    // Keep latest state in rememberUpdatedState so unified pointerInput never restarts mid-drag
    val latestElements by rememberUpdatedState(elements)
    val latestSelectedId by rememberUpdatedState(selectedElementId)
    val latestSelectedIds by rememberUpdatedState(effectiveSelectedIds)
    val latestMatConfig by rememberUpdatedState(matConfig)
    val latestZoom by rememberUpdatedState(zoomScale)
    val latestPan by rememberUpdatedState(panOffset)
    val latestNodeMode by rememberUpdatedState(isNodeEditMode)
    val latestFreehandMode by rememberUpdatedState(isFreehandDrawMode)
    val latestCompiledPaths by rememberUpdatedState(compiledPaths)

    fun computeLayoutTransform(
        size: Size,
        mat: CuttingMatConfig,
        zoom: Float,
        pan: Offset
    ): Triple<Float, Float, Float> {
        val padding = 28f
        val availW = (size.width - padding * 2f).coerceAtLeast(10f)
        val availH = (size.height - padding * 2f).coerceAtLeast(10f)
        val baseScale = min(availW / mat.widthMm, availH / mat.heightMm)
        val pxPerMm = baseScale * zoom
        val matPxW = mat.widthMm * pxPerMm
        val matPxH = mat.heightMm * pxPerMm
        val originX = (size.width - matPxW) / 2f + pan.x
        val originY = (size.height - matPxH) / 2f + pan.y
        return Triple(pxPerMm, originX, originY)
    }

    fun computeSelectionBoxMm(list: List<DesignElement>): BoundingBoxMm? {
        if (list.isEmpty()) return null
        return BoundingBoxMm(
            minX = list.minOf { it.xMm },
            minY = list.minOf { it.yMm },
            maxX = list.maxOf { it.xMm + it.widthMm },
            maxY = list.maxOf { it.yMm + it.heightMm }
        )
    }

    fun handlePositionsScreen(
        box: BoundingBoxMm,
        pxPerMm: Float,
        originX: Float,
        originY: Float
    ): Map<ResizeHandle, Offset> {
        val left = originX + box.minX * pxPerMm
        val top = originY + box.minY * pxPerMm
        val right = originX + box.maxX * pxPerMm
        val bottom = originY + box.maxY * pxPerMm
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f
        return mapOf(
            ResizeHandle.TOP_LEFT to Offset(left, top),
            ResizeHandle.TOP_CENTER to Offset(midX, top),
            ResizeHandle.TOP_RIGHT to Offset(right, top),
            ResizeHandle.MID_LEFT to Offset(left, midY),
            ResizeHandle.MID_RIGHT to Offset(right, midY),
            ResizeHandle.BOTTOM_LEFT to Offset(left, bottom),
            ResizeHandle.BOTTOM_CENTER to Offset(midX, bottom),
            ResizeHandle.BOTTOM_RIGHT to Offset(right, bottom)
        )
    }

    fun findNearestNode(screenPos: Offset, pxPerMm: Float, originX: Float, originY: Float): NodeRef? {
        val selId = latestSelectedId ?: return null
        val polys = latestCompiledPaths[selId] ?: return null
        var bestRef: NodeRef? = null
        var bestDist = nodeHitRadiusPx
        polys.forEachIndexed { pIdx, poly ->
            poly.points.forEachIndexed { ptIdx, pt ->
                val sx = originX + pt.xMm * pxPerMm
                val sy = originY + pt.yMm * pxPerMm
                val d = hypot(screenPos.x - sx, screenPos.y - sy)
                if (d < bestDist) {
                    bestDist = d
                    bestRef = NodeRef(pIdx, ptIdx)
                }
            }
        }
        return bestRef
    }

    fun hitTestElement(mm: Point2D): DesignElement? {
        return latestElements.lastOrNull { el ->
            if (!el.isVisible) return@lastOrNull false
            val pad = 6f
            mm.xMm >= el.xMm - pad &&
                mm.xMm <= el.xMm + el.widthMm + pad &&
                mm.yMm >= el.yMm - pad &&
                mm.yMm <= el.yMm + el.heightMm + pad
        }
    }

    fun resolveDragTarget(startOffset: Offset): DragTarget {
        if (canvasSize.width <= 0f) return DragTarget.None
        val (pxPerMm, originX, originY) = computeLayoutTransform(
            canvasSize, latestMatConfig, latestZoom, latestPan
        )
        val selElements = latestElements.filter { it.id in latestSelectedIds }

        if (latestNodeMode && selElements.size == 1) {
            val hitNode = findNearestNode(startOffset, pxPerMm, originX, originY)
            return if (hitNode != null) {
                onSelectNode(hitNode)
                DragTarget.MoveNode(hitNode)
            } else {
                DragTarget.PanMat
            }
        }

        val unlockedSelection = selElements.filter { !it.isLocked }
        val selBox = computeSelectionBoxMm(unlockedSelection)
        if (selBox != null) {
            // 1. Single-element rotation handle above top-center
            if (unlockedSelection.size == 1) {
                val single = unlockedSelection.first()
                val rotX = originX + (single.xMm + single.widthMm / 2f) * pxPerMm
                val rotY = originY + single.yMm * pxPerMm - rotateHandleOffsetPx
                if (hypot(startOffset.x - rotX, startOffset.y - rotY) <= handleHitRadiusPx) {
                    return DragTarget.Rotate(single.id)
                }
            }

            // 2. Check all 8 bounding resize handles first (high priority over body hit!)
            val handles = handlePositionsScreen(selBox, pxPerMm, originX, originY)
            val hitHandle = handles.entries.minByOrNull { (_, pos) ->
                hypot(startOffset.x - pos.x, startOffset.y - pos.y)
            }?.takeIf { (_, pos) ->
                hypot(startOffset.x - pos.x, startOffset.y - pos.y) <= handleHitRadiusPx
            }
            if (hitHandle != null) {
                val singleId = unlockedSelection.singleOrNull()?.id
                return DragTarget.Resize(singleId, hitHandle.key)
            }
        }

        // 3. Check element body hit
        val mm = Point2D((startOffset.x - originX) / pxPerMm, (startOffset.y - originY) / pxPerMm)
        val hitEl = hitTestElement(mm)
        return if (hitEl != null) {
            if (hitEl.id !in latestSelectedIds) {
                onSelectElement(hitEl.id)
            }
            DragTarget.MoveElement(hitEl.id)
        } else {
            DragTarget.PanMat
        }
    }

    fun applyDragDelta(target: DragTarget, pointerPos: Offset, dragDelta: Offset) {
        if (canvasSize.width <= 0f) return
        val (pxPerMm, originX, originY) = computeLayoutTransform(
            canvasSize, latestMatConfig, latestZoom, latestPan
        )
        val dxMm = dragDelta.x / pxPerMm
        val dyMm = dragDelta.y / pxPerMm

        when (target) {
            is DragTarget.MoveElement -> {
                onDragElementDelta(target.id, dxMm, dyMm, false)
            }
            is DragTarget.Resize -> {
                val targets = if (target.singleId != null) {
                    latestElements.filter { it.id == target.singleId }
                } else {
                    latestElements.filter { it.id in latestSelectedIds && !it.isLocked }
                }
                val box = computeSelectionBoxMm(targets) ?: return
                val minSize = if (targets.size > 1) 14f else 10f
                var nx = box.minX
                var ny = box.minY
                var nw = box.width
                var nh = box.height

                when (target.handle) {
                    ResizeHandle.TOP_LEFT -> {
                        val nextW = (nw - dxMm).coerceAtLeast(minSize)
                        val nextH = (nh - dyMm).coerceAtLeast(minSize)
                        nx += (nw - nextW)
                        ny += (nh - nextH)
                        nw = nextW
                        nh = nextH
                    }
                    ResizeHandle.TOP_CENTER -> {
                        val nextH = (nh - dyMm).coerceAtLeast(minSize)
                        ny += (nh - nextH)
                        nh = nextH
                    }
                    ResizeHandle.TOP_RIGHT -> {
                        nw = (nw + dxMm).coerceAtLeast(minSize)
                        val nextH = (nh - dyMm).coerceAtLeast(minSize)
                        ny += (nh - nextH)
                        nh = nextH
                    }
                    ResizeHandle.MID_LEFT -> {
                        val nextW = (nw - dxMm).coerceAtLeast(minSize)
                        nx += (nw - nextW)
                        nw = nextW
                    }
                    ResizeHandle.MID_RIGHT -> {
                        nw = (nw + dxMm).coerceAtLeast(minSize)
                    }
                    ResizeHandle.BOTTOM_LEFT -> {
                        val nextW = (nw - dxMm).coerceAtLeast(minSize)
                        nx += (nw - nextW)
                        nw = nextW
                        nh = (nh + dyMm).coerceAtLeast(minSize)
                    }
                    ResizeHandle.BOTTOM_CENTER -> {
                        nh = (nh + dyMm).coerceAtLeast(minSize)
                    }
                    ResizeHandle.BOTTOM_RIGHT -> {
                        nw = (nw + dxMm).coerceAtLeast(minSize)
                        nh = (nh + dyMm).coerceAtLeast(minSize)
                    }
                }

                if (target.singleId != null) {
                    onResizeElementBounds(target.singleId, nx, ny, nw, nh, false)
                } else {
                    onResizeSelectionBounds(nx, ny, nw, nh, false)
                }
            }
            is DragTarget.Rotate -> {
                val el = latestElements.firstOrNull { it.id == target.id } ?: return
                val cxScreen = originX + (el.xMm + el.widthMm / 2f) * pxPerMm
                val cyScreen = originY + (el.yMm + el.heightMm / 2f) * pxPerMm
                val angleDeg = (atan2(pointerPos.y - cyScreen, pointerPos.x - cxScreen) * 180f / PI.toFloat()) + 90f
                onRotateElement(el.id, angleDeg, false)
            }
            is DragTarget.MoveNode -> {
                onDragNodeDelta(target.node.polylineIndex, target.node.pointIndex, dxMm, dyMm, false)
            }
            DragTarget.PanMat -> {
                onZoomPanChange(latestZoom, latestPan + dragDelta)
            }
            DragTarget.None -> {}
        }
    }

    fun commitDragEnd(target: DragTarget) {
        when (target) {
            is DragTarget.MoveElement -> onDragElementDelta(target.id, 0f, 0f, true)
            is DragTarget.Resize -> {
                val targets = if (target.singleId != null) {
                    latestElements.filter { it.id == target.singleId }
                } else {
                    latestElements.filter { it.id in latestSelectedIds && !it.isLocked }
                }
                val box = computeSelectionBoxMm(targets) ?: return
                if (target.singleId != null) {
                    onResizeElementBounds(target.singleId, box.minX, box.minY, box.width, box.height, true)
                } else {
                    onResizeSelectionBounds(box.minX, box.minY, box.width, box.height, true)
                }
            }
            is DragTarget.Rotate -> {
                val el = latestElements.firstOrNull { it.id == target.id }
                if (el != null) {
                    onRotateElement(el.id, el.rotationDeg, true)
                }
            }
            is DragTarget.MoveNode -> {
                onDragNodeDelta(target.node.polylineIndex, target.node.pointIndex, 0f, 0f, true)
            }
            else -> {}
        }
    }

    // Unified single-loop gesture handler: zero conflict between resize handles, body drags, taps, and pinch-zoom!
    val interactionModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                val startOffset = firstDown.position
                var dragTarget: DragTarget = DragTarget.None
                var pastTouchSlop = false
                var isMultiTouch = false
                var totalDelta = Offset.Zero
                val touchSlop = viewConfiguration.touchSlop
                val drawingFreehand = latestFreehandMode
                if (drawingFreehand) {
                    freehandScreenPts = listOf(startOffset)
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val activeChanges = event.changes.filter { it.pressed }

                    if (activeChanges.size >= 2) {
                        isMultiTouch = true
                        freehandScreenPts = emptyList()
                        dragTarget = DragTarget.None
                        activeDragTarget = DragTarget.None
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            val nextZoom = (latestZoom * zoomChange).coerceIn(0.6f, 4.5f)
                            onZoomPanChange(nextZoom, latestPan + panChange)
                            event.changes.forEach { it.consume() }
                        }
                        continue
                    }

                    val change = event.changes.firstOrNull { it.id == firstDown.id }
                        ?: event.changes.firstOrNull()
                    if (change == null || !change.pressed) {
                        if (drawingFreehand && !isMultiTouch) {
                            val captured = freehandScreenPts
                            freehandScreenPts = emptyList()
                            if (canvasSize.width > 0f && captured.size >= 2) {
                                val (pxPerMm, originX, originY) = computeLayoutTransform(
                                    canvasSize, latestMatConfig, latestZoom, latestPan
                                )
                                val mmPts = captured.map { off ->
                                    Point2D(
                                        ((off.x - originX) / pxPerMm).coerceIn(0f, latestMatConfig.widthMm),
                                        ((off.y - originY) / pxPerMm).coerceIn(0f, latestMatConfig.heightMm)
                                    )
                                }
                                onFinishFreehandStroke(mmPts)
                            }
                            break
                        }
                        if (!isMultiTouch && !pastTouchSlop) {
                            if (canvasSize.width > 0f) {
                                val (pxPerMm, originX, originY) = computeLayoutTransform(
                                    canvasSize, latestMatConfig, latestZoom, latestPan
                                )
                                if (latestNodeMode) {
                                    val hitNode = findNearestNode(startOffset, pxPerMm, originX, originY)
                                    if (hitNode != null) {
                                        onSelectNode(hitNode)
                                        break
                                    }
                                }
                                val mm = Point2D(
                                    (startOffset.x - originX) / pxPerMm,
                                    (startOffset.y - originY) / pxPerMm
                                )
                                val hit = hitTestElement(mm)
                                onSelectElement(hit?.id)
                            }
                        } else if (!isMultiTouch && pastTouchSlop) {
                            commitDragEnd(dragTarget)
                        }
                        activeDragTarget = DragTarget.None
                        break
                    }

                    if (isMultiTouch) {
                        continue
                    }

                    if (drawingFreehand) {
                        val lastPt = freehandScreenPts.lastOrNull()
                        if (lastPt == null || hypot(change.position.x - lastPt.x, change.position.y - lastPt.y) >= 3f) {
                            freehandScreenPts = freehandScreenPts + change.position
                        }
                        change.consume()
                        continue
                    }

                    val stepDelta = change.position - change.previousPosition
                    totalDelta += stepDelta

                    if (!pastTouchSlop) {
                        if (hypot(totalDelta.x, totalDelta.y) >= touchSlop) {
                            pastTouchSlop = true
                            dragTarget = resolveDragTarget(startOffset)
                            activeDragTarget = dragTarget
                            applyDragDelta(dragTarget, change.position, totalDelta)
                            change.consume()
                        }
                    } else if (stepDelta != Offset.Zero) {
                        applyDragDelta(dragTarget, change.position, stepDelta)
                        change.consume()
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .background(colorScheme.surfaceContainerLowest)
            .then(interactionModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            canvasSize = size
            val (pxPerMm, originX, originY) = computeLayoutTransform(size, matConfig, zoomScale, panOffset)
            val matW = matConfig.widthMm * pxPerMm
            val matH = matConfig.heightMm * pxPerMm

            // Cutting mat surface
            drawRect(
                color = colorScheme.surfaceContainerLow,
                topLeft = Offset(originX, originY),
                size = Size(matW, matH)
            )

            // Minor & major millimeter grid
            val step = matConfig.gridStepMm.coerceAtLeast(5f)
            var xMm = 0f
            while (xMm <= matConfig.widthMm + 0.01f) {
                val isMajor = (xMm.toInt() % 50 == 0)
                val px = originX + xMm * pxPerMm
                drawLine(
                    color = if (isMajor) colorScheme.outline.copy(alpha = 0.55f)
                    else colorScheme.outlineVariant.copy(alpha = 0.35f),
                    start = Offset(px, originY),
                    end = Offset(px, originY + matH),
                    strokeWidth = if (isMajor) 1.4f else 0.7f
                )
                if (isMajor && xMm > 0f && pxPerMm > 0.65f && xMm < matConfig.widthMm) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${xMm.toInt()}",
                        topLeft = Offset(px + 3f, originY + 3f),
                        style = rulerStyle
                    )
                }
                xMm += step
            }

            var yMm = 0f
            while (yMm <= matConfig.heightMm + 0.01f) {
                val isMajor = (yMm.toInt() % 50 == 0)
                val py = originY + yMm * pxPerMm
                drawLine(
                    color = if (isMajor) colorScheme.outline.copy(alpha = 0.55f)
                    else colorScheme.outlineVariant.copy(alpha = 0.35f),
                    start = Offset(originX, py),
                    end = Offset(originX + matW, py),
                    strokeWidth = if (isMajor) 1.4f else 0.7f
                )
                if (isMajor && yMm > 0f && pxPerMm > 0.65f && yMm < matConfig.heightMm) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${yMm.toInt()}",
                        topLeft = Offset(originX + 4f, py + 2f),
                        style = rulerStyle
                    )
                }
                yMm += step
            }

            // Mat outer frame
            drawRect(
                color = colorScheme.outline,
                topLeft = Offset(originX, originY),
                size = Size(matW, matH),
                style = Stroke(width = 2.2f)
            )

            // Origin (0,0) plotter home marker
            drawCircle(
                color = colorScheme.secondary,
                radius = 5.5f,
                center = Offset(originX, originY)
            )

            // Optional Weeding Box
            if (boundingBox != null) {
                val m = matConfig.weedingMarginMm
                val wx0 = (boundingBox.minX - m).coerceAtLeast(0f)
                val wy0 = (boundingBox.minY - m).coerceAtLeast(0f)
                val wx1 = (boundingBox.maxX + m).coerceAtMost(matConfig.widthMm)
                val wy1 = (boundingBox.maxY + m).coerceAtMost(matConfig.heightMm)
                drawRect(
                    color = colorScheme.secondary,
                    topLeft = Offset(originX + wx0 * pxPerMm, originY + wy0 * pxPerMm),
                    size = Size((wx1 - wx0) * pxPerMm, (wy1 - wy0) * pxPerMm),
                    style = Stroke(
                        width = 2.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }

            // Render vector elements (Fill first with EvenOdd rule, then Cut-Line stroke)
            elements.forEach { el ->
                if (!el.isVisible) return@forEach
                val isSelected = el.id in effectiveSelectedIds
                val matchesCutColor = matConfig.cutColorFilterHex == null ||
                    (el.fillColorHex ?: el.strokeColorHex) == matConfig.cutColorFilterHex
                val alphaFactor = if (!interactive && !matchesCutColor) 0.18f else 1f
                val cutLineColor = Color(el.strokeColorHex).copy(alpha = alphaFactor)
                val polylines = compiledPaths[el.id].orEmpty()

                val fillHex = el.fillColorHex
                if (fillHex != null && !isWireframeMode) {
                    val fillPath = Path().apply {
                        fillType = PathFillType.EvenOdd
                    }
                    var hasFillable = false
                    polylines.forEach { poly ->
                        if (poly.closed && poly.points.size >= 3) {
                            poly.points.forEachIndexed { idx, pt ->
                                val sx = originX + pt.xMm * pxPerMm
                                val sy = originY + pt.yMm * pxPerMm
                                if (idx == 0) fillPath.moveTo(sx, sy) else fillPath.lineTo(sx, sy)
                            }
                            fillPath.close()
                            hasFillable = true
                        }
                    }
                    if (hasFillable) {
                        drawPath(
                            path = fillPath,
                            color = Color(fillHex).copy(alpha = 0.45f * alphaFactor),
                            style = Fill
                        )
                    }
                }

                polylines.forEach { poly ->
                    if (poly.points.size >= 2) {
                        val path = Path()
                        poly.points.forEachIndexed { idx, pt ->
                            val sx = originX + pt.xMm * pxPerMm
                            val sy = originY + pt.yMm * pxPerMm
                            if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        if (poly.closed) path.close()

                        if (isSelected) {
                            drawPath(
                                path = path,
                                color = colorScheme.primary.copy(alpha = 0.34f),
                                style = Stroke(width = 7.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                        drawPath(
                            path = path,
                            color = cutLineColor,
                            style = Stroke(
                                width = when {
                                    isWireframeMode && isSelected -> 2.1f
                                    isWireframeMode -> 1.3f
                                    isSelected -> 2.8f
                                    else -> 2.2f
                                },
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // Subtle per-member box when multiple items are selected or element is locked
                if ((isSelected && effectiveSelectedIds.size > 1 && interactive) || el.isLocked) {
                    val boxColor = if (el.isLocked) {
                        colorScheme.secondary.copy(alpha = 0.65f)
                    } else {
                        colorScheme.primary.copy(alpha = 0.35f)
                    }
                    drawRect(
                        color = boxColor,
                        topLeft = Offset(originX + el.xMm * pxPerMm, originY + el.yMm * pxPerMm),
                        size = Size(el.widthMm * pxPerMm, el.heightMm * pxPerMm),
                        style = Stroke(
                            width = if (el.isLocked) 1.6f else 1.1f,
                            pathEffect = if (el.isLocked) PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f) else null
                        )
                    )
                }
            }

            // Smart Center-Alignment Guides when moving element(s)
            val movingSelection = activeDragTarget is DragTarget.MoveElement
            val unlockedSelBox = computeSelectionBoxMm(elements.filter { it.id in effectiveSelectedIds })
            if (movingSelection && unlockedSelBox != null && interactive) {
                val selCx = (unlockedSelBox.minX + unlockedSelBox.maxX) / 2f
                val selCy = (unlockedSelBox.minY + unlockedSelBox.maxY) / 2f
                val matCx = matConfig.widthMm / 2f
                val matCy = matConfig.heightMm / 2f
                if (kotlin.math.abs(selCx - matCx) <= 2.0f) {
                    val gx = originX + matCx * pxPerMm
                    drawLine(
                        color = colorScheme.tertiary,
                        start = Offset(gx, originY),
                        end = Offset(gx, originY + matH),
                        strokeWidth = 1.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                }
                if (kotlin.math.abs(selCy - matCy) <= 2.0f) {
                    val gy = originY + matCy * pxPerMm
                    drawLine(
                        color = colorScheme.tertiary,
                        start = Offset(originX, gy),
                        end = Offset(originX + matW, gy),
                        strokeWidth = 1.8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                }
            }

            // Active selection overlay: either Node Edit anchors OR bold Resize/Rotate/Dimension handles
            val selectedList = elements.filter { it.id in effectiveSelectedIds }
            if (selectedList.isNotEmpty() && interactive) {
                if (isNodeEditMode && selectedList.size == 1) {
                    val polylines = compiledPaths[selectedList.first().id].orEmpty()
                    polylines.forEachIndexed { pIdx, poly ->
                        if (!poly.closed && poly.points.size >= 2) {
                            val pFirst = poly.points.first()
                            val pLast = poly.points.last()
                            drawLine(
                                color = Color(0xFFF59E0B).copy(alpha = 0.72f),
                                start = Offset(originX + pLast.xMm * pxPerMm, originY + pLast.yMm * pxPerMm),
                                end = Offset(originX + pFirst.xMm * pxPerMm, originY + pFirst.yMm * pxPerMm),
                                strokeWidth = 2.0f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                            )
                        }
                        poly.points.forEachIndexed { ptIdx, pt ->
                            val sx = originX + pt.xMm * pxPerMm
                            val sy = originY + pt.yMm * pxPerMm
                            val isSelNode = selectedNode?.polylineIndex == pIdx &&
                                selectedNode?.pointIndex == ptIdx
                            val isOpenStart = !poly.closed && ptIdx == 0
                            val isOpenEnd = !poly.closed && ptIdx == poly.points.lastIndex
                            if (isSelNode) {
                                drawCircle(
                                    color = colorScheme.secondary.copy(alpha = 0.35f),
                                    radius = 15f,
                                    center = Offset(sx, sy)
                                )
                                drawCircle(
                                    color = colorScheme.secondary,
                                    radius = 7.5f,
                                    center = Offset(sx, sy)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3.2f,
                                    center = Offset(sx, sy)
                                )
                            } else {
                                val ringColor = when {
                                    isOpenStart -> Color(0xFF22C55E)
                                    isOpenEnd -> Color(0xFFF59E0B)
                                    else -> colorScheme.primary
                                }
                                drawCircle(
                                    color = colorScheme.surface,
                                    radius = if (isOpenStart || isOpenEnd) 6.8f else 5.5f,
                                    center = Offset(sx, sy)
                                )
                                drawCircle(
                                    color = ringColor,
                                    radius = if (isOpenStart || isOpenEnd) 6.8f else 5.5f,
                                    center = Offset(sx, sy),
                                    style = Stroke(width = if (isOpenStart || isOpenEnd) 2.6f else 2.0f)
                                )
                            }
                        }
                    }
                } else {
                    val box = computeSelectionBoxMm(selectedList)
                    if (box != null) {
                        val bx = originX + box.minX * pxPerMm
                        val by = originY + box.minY * pxPerMm
                        val bw = box.width * pxPerMm
                        val bh = box.height * pxPerMm

                        // Dashed selection frame
                        drawRect(
                            color = colorScheme.primary.copy(alpha = 0.9f),
                            topLeft = Offset(bx, by),
                            size = Size(bw, bh),
                            style = Stroke(
                                width = 2.0f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f), 0f)
                            )
                        )

                        // Group badge at top-left when multiple items or grouped
                        val isGrouped = selectedList.size > 1 && selectedList.all { it.groupId != null } &&
                            selectedList.mapNotNull { it.groupId }.distinct().size == 1
                        if (selectedList.size > 1) {
                            val badgeText = if (isGrouped) "مجموعة (${selectedList.size})" else "محدد (${selectedList.size})"
                            val measured = textMeasurer.measure(badgeText, groupBadgeStyle)
                            val bW = measured.size.width + 14f
                            val bH = measured.size.height + 6f
                            drawRoundRect(
                                color = colorScheme.secondary,
                                topLeft = Offset(bx, by - bH - 6f),
                                size = Size(bW, bH),
                                cornerRadius = CornerRadius(7f, 7f)
                            )
                            drawText(
                                textLayoutResult = measured,
                                topLeft = Offset(bx + 7f, by - bH - 3f)
                            )
                        }

                        // Rotation stem & handle above top-center (single element)
                        if (selectedList.size == 1) {
                            val rotTop = Offset(bx + bw / 2f, by - rotateHandleOffsetPx)
                            drawLine(
                                color = colorScheme.secondary,
                                start = Offset(bx + bw / 2f, by),
                                end = rotTop,
                                strokeWidth = 2.2f
                            )
                            drawCircle(
                                color = colorScheme.secondary,
                                radius = 10f,
                                center = rotTop
                            )
                            drawCircle(
                                color = colorScheme.surface,
                                radius = 3.8f,
                                center = rotTop
                            )
                        }

                        // 8 bold interactive resize handles
                        val handles = handlePositionsScreen(box, pxPerMm, originX, originY)
                        handles.forEach { (kind, pos) ->
                            val isCorner = kind == ResizeHandle.TOP_LEFT ||
                                kind == ResizeHandle.TOP_RIGHT ||
                                kind == ResizeHandle.BOTTOM_LEFT ||
                                kind == ResizeHandle.BOTTOM_RIGHT
                            val isActiveHandle = activeDragTarget.let {
                                it is DragTarget.Resize && it.handle == kind
                            }
                            if (isCorner) {
                                val r = if (isActiveHandle) 12.5f else 10f
                                drawCircle(
                                    color = if (isActiveHandle) colorScheme.secondary else colorScheme.surface,
                                    radius = r,
                                    center = pos
                                )
                                drawCircle(
                                    color = if (isActiveHandle) colorScheme.onSecondary else colorScheme.primary,
                                    radius = r,
                                    center = pos,
                                    style = Stroke(width = 3.0f)
                                )
                                drawCircle(
                                    color = colorScheme.primary,
                                    radius = 3.5f,
                                    center = pos
                                )
                            } else {
                                val isHorizontal = kind == ResizeHandle.TOP_CENTER || kind == ResizeHandle.BOTTOM_CENTER
                                val rw = if (isHorizontal) 22f else 9f
                                val rh = if (isHorizontal) 9f else 22f
                                drawRoundRect(
                                    color = if (isActiveHandle) colorScheme.secondary else colorScheme.primary,
                                    topLeft = Offset(pos.x - rw / 2f, pos.y - rh / 2f),
                                    size = Size(rw, rh),
                                    cornerRadius = CornerRadius(5f, 5f)
                                )
                            }
                        }

                        // Live Width / Height measurement pills
                        val wLabel = "${box.width.roundToInt()} mm"
                        val wMeasured = textMeasurer.measure(wLabel, dimBadgeStyle)
                        val wPillW = wMeasured.size.width + 14f
                        val wPillH = wMeasured.size.height + 6f
                        val wPillX = bx + (bw - wPillW) / 2f
                        val wPillY = by + bh + 12f
                        drawRoundRect(
                            color = colorScheme.primary,
                            topLeft = Offset(wPillX, wPillY),
                            size = Size(wPillW, wPillH),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawText(
                            textLayoutResult = wMeasured,
                            topLeft = Offset(wPillX + 7f, wPillY + 3f)
                        )

                        val hLabel = "${box.height.roundToInt()} mm"
                        val hMeasured = textMeasurer.measure(hLabel, dimBadgeStyle)
                        val hPillW = hMeasured.size.width + 14f
                        val hPillH = hMeasured.size.height + 6f
                        val hPillX = bx + bw + 12f
                        val hPillY = by + (bh - hPillH) / 2f
                        drawRoundRect(
                            color = colorScheme.primary,
                            topLeft = Offset(hPillX, hPillY),
                            size = Size(hPillW, hPillH),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawText(
                            textLayoutResult = hMeasured,
                            topLeft = Offset(hPillX + 7f, hPillY + 3f)
                        )
                    }
                }
            }

            // Live Freehand Draw Stroke Preview
            if (freehandScreenPts.size >= 2) {
                val fhPath = Path()
                freehandScreenPts.forEachIndexed { idx, pt ->
                    if (idx == 0) fhPath.moveTo(pt.x, pt.y) else fhPath.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = fhPath,
                    color = colorScheme.secondary,
                    style = Stroke(width = 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawCircle(
                    color = Color(0xFF22C55E),
                    radius = 7f,
                    center = freehandScreenPts.first()
                )
            }

            // Live Plotter Blade Carriage Head Indicator
            simulatedHeadMm?.let { head ->
                val hx = originX + head.xMm * pxPerMm
                val hy = originY + head.yMm * pxPerMm
                drawCircle(
                    color = colorScheme.secondary.copy(alpha = 0.30f),
                    radius = 16f,
                    center = Offset(hx, hy)
                )
                drawCircle(
                    color = colorScheme.secondary,
                    radius = 6f,
                    center = Offset(hx, hy)
                )
                drawLine(
                    color = colorScheme.secondary.copy(alpha = 0.7f),
                    start = Offset(hx - 14f, hy),
                    end = Offset(hx + 14f, hy),
                    strokeWidth = 2f
                )
                drawLine(
                    color = colorScheme.secondary.copy(alpha = 0.7f),
                    start = Offset(hx, hy - 14f),
                    end = Offset(hx, hy + 14f),
                    strokeWidth = 2f
                )
            }
        }
    }
}
