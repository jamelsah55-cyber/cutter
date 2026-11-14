package com.cutterpro.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cutterpro.desktop.model.AlignmentAction
import com.cutterpro.desktop.model.VectorBooleanOp
import com.cutterpro.desktop.model.VectorShapeType
import com.cutterpro.desktop.model.VinylColorPalettes
import com.cutterpro.desktop.state.DesktopStudioController
import com.cutterpro.desktop.state.DesktopUiState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.util.Locale

private val DesktopDarkColors = darkColorScheme(
    primary = Color(0xFF00D4B8),
    onPrimary = Color(0xFF00201B),
    secondary = Color(0xFFFF7A2F),
    onSecondary = Color(0xFF2B1000),
    background = Color(0xFF0F1622),
    surface = Color(0xFF161F30),
    surfaceVariant = Color(0xFF1F2B40),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444)
)

@Composable
fun DesktopApp(controller: DesktopStudioController) {
    val state by controller.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var activeInspectorTab by remember { mutableStateOf(0) } // 0=Properties, 1=Layers, 2=Machine
    var isEditingFillColor by remember { mutableStateOf(true) }

    var showTextDialog by remember { mutableStateOf(false) }
    var showShapesDialog by remember { mutableStateOf(false) }
    var showMatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            controller.clearStatusMessage()
        }
    }

    MaterialTheme(colorScheme = DesktopDarkColors) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // 1. Top Desktop Menu & Quick Action Bar
                    DesktopTopBar(
                        state = state,
                        controller = controller,
                        zoomScale = zoomScale,
                        onZoomIn = { zoomScale = (zoomScale * 1.18f).coerceAtMost(4f) },
                        onZoomOut = { zoomScale = (zoomScale / 1.18f).coerceAtLeast(0.35f) },
                        onZoomReset = { zoomScale = 1f; panOffset = Offset.Zero },
                        onOpenMatDialog = { showMatDialog = true },
                        onImportVector = {
                            pickDesktopFile("استيراد ملف SVG / PLT", arrayOf("svg", "plt"))?.let {
                                controller.importVectorFile(it)
                            }
                        },
                        onTraceImage = {
                            pickDesktopFile("تتبع صورة إلى مسار قص", arrayOf("png", "jpg", "jpeg", "bmp"))?.let {
                                controller.traceImageFile(it)
                            }
                        },
                        onExportCut = {
                            val ext = state.protocol.fileExtension
                            saveDesktopFile("حفظ ملف القص", "design.$ext")?.let { file ->
                                file.writeText(controller.exportContent(ext))
                            }
                        },
                        onExportSvg = {
                            saveDesktopFile("تصدير تصميم SVG", "design.svg")?.let { file ->
                                file.writeText(controller.exportContent("svg"))
                            }
                        }
                    )

                    // 2. SignMaster Live Coordinate & Wireframe Ribbon
                    DesktopPropertyRibbon(state = state, controller = controller)

                    // 3. Main 3-Pane Workspace (Side Toolbox | Interactive Canvas | Right Docked Inspector)
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DesktopSideToolbox(
                            state = state,
                            controller = controller,
                            onOpenText = { showTextDialog = true },
                            onOpenShapes = { showShapesDialog = true },
                            onImportSvg = {
                                pickDesktopFile("استيراد SVG / PLT", arrayOf("svg", "plt"))?.let {
                                    controller.importVectorFile(it)
                                }
                            },
                            onTraceImage = {
                                pickDesktopFile("تتبع صورة", arrayOf("png", "jpg", "jpeg", "bmp"))?.let {
                                    controller.traceImageFile(it)
                                }
                            }
                        )

                        VerticalDivider(color = Color(0xFF28354B))

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            DesktopCuttingCanvas(
                                elements = state.elements,
                                selectedIds = state.selectedIds,
                                primaryId = state.primaryId,
                                matConfig = state.matConfig,
                                vectorEngine = controller.vectorEngine,
                                isNodeEditMode = state.isNodeEditMode,
                                selectedNode = state.selectedNode,
                                isFreehandMode = state.isFreehandMode,
                                isWireframeMode = state.isWireframeMode,
                                simulatedHead = state.simulatedHead,
                                zoomScale = zoomScale,
                                panOffset = panOffset,
                                onSelectElement = controller::selectElement,
                                onMoveElement = controller::moveElementDelta,
                                onResizeElement = controller::resizeElementBounds,
                                onRotateElement = controller::rotateElement,
                                onSelectNode = controller::selectNode,
                                onMoveNode = controller::moveNodeDelta,
                                onCommitFreehand = controller::commitFreehandStroke,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (state.isNodeEditMode) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.TopCenter).padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("وضع تحرير العقد: اسحب أي نقطة لتعديل المنحنى", style = MaterialTheme.typography.labelMedium)
                                        TextButton(onClick = { controller.exitNodeEditMode() }) { Text("تم") }
                                    }
                                }
                            } else if (state.isFreehandMode) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.align(Alignment.TopCenter).padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("وضع الرسم الحر: ارسم بالماوس مباشرة على الحصيرة", style = MaterialTheme.typography.labelMedium)
                                        TextButton(onClick = { controller.toggleFreehand() }) { Text("إلغاء") }
                                    }
                                }
                            }
                        }

                        VerticalDivider(color = Color(0xFF28354B))

                        // Right/End Docked Inspector
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.width(325.dp).fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TabRow(selectedTabIndex = activeInspectorTab) {
                                    Tab(
                                        selected = activeInspectorTab == 0,
                                        onClick = { activeInspectorTab = 0 },
                                        text = { Text("الخصائص") },
                                        icon = { Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp)) }
                                    )
                                    Tab(
                                        selected = activeInspectorTab == 1,
                                        onClick = { activeInspectorTab = 1 },
                                        text = { Text("الطبقات (${state.elements.size})") },
                                        icon = { Icon(Icons.Default.Layers, null, modifier = Modifier.size(18.dp)) }
                                    )
                                    Tab(
                                        selected = activeInspectorTab == 2,
                                        onClick = { activeInspectorTab = 2 },
                                        text = { Text("الماكينة") },
                                        icon = { Icon(Icons.Default.CenterFocusStrong, null, modifier = Modifier.size(18.dp)) }
                                    )
                                }

                                when (activeInspectorTab) {
                                    0 -> DesktopPropertiesTab(state = state, controller = controller)
                                    1 -> DesktopLayersTab(state = state, controller = controller)
                                    else -> DesktopMachinePanel(
                                        state = state,
                                        controller = controller,
                                        onExportCutFile = {
                                            val ext = state.protocol.fileExtension
                                            saveDesktopFile("حفظ ملف القص", "design.$ext")?.let { file ->
                                                file.writeText(controller.exportContent(ext))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Bottom SignMaster Vinyl Color Strip
                    DesktopColorStrip(
                        isFillMode = isEditingFillColor,
                        onToggleMode = { isEditingFillColor = !isEditingFillColor },
                        onPickFill = { controller.updateFillColor(it) },
                        onPickStroke = { controller.updateStrokeColor(it) }
                    )
                }
            }

            if (showTextDialog) {
                AddDesktopTextDialog(
                    availableFonts = controller.availableFonts,
                    onDismiss = { showTextDialog = false },
                    onAdd = { txt, font, h ->
                        controller.addText(txt, font, h)
                        showTextDialog = false
                    }
                )
            }

            if (showShapesDialog) {
                AddDesktopShapeDialog(
                    onDismiss = { showShapesDialog = false },
                    onSelect = { shape ->
                        controller.addShape(shape)
                        showShapesDialog = false
                    }
                )
            }

            if (showMatDialog) {
                MatDimensionsDialog(
                    currentWidth = state.matConfig.widthMm,
                    currentHeight = state.matConfig.heightMm,
                    onDismiss = { showMatDialog = false },
                    onSave = { w, h ->
                        controller.updateMatSize(w, h)
                        showMatDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DesktopTopBar(
    state: DesktopUiState,
    controller: DesktopStudioController,
    zoomScale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onOpenMatDialog: () -> Unit,
    onImportVector: () -> Unit,
    onTraceImage: () -> Unit,
    onExportCut: () -> Unit,
    onExportSvg: () -> Unit
) {
    Surface(color = Color(0xFF131C2B), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("كتر برو Desktop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            VerticalDivider(modifier = Modifier.height(22.dp).padding(horizontal = 4.dp))

            IconButton(onClick = { controller.undo() }, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع")
            }
            IconButton(onClick = { controller.redo() }, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "إعادة")
            }

            VerticalDivider(modifier = Modifier.height(22.dp).padding(horizontal = 4.dp))

            OutlinedButton(onClick = onImportVector, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("استيراد SVG/PLT")
            }
            OutlinedButton(onClick = onTraceImage, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Default.ImageSearch, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("تتبع صورة")
            }
            OutlinedButton(onClick = onExportSvg, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("تصدير SVG")
            }
            Button(onClick = onExportCut, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text("تصدير ${state.protocol.fileExtension.uppercase()}")
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(onClick = onOpenMatDialog, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Text("الحصيرة: ${state.matConfig.widthMm.toInt()} × ${state.matConfig.heightMm.toInt()} مم")
            }
            IconButton(onClick = onZoomOut) { Icon(Icons.Default.ZoomOut, "تصغير") }
            Text("${(zoomScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { onZoomReset() })
            IconButton(onClick = onZoomIn) { Icon(Icons.Default.ZoomIn, "تكبير") }
        }
    }
}

@Composable
private fun DesktopPropertyRibbon(state: DesktopUiState, controller: DesktopStudioController) {
    val sel = state.selectedElement
    Surface(color = Color(0xFF192336)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (sel != null) {
                Text("العنصر: ${sel.name}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    String.format(Locale.US, "X: %.1f مم | Y: %.1f مم | W: %.1f مم | H: %.1f مم | ∠ %.0f°",
                        sel.xMm, sel.yMm, sel.widthMm, sel.heightMm, sel.rotationDeg),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text("انقر على أي شكل في الحصيرة لتعديله أو أضف نصاً/شكلاً جديداً من صندوق الأدوات", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = state.isWireframeMode,
                onClick = { controller.toggleWireframe() },
                label = { Text("عرض سلكي (Wireframe)") },
                leadingIcon = { Icon(Icons.Default.GridOn, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = state.matConfig.snapToGrid,
                onClick = { controller.toggleSnap() },
                label = { Text("محاذاة الشبكة") }
            )
        }
    }
}

@Composable
private fun DesktopSideToolbox(
    state: DesktopUiState,
    controller: DesktopStudioController,
    onOpenText: () -> Unit,
    onOpenShapes: () -> Unit,
    onImportSvg: () -> Unit,
    onTraceImage: () -> Unit
) {
    Surface(color = Color(0xFF151E2E), modifier = Modifier.width(68.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolBtn(Icons.Default.NearMe, "تحديد", !state.isNodeEditMode && !state.isFreehandMode) {
                controller.exitNodeEditMode()
                if (state.isFreehandMode) controller.toggleFreehand()
            }
            ToolBtn(Icons.Default.SelectAll, "متعدد", state.isMultiSelect) { controller.toggleMultiSelect() }
            ToolBtn(Icons.Default.Edit, "العقد", state.isNodeEditMode) {
                if (state.isNodeEditMode) controller.exitNodeEditMode() else controller.enterNodeEditMode()
            }
            ToolBtn(Icons.Default.TextFields, "نص", false, onOpenText)
            ToolBtn(Icons.Default.Category, "أشكال", false, onOpenShapes)
            ToolBtn(Icons.Default.Gesture, "رسم", state.isFreehandMode) { controller.toggleFreehand() }
            ToolBtn(Icons.Default.ImageSearch, "تتبع", false, onTraceImage)
            ToolBtn(Icons.Default.FileOpen, "SVG", false, onImportSvg)
        }
    }
}

@Composable
private fun ToolBtn(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun DesktopPropertiesTab(state: DesktopUiState, controller: DesktopStudioController) {
    val sel = state.selectedElement
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (sel != null) {
            Text("الأبعاد والموقع (مم)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            var xStr by remember(sel.id, sel.xMm) { mutableStateOf(String.format(Locale.US, "%.1f", sel.xMm)) }
            var yStr by remember(sel.id, sel.yMm) { mutableStateOf(String.format(Locale.US, "%.1f", sel.yMm)) }
            var wStr by remember(sel.id, sel.widthMm) { mutableStateOf(String.format(Locale.US, "%.1f", sel.widthMm)) }
            var hStr by remember(sel.id, sel.heightMm) { mutableStateOf(String.format(Locale.US, "%.1f", sel.heightMm)) }
            var rStr by remember(sel.id, sel.rotationDeg) { mutableStateOf(String.format(Locale.US, "%.0f", sel.rotationDeg)) }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(xStr, { xStr = it }, label = { Text("X") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(yStr, { yStr = it }, label = { Text("Y") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(wStr, { wStr = it }, label = { Text("العرض W") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(hStr, { hStr = it }, label = { Text("الارتفاع H") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(rStr, { rStr = it }, label = { Text("الدوران °") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = {
                    controller.updateSelectedNumeric(
                        x = xStr.toFloatOrNull(),
                        y = yStr.toFloatOrNull(),
                        w = wStr.toFloatOrNull(),
                        h = hStr.toFloatOrNull(),
                        rot = rStr.toFloatOrNull()
                    )
                }) { Text("تطبيق") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { controller.toggleMirrorHorizontal() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Flip, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مرآة أفقية", maxLines = 1)
                }
                OutlinedButton(onClick = { controller.toggleMirrorVertical() }, modifier = Modifier.weight(1f)) {
                    Text("مرآة عمودية", maxLines = 1)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { controller.duplicateSelected() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("نسخ")
                }
                OutlinedButton(onClick = { controller.deleteSelected() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حذف")
                }
            }
        }

        HorizontalDivider()
        Text("العمليات الهندسية (Boolean)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text("حدد عنصرين أو أكثر (زر متعدد) للدمج أو التفريغ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            VectorBooleanOp.entries.forEach { op ->
                OutlinedButton(
                    onClick = { controller.applyBooleanOp(op) },
                    enabled = state.selectedIds.size >= 2,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(op.labelAr.substringBefore(" "), maxLines = 1)
                }
            }
        }

        HorizontalDivider()
        Text("أدوات المنحنيات والملصقات", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { controller.simplifySelected() }, enabled = sel != null, modifier = Modifier.weight(1f)) {
                Text("تبسيط العقد", maxLines = 1)
            }
            OutlinedButton(onClick = { controller.smoothSelected() }, enabled = sel != null, modifier = Modifier.weight(1f)) {
                Text("تنعيم", maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { controller.closeSelectedCurves() }, enabled = sel != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CloseFullscreen, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("إغلاق المسار", maxLines = 1)
            }
            Button(onClick = { controller.createContourOffset(4f) }, enabled = sel != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("حد ملصق", maxLines = 1)
            }
        }

        HorizontalDivider()
        Text("المحاذاة والتوزيع", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AlignmentAction.entries.chunked(3).forEach { rowActions ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    rowActions.forEach { act ->
                        OutlinedButton(
                            onClick = { controller.align(act) },
                            enabled = state.selectedIds.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(act.labelAr, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopLayersTab(state: DesktopUiState, controller: DesktopStudioController) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("مدير العناصر والطبقات (Object Manager)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        state.elements.asReversed().forEach { el ->
            val selected = el.id in state.selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { controller.selectElement(el.id) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(el.fillColorHex ?: el.strokeColorHex)))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(el.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${el.widthMm.toInt()}×${el.heightMm.toInt()} مم", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { controller.moveOrder(el.id, true) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowUpward, "للأمام", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { controller.moveOrder(el.id, false) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ArrowDownward, "للخلف", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { controller.toggleVisibility(el.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(if (el.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "إظهار/إخفاء", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { controller.toggleLock(el.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(if (el.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, "قفل", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DesktopColorStrip(
    isFillMode: Boolean,
    onToggleMode: () -> Unit,
    onPickFill: (Long?) -> Unit,
    onPickStroke: (Long) -> Unit
) {
    val swatches = if (isFillMode) VinylColorPalettes.fillSwatches else VinylColorPalettes.cutLineSwatches
    Surface(color = Color(0xFF121926), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = isFillMode,
                onClick = onToggleMode,
                label = { Text(if (isFillMode) "لون التعبئة" else "لون خط القص") }
            )
            if (isFillMode) {
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF1E293B))
                        .border(1.5.dp, Color(0xFF94A3B8), CircleShape).clickable { onPickFill(null) },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        drawLine(Color(0xFFEF4444), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2.5f)
                    }
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                swatches.forEach { sw ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(sw.hex))
                            .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                if (isFillMode) onPickFill(sw.hex) else onPickStroke(sw.hex)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddDesktopTextDialog(
    availableFonts: List<String>,
    onDismiss: () -> Unit,
    onAdd: (String, String, Float) -> Unit
) {
    var text by remember { mutableStateOf("أهلاً بكم") }
    var selectedFont by remember { mutableStateOf(availableFonts.firstOrNull() ?: "Arial") }
    var heightMm by remember { mutableStateOf("40") }
    var fontMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة نص عربي / إنجليزي (نظام ويندوز)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("النص") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { fontMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("الخط: $selectedFont")
                    }
                    DropdownMenu(expanded = fontMenuOpen, onDismissRequest = { fontMenuOpen = false }) {
                        availableFonts.take(25).forEach { f ->
                            DropdownMenuItem(text = { Text(f) }, onClick = { selectedFont = f; fontMenuOpen = false })
                        }
                    }
                }
                OutlinedTextField(value = heightMm, onValueChange = { heightMm = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("الارتفاع (مم)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(text, selectedFont, heightMm.toFloatOrNull() ?: 40f) }) { Text("إضافة للوحة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AddDesktopShapeDialog(onDismiss: () -> Unit, onSelect: (VectorShapeType) -> Unit) {
    val shapes = VectorShapeType.entries.filter {
        it !in setOf(VectorShapeType.TEXT, VectorShapeType.IMPORTED_SVG, VectorShapeType.TRACED_IMAGE, VectorShapeType.CUSTOM_PATH)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مكتبة الأشكال الهندسية والزخارف") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shapes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { shape ->
                            OutlinedButton(onClick = { onSelect(shape) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.AddBox, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(shape.labelAr, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun MatDimensionsDialog(currentWidth: Float, currentHeight: Float, onDismiss: () -> Unit, onSave: (Float, Float) -> Unit) {
    var w by remember { mutableStateOf(currentWidth.toInt().toString()) }
    var h by remember { mutableStateOf(currentHeight.toInt().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أبعاد حصيرة القص / الرول (مم)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(w, { w = it.filter { c -> c.isDigit() } }, label = { Text("العرض (مم)") }, singleLine = true)
                OutlinedTextField(h, { h = it.filter { c -> c.isDigit() } }, label = { Text("الطول (مم)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { w = "300"; h = "300" }) { Text("30×30 سم") }
                    TextButton(onClick = { w = "600"; h = "1000" }) { Text("60×100 سم") }
                    TextButton(onClick = { w = "1200"; h = "1000" }) { Text("120×100 سم") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(w.toFloatOrNull() ?: currentWidth, h.toFloatOrNull() ?: currentHeight) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun pickDesktopFile(title: String, extensions: Array<String>): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.filenameFilter = FilenameFilter { _, name ->
        extensions.any { ext -> name.lowercase().endsWith(".$ext") }
    }
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return File(dir, fileName)
}

private fun saveDesktopFile(title: String, defaultName: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
    dialog.file = defaultName
    dialog.isVisible = true
    val fileName = dialog.file ?: return null
    val dir = dialog.directory ?: return null
    return File(dir, fileName)
}
