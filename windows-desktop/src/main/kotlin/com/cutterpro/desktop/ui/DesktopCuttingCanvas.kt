package com.cutterpro.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.cutterpro.desktop.model.CuttingMatConfig
import com.cutterpro.desktop.model.DesignElement
import com.cutterpro.desktop.model.NodeRef
import com.cutterpro.desktop.model.Point2D
import com.cutterpro.desktop.vector.DesktopVectorEngine
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private enum class DragMode { NONE, MOVE, NODE, FREEHAND, HANDLE_BR, HANDLE_ROT }

@Composable
fun DesktopCuttingCanvas(
    elements: List<DesignElement>,
    selectedIds: Set<String>,
    primaryId: String?,
    matConfig: CuttingMatConfig,
    vectorEngine: DesktopVectorEngine,
    isNodeEditMode: Boolean,
    selectedNode: NodeRef?,
    isFreehandMode: Boolean,
    isWireframeMode: Boolean,
    simulatedHead: Point2D?,
    zoomScale: Float,
    panOffset: Offset,
    onSelectElement: (String?) -> Unit,
    onMoveElement: (String, Float, Float, Boolean) -> Unit,
    onResizeElement: (String, Float, Float, Float, Float, Boolean) -> Unit,
    onRotateElement: (String, Float, Boolean) -> Unit,
    onSelectNode: (NodeRef?) -> Unit,
    onMoveNode: (Int, Int, Float, Float, Boolean) -> Unit,
    onCommitFreehand: (List<Point2D>) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = Color(0xFF101724)
    val matColor = Color(0xFF1A2334)
    val gridMajor = Color(0xFF34455F)
    val gridMinor = Color(0xFF233044)
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary

    var matPxPerMm by remember { mutableStateOf(1f) }
    var matOriginPx by remember { mutableStateOf(Offset.Zero) }
    val freehandPts = remember { mutableStateListOf<Point2D>() }

    val curEls by rememberUpdatedState(elements)
    val curSel by rememberUpdatedState(primaryId)
    val curNodeEdit by rememberUpdatedState(isNodeEditMode)
    val curFreehand by rememberUpdatedState(isFreehandMode)
    val curNode by rememberUpdatedState(selectedNode)

    Box(modifier = modifier.background(bgColor)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        if (curFreehand) return@detectTapGestures
                        val mmX = (pos.x - matOriginPx.x) / matPxPerMm
                        val mmY = (pos.y - matOriginPx.y) / matPxPerMm
                        if (curNodeEdit && curSel != null) {
                            val active = curEls.firstOrNull { it.id == curSel }
                            if (active != null) {
                                val polys = vectorEngine.buildWorldPolylines(active)
                                var best: NodeRef? = null; var bestD = 12f
                                polys.forEachIndexed { pi, poly ->
                                    poly.points.forEachIndexed { idx, pt ->
                                        val d = hypot(pt.xMm - mmX, pt.yMm - mmY)
                                        if (d < bestD) { bestD = d; best = NodeRef(pi, idx) }
                                    }
                                }
                                onSelectNode(best)
                                return@detectTapGestures
                            }
                        }
                        val hit = curEls.asReversed().firstOrNull { el ->
                            el.isVisible && mmX in el.xMm..(el.xMm + el.widthMm) && mmY in el.yMm..(el.yMm + el.heightMm)
                        }
                        onSelectElement(hit?.id)
                    }
                }
                .pointerInput(Unit) {
                    var mode = DragMode.NONE
                    var dragTargetId: String? = null
                    var dragNode: NodeRef? = null
                    var lastPos = Offset.Zero

                    detectDragGestures(
                        onDragStart = { start ->
                            lastPos = start
                            val mmX = (start.x - matOriginPx.x) / matPxPerMm
                            val mmY = (start.y - matOriginPx.y) / matPxPerMm
                            if (curFreehand) {
                                mode = DragMode.FREEHAND
                                freehandPts.clear()
                                freehandPts.add(Point2D(mmX, mmY))
                                return@detectDragGestures
                            }
                            if (curNodeEdit && curSel != null) {
                                val el = curEls.firstOrNull { it.id == curSel }
                                if (el != null) {
                                    val polys = vectorEngine.buildWorldPolylines(el)
                                    var best: NodeRef? = curNode; var bestD = 12f
                                    polys.forEachIndexed { pi, poly ->
                                        poly.points.forEachIndexed { idx, pt ->
                                            val d = hypot(pt.xMm - mmX, pt.yMm - mmY)
                                            if (d < bestD) { bestD = d; best = NodeRef(pi, idx) }
                                        }
                                    }
                                    if (best != null) {
                                        dragNode = best
                                        onSelectNode(best)
                                        mode = DragMode.NODE
                                        return@detectDragGestures
                                    }
                                }
                            }
                            val selEl = curEls.firstOrNull { it.id == curSel }
                            if (selEl != null && selEl.isVisible) {
                                val brX = matOriginPx.x + (selEl.xMm + selEl.widthMm) * matPxPerMm
                                val brY = matOriginPx.y + (selEl.yMm + selEl.heightMm) * matPxPerMm
                                val rotX = matOriginPx.x + (selEl.xMm + selEl.widthMm / 2f) * matPxPerMm
                                val rotY = matOriginPx.y + selEl.yMm * matPxPerMm - 24f
                                if (hypot(start.x - brX, start.y - brY) <= 18f) {
                                    dragTargetId = selEl.id; mode = DragMode.HANDLE_BR; return@detectDragGestures
                                }
                                if (hypot(start.x - rotX, start.y - rotY) <= 18f) {
                                    dragTargetId = selEl.id; mode = DragMode.HANDLE_ROT; return@detectDragGestures
                                }
                            }
                            val hit = curEls.asReversed().firstOrNull { el ->
                                el.isVisible && mmX in el.xMm..(el.xMm + el.widthMm) && mmY in el.yMm..(el.yMm + el.heightMm)
                            }
                            if (hit != null) {
                                dragTargetId = hit.id
                                onSelectElement(hit.id)
                                mode = DragMode.MOVE
                            } else mode = DragMode.NONE
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            lastPos += dragAmount
                            val dxMm = dragAmount.x / matPxPerMm
                            val dyMm = dragAmount.y / matPxPerMm
                            when (mode) {
                                DragMode.FREEHAND -> {
                                    val mmX = (lastPos.x - matOriginPx.x) / matPxPerMm
                                    val mmY = (lastPos.y - matOriginPx.y) / matPxPerMm
                                    freehandPts.add(Point2D(mmX, mmY))
                                }
                                DragMode.MOVE -> dragTargetId?.let { onMoveElement(it, dxMm, dyMm, false) }
                                DragMode.NODE -> dragNode?.let { onMoveNode(it.polylineIndex, it.pointIndex, dxMm, dyMm, false) }
                                DragMode.HANDLE_BR -> {
                                    val el = curEls.firstOrNull { it.id == dragTargetId } ?: return@detectDragGestures
                                    onResizeElement(el.id, el.xMm, el.yMm, el.widthMm + dxMm, el.heightMm + dyMm, false)
                                }
                                DragMode.HANDLE_ROT -> {
                                    val el = curEls.firstOrNull { it.id == dragTargetId } ?: return@detectDragGestures
                                    val cx = matOriginPx.x + (el.xMm + el.widthMm / 2f) * matPxPerMm
                                    val cy = matOriginPx.y + (el.yMm + el.heightMm / 2f) * matPxPerMm
                                    val deg = (atan2(lastPos.y - cy, lastPos.x - cx) * 180f / PI.toFloat()) + 90f
                                    onRotateElement(el.id, deg, false)
                                }
                                DragMode.NONE -> Unit
                            }
                        },
                        onDragEnd = {
                            when (mode) {
                                DragMode.FREEHAND -> {
                                    onCommitFreehand(freehandPts.toList())
                                    freehandPts.clear()
                                }
                                DragMode.MOVE -> dragTargetId?.let { onMoveElement(it, 0f, 0f, true) }
                                DragMode.NODE -> dragNode?.let { onMoveNode(it.polylineIndex, it.pointIndex, 0f, 0f, true) }
                                DragMode.HANDLE_BR -> {
                                    curEls.firstOrNull { it.id == dragTargetId }?.let {
                                        onResizeElement(it.id, it.xMm, it.yMm, it.widthMm, it.heightMm, true)
                                    }
                                }
                                DragMode.HANDLE_ROT -> {
                                    curEls.firstOrNull { it.id == dragTargetId }?.let { onRotateElement(it.id, it.rotationDeg, true) }
                                }
                                DragMode.NONE -> Unit
                            }
                            mode = DragMode.NONE
                        }
                    )
                }
        ) {
            val pad = 32f
            val baseScale = minOf(
                (size.width - pad * 2) / matConfig.widthMm.coerceAtLeast(1f),
                (size.height - pad * 2) / matConfig.heightMm.coerceAtLeast(1f)
            )
            val pxPerMm = baseScale * zoomScale
            val matW = matConfig.widthMm * pxPerMm
            val matH = matConfig.heightMm * pxPerMm
            val ox = (size.width - matW) / 2f + panOffset.x
            val oy = (size.height - matH) / 2f + panOffset.y
            matPxPerMm = pxPerMm
            matOriginPx = Offset(ox, oy)

            drawRect(matColor, topLeft = Offset(ox, oy), size = androidx.compose.ui.geometry.Size(matW, matH))

            val step = matConfig.gridStepMm.coerceAtLeast(5f)
            var xMm = 0f
            while (xMm <= matConfig.widthMm + 0.1f) {
                val px = ox + xMm * pxPerMm
                val major = (xMm.toInt() % 50 == 0)
                drawLine(if (major) gridMajor else gridMinor, Offset(px, oy), Offset(px, oy + matH), strokeWidth = if (major) 1.4f else 0.7f)
                xMm += step
            }
            var yMm = 0f
            while (yMm <= matConfig.heightMm + 0.1f) {
                val py = oy + yMm * pxPerMm
                val major = (yMm.toInt() % 50 == 0)
                drawLine(if (major) gridMajor else gridMinor, Offset(ox, py), Offset(ox + matW, py), strokeWidth = if (major) 1.4f else 0.7f)
                yMm += step
            }
            drawRect(primaryColor.copy(alpha = 0.75f), topLeft = Offset(ox, oy), size = androidx.compose.ui.geometry.Size(matW, matH), style = Stroke(2f))

            elements.forEach { el ->
                if (!el.isVisible) return@forEach
                val isSel = el.id in selectedIds
                val polys = vectorEngine.buildWorldPolylines(el)
                val colorKey = el.fillColorHex ?: el.strokeColorHex
                val alphaFactor = if (matConfig.cutColorFilterHex != null && matConfig.cutColorFilterHex != colorKey) 0.18f else 1f
                val strokeC = Color(el.strokeColorHex).copy(alpha = alphaFactor)
                val fillC = el.fillColorHex?.let { Color(it).copy(alpha = 0.42f * alphaFactor) }

                val path = Path().apply { fillType = PathFillType.EvenOdd }
                polys.forEach { poly ->
                    if (poly.points.size >= 2) {
                        poly.points.forEachIndexed { idx, pt ->
                            val sx = ox + pt.xMm * pxPerMm
                            val sy = oy + pt.yMm * pxPerMm
                            if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                        }
                        if (poly.closed) path.close()
                    }
                }
                if (!isWireframeMode && fillC != null) drawPath(path, color = fillC)
                drawPath(
                    path,
                    color = if (isSel) Color.White.copy(alpha = alphaFactor) else strokeC,
                    style = Stroke(width = if (isWireframeMode) 1.5f else if (isSel) 3.0f else 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                if (isSel && !isNodeEditMode) {
                    val bx = ox + el.xMm * pxPerMm
                    val by = oy + el.yMm * pxPerMm
                    val bw = el.widthMm * pxPerMm
                    val bh = el.heightMm * pxPerMm
                    drawRect(
                        color = accentColor.copy(alpha = 0.85f),
                        topLeft = Offset(bx, by),
                        size = androidx.compose.ui.geometry.Size(bw, bh),
                        style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    )
                    if (el.id == primaryId) {
                        drawCircle(accentColor, radius = 8f, center = Offset(bx + bw, by + bh))
                        drawLine(accentColor, Offset(bx + bw / 2f, by), Offset(bx + bw / 2f, by - 22f), strokeWidth = 2f)
                        drawCircle(primaryColor, radius = 7.5f, center = Offset(bx + bw / 2f, by - 22f))
                    }
                }
                if (isNodeEditMode && el.id == primaryId) {
                    polys.forEachIndexed { pi, poly ->
                        poly.points.forEachIndexed { idx, pt ->
                            val selN = selectedNode?.polylineIndex == pi && selectedNode.pointIndex == idx
                            drawCircle(
                                color = if (selN) Color(0xFFFF4D6D) else accentColor,
                                radius = if (selN) 7.5f else 5f,
                                center = Offset(ox + pt.xMm * pxPerMm, oy + pt.yMm * pxPerMm)
                            )
                        }
                    }
                }
            }

            if (freehandPts.size >= 2) {
                val p = Path()
                freehandPts.forEachIndexed { i, pt ->
                    val sx = ox + pt.xMm * pxPerMm; val sy = oy + pt.yMm * pxPerMm
                    if (i == 0) p.moveTo(sx, sy) else p.lineTo(sx, sy)
                }
                drawPath(p, accentColor, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            simulatedHead?.let { head ->
                val hx = ox + head.xMm * pxPerMm
                val hy = oy + head.yMm * pxPerMm
                drawCircle(Color(0xFFFF4D6D), radius = 8f, center = Offset(hx, hy))
                drawCircle(Color.White, radius = 3.5f, center = Offset(hx, hy))
            }
        }
    }
}
