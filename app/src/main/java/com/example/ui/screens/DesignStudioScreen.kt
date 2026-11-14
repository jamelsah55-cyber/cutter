package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AlignmentAction
import com.example.domain.model.DesignElement
import com.example.domain.model.DesignFontStyle
import com.example.domain.model.VectorShapeType
import com.example.domain.model.VinylColorPalettes
import com.example.domain.vector.ImageContourTracer
import com.example.domain.vector.ImageTraceMode
import com.example.domain.vector.TracedImageResult
import com.example.ui.CutterUiState
import com.example.ui.MainViewModel
import com.example.ui.components.CuttingMatCanvas
import com.example.ui.components.ObjectManagerSheetContent
import com.example.ui.components.PathToolsBottomSheetContent
import com.example.ui.components.SignMasterBottomColorStrip
import com.example.ui.components.SignMasterSideToolbox
import com.example.ui.components.SignMasterTopPropertyRibbon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignStudioScreen(
    state: CutterUiState,
    viewModel: MainViewModel,
    onNavigateToCut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showTextDialog by remember { mutableStateOf(false) }
    var showShapesSheet by remember { mutableStateOf(false) }
    var showInspectorSheet by remember { mutableStateOf(false) }
    var showArrangeSheet by remember { mutableStateOf(false) }
    var showColorPaletteSheet by remember { mutableStateOf(false) }
    var showPathToolsSheet by remember { mutableStateOf(false) }
    var showObjectManagerSheet by remember { mutableStateOf(false) }
    var colorStripApplyToStroke by remember { mutableStateOf(false) }
    var showArrayCloneDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showMatSizeDialog by remember { mutableStateOf(false) }
    var pendingTraceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val svgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "design.svg"
                if (!content.isNullOrBlank()) {
                    viewModel.importVectorFile(name, content)
                } else {
                    viewModel.showMessage("الملف المحدد فارغ")
                }
            } catch (_: Exception) {
                viewModel.showMessage("تعذر قراءة الملف المحدد")
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = ImageContourTracer.loadScaledBitmap(context, uri, maxSidePx = 420)
            if (bmp != null) {
                pendingTraceBitmap = bmp
            } else {
                viewModel.showMessage("تعذر فتح الصورة المحددة")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Studio Top Telemetry Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showMatSizeDialog = true }
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = if (state.isNodeEditMode) "وضع تعديل العقد" else "استوديو التصميم",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.isNodeEditMode) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${state.matConfig.widthMm.toInt()}×${state.matConfig.heightMm.toInt()} مم · ${state.elements.size} عنصر · ${(state.cutPlan.cutDistanceMm / 10f).roundToInt() / 100f} م قص",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "تراجع")
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "إعادة")
                    }
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = state.elements.isNotEmpty(),
                        modifier = Modifier.testTag("save_project_button")
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = "حفظ التصميم")
                    }
                    IconButton(
                        onClick = { viewModel.clearCanvas() },
                        enabled = state.elements.isNotEmpty(),
                        modifier = Modifier.testTag("clear_canvas_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "تفريغ اللوحة")
                    }
                    FilledTonalButton(
                        onClick = onNavigateToCut,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("quick_go_cut_button")
                    ) {
                        Text("قص الآن", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Quick Canvas Modifiers, Mat Size, Multi-Select & Zoom Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = false,
                onClick = { showMatSizeDialog = true },
                label = { Text("الحصيرة: ${state.matConfig.widthMm.toInt()}×${state.matConfig.heightMm.toInt()}") },
                leadingIcon = {
                    Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("mat_size_chip")
            )
            FilterChip(
                selected = state.isFreehandDrawMode,
                onClick = { viewModel.toggleFreehandDrawMode() },
                label = { Text("رسم حر") },
                leadingIcon = {
                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.testTag("freehand_draw_chip")
            )
            FilterChip(
                selected = state.isMultiSelectMode,
                onClick = { viewModel.toggleMultiSelectMode() },
                label = { Text("تحديد متعدد") },
                leadingIcon = {
                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                modifier = Modifier.testTag("multi_select_chip")
            )
            if (state.elements.size >= 2) {
                FilterChip(
                    selected = state.selectedElements.size == state.elements.size,
                    onClick = { viewModel.selectAllElements() },
                    label = { Text("تحديد الكل") },
                    modifier = Modifier.testTag("select_all_chip")
                )
            }
            FilterChip(
                selected = state.matConfig.snapToGrid,
                onClick = { viewModel.toggleSnapToGrid() },
                label = { Text("تثبيت الشبكة") },
                leadingIcon = {
                    Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("snap_grid_chip")
            )
            FilterChip(
                selected = state.matConfig.weedingBoxEnabled,
                onClick = { viewModel.toggleWeedingBox() },
                label = { Text("إطار التفريغ") },
                leadingIcon = {
                    Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.testTag("weeding_box_chip")
            )

            IconButton(
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4.5f) },
                modifier = Modifier.size(40.dp).testTag("zoom_in_button")
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "تكبير")
            }
            IconButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.6f) },
                modifier = Modifier.size(40.dp).testTag("zoom_out_button")
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "تصغير")
            }
            IconButton(
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                modifier = Modifier.size(40.dp).testTag("zoom_reset_button")
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "توسيط اللوحة")
            }
        }

        // SignMaster Live Coordinates, Wireframe Toggle & Object Manager Ribbon
        SignMasterTopPropertyRibbon(
            selectedElements = state.selectedElements,
            totalElementsCount = state.elements.size,
            isWireframeMode = state.isWireframeMode,
            onToggleWireframe = { viewModel.toggleWireframeMode() },
            onOpenObjectManager = { showObjectManagerSheet = true },
            onOpenNumericInspector = {
                if (state.selectedElement != null) showInspectorSheet = true
            }
        )

        // SignMaster CAD Workspace: Docked Side Toolbox + Interactive Cutting Mat Canvas
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            SignMasterSideToolbox(
                isSelectMode = !state.isNodeEditMode && !state.isFreehandDrawMode,
                isNodeEditMode = state.isNodeEditMode,
                isFreehandMode = state.isFreehandDrawMode,
                hasSingleSelection = state.selectedElement != null && state.selectedElements.size <= 1,
                onSelectTool = {
                    if (state.isNodeEditMode) viewModel.exitNodeEditMode()
                    if (state.isFreehandDrawMode) viewModel.toggleFreehandDrawMode()
                },
                onNodeEditTool = {
                    if (state.isNodeEditMode) viewModel.exitNodeEditMode() else viewModel.enterNodeEditMode()
                },
                onAddText = { showTextDialog = true },
                onAddShape = { showShapesSheet = true },
                onToggleFreehand = { viewModel.toggleFreehandDrawMode() },
                onTraceImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenPathTools = {
                    if (state.selectedElements.isNotEmpty()) showPathToolsSheet = true
                },
                onImportSvg = {
                    svgPicker.launch(arrayOf("image/svg+xml", "text/plain", "application/octet-stream", "*/*"))
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                CuttingMatCanvas(
                    elements = state.elements,
                    selectedElementId = state.selectedElementId,
                    selectedElementIds = state.selectedElementIds,
                    matConfig = state.matConfig,
                    geometryEngine = viewModel.geometryEngine,
                    simulatedHeadMm = state.simulatedHeadMm,
                    zoomScale = zoomScale,
                    panOffset = panOffset,
                    onZoomPanChange = { z, p ->
                        zoomScale = z
                        panOffset = p
                    },
                    onSelectElement = { viewModel.selectElement(it) },
                    onDragElementDelta = { id, dx, dy, commit ->
                        viewModel.moveElementDelta(id, dx, dy, commit)
                    },
                    isNodeEditMode = state.isNodeEditMode,
                    selectedNode = state.selectedNode,
                    onSelectNode = { viewModel.selectNode(it) },
                    onDragNodeDelta = { pIdx, ptIdx, dx, dy, commit ->
                        viewModel.moveNodeDelta(pIdx, ptIdx, dx, dy, commit)
                    },
                    onResizeElementBounds = { id, nx, ny, nw, nh, commit ->
                        viewModel.resizeElementBounds(id, nx, ny, nw, nh, commit)
                    },
                    onResizeSelectionBounds = { nx, ny, nw, nh, commit ->
                        viewModel.resizeSelectionBounds(nx, ny, nw, nh, commit)
                    },
                    onRotateElement = { id, deg, commit ->
                        viewModel.rotateElementAbsolute(id, deg, commit)
                    },
                    isFreehandDrawMode = state.isFreehandDrawMode,
                    onFinishFreehandStroke = { pts -> viewModel.commitFreehandStroke(pts) },
                    isWireframeMode = state.isWireframeMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("cutting_mat_canvas")
                )

            androidx.compose.animation.AnimatedVisibility(
                visible = state.isFreehandDrawMode,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = "اسحب بإصبعك للرسم الحر · اقترب من نقطة البداية للإغلاق التلقائي",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Selected Element(s) OR Node Edit Contextual Floating Pill
            androidx.compose.animation.AnimatedVisibility(
                visible = state.selectedElements.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                val selList = state.selectedElements
                if (selList.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 6.dp,
                        modifier = Modifier.border(
                            1.dp,
                            if (state.isNodeEditMode) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            RoundedCornerShape(16.dp)
                        )
                    ) {
                        if (state.isNodeEditMode && state.selectedElement != null) {
                            val selected = state.selectedElement!!
                            val activeNode = state.selectedNode
                            val totalNodesInPoly = activeNode?.let {
                                selected.normalizedPaths.getOrNull(it.polylineIndex)?.points?.size ?: 0
                            } ?: 0
                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = "تعديل العقد",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (activeNode != null) {
                                            "عقدة ${activeNode.pointIndex + 1}/$totalNodesInPoly"
                                        } else "حدد عقدة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = { viewModel.insertNodeAfterSelected() },
                                    modifier = Modifier.testTag("insert_node_button")
                                ) {
                                    Icon(Icons.Default.AddCircleOutline, contentDescription = "إضافة عقدة")
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSelectedNode() },
                                    modifier = Modifier.testTag("delete_node_button")
                                ) {
                                    Icon(
                                        Icons.Default.RemoveCircleOutline,
                                        contentDescription = "حذف العقدة",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                                val activePoly = activeNode?.let { selected.normalizedPaths.getOrNull(it.polylineIndex) }
                                IconButton(
                                    onClick = { viewModel.toggleSelectedPolylineClosed() },
                                    modifier = Modifier.testTag("toggle_close_path_button")
                                ) {
                                    Icon(
                                        Icons.Default.Loop,
                                        contentDescription = "فتح أو إغلاق المسار",
                                        tint = if (activePoly?.closed == false) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.simplifySelectedPaths(0.55f) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("node_simplify_button")
                                ) {
                                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("تبسيط", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.smoothSelectedCurves() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("node_smooth_button")
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("تنعيم", style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(
                                    onClick = { showPathToolsSheet = true },
                                    modifier = Modifier.testTag("node_path_tools_button")
                                ) {
                                    Icon(Icons.Default.Gesture, contentDescription = "أدوات المنحنيات")
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.exitNodeEditMode() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("exit_node_edit_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("إنهاء")
                                }
                            }
                        } else {
                            val single = selList.singleOrNull()
                            val boxMinX = selList.minOf { it.xMm }
                            val boxMinY = selList.minOf { it.yMm }
                            val boxMaxX = selList.maxOf { it.xMm + it.widthMm }
                            val boxMaxY = selList.maxOf { it.yMm + it.heightMm }
                            val boxW = (boxMaxX - boxMinX).roundToInt()
                            val boxH = (boxMaxY - boxMinY).roundToInt()

                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        text = when {
                                            single != null -> single.name
                                            state.isGroupedSelection -> "مجموعة (${selList.size})"
                                            else -> "${selList.size} عناصر"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${boxW}×${boxH} مم" +
                                            if (single?.mirrorHorizontal == true) " · معكوس" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                                if (single != null) {
                                    FilledTonalIconButton(
                                        onClick = { viewModel.enterNodeEditMode() },
                                        modifier = Modifier.testTag("enter_node_edit_button")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل العقد")
                                    }
                                    FilledTonalIconButton(
                                        onClick = { showInspectorSheet = true },
                                        modifier = Modifier.testTag("element_properties_button")
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = "الخصائص والأبعاد")
                                    }
                                    FilledTonalButton(
                                        onClick = { showArrayCloneDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("array_clone_button")
                                    ) {
                                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("مصفوفة", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { showPathToolsSheet = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("curve_tools_button")
                                ) {
                                    Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("المنحنيات", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { showColorPaletteSheet = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("color_palette_button")
                                ) {
                                    val activeStroke = Color(selList.first().strokeColorHex)
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(selList.first().fillColorHex?.let { Color(it) } ?: Color.Transparent)
                                            .border(2.2.dp, activeStroke, CircleShape)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("الألوان", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { showArrangeSheet = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("align_arrange_button")
                                ) {
                                    Icon(Icons.Default.AlignHorizontalCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("محاذاة", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.weldSelectedElements() },
                                    enabled = state.canWeld,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("weld_button")
                                ) {
                                    Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("لحام", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.groupSelectedElements() },
                                    enabled = state.canGroup,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("group_button")
                                ) {
                                    Icon(Icons.Default.GroupWork, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("تجميع", style = MaterialTheme.typography.labelMedium)
                                }
                                FilledTonalButton(
                                    onClick = { viewModel.ungroupSelectedElements() },
                                    enabled = state.canUngroup,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("ungroup_button")
                                ) {
                                    Icon(Icons.Default.LayersClear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("تفكيك", style = MaterialTheme.typography.labelMedium)
                                }
                                val anyLocked = selList.any { it.isLocked }
                                IconButton(
                                    onClick = { viewModel.toggleLockSelected() },
                                    modifier = Modifier.testTag("lock_element_button")
                                ) {
                                    Icon(
                                        if (anyLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (anyLocked) "فك القفل" else "قفل العنصر",
                                        tint = if (anyLocked) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.toggleMirrorSelected() },
                                    modifier = Modifier.testTag("mirror_htv_button")
                                ) {
                                    Icon(
                                        Icons.Default.Flip,
                                        contentDescription = "عكس أفقي للطباعة الحرارية",
                                        tint = if (single?.mirrorHorizontal == true) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.rotateSelectedBy(45f) },
                                    modifier = Modifier.testTag("rotate_element_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "تدوير 45 درجة")
                                }
                                IconButton(
                                    onClick = { viewModel.duplicateSelected() },
                                    modifier = Modifier.testTag("duplicate_element_button")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "تكرار العنصر")
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSelected() },
                                    modifier = Modifier.testTag("delete_element_button")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "حذف العنصر",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        // SignMaster Bottom Vinyl Color Swatch Bar (1-tap Fill / Cut-Line Stroke)
        SignMasterBottomColorStrip(
            selectedElements = state.selectedElements,
            applyToStroke = colorStripApplyToStroke,
            onToggleMode = { colorStripApplyToStroke = !colorStripApplyToStroke },
            onSelectFill = { hex -> viewModel.updateSelectionFillColor(hex) },
            onSelectStroke = { hex -> viewModel.updateSelectionStrokeColor(hex) },
            onOpenFullPalette = { showColorPaletteSheet = true }
        )
    }

    pendingTraceBitmap?.let { bmp ->
        TraceImageDialog(
            bitmap = bmp,
            onDismiss = {
                pendingTraceBitmap = null
            },
            onConfirm = { paths, aspectRatio ->
                viewModel.addTracedImageElement("صورة متتبعة", paths, aspectRatio)
                pendingTraceBitmap = null
            }
        )
    }

    if (showTextDialog) {
        AddArabicTextDialog(
            onDismiss = { showTextDialog = false },
            onConfirm = { text, font, heightMm ->
                viewModel.addTextElement(text, font, heightMm)
                showTextDialog = false
            }
        )
    }

    if (showShapesSheet) {
        ModalBottomSheet(onDismissRequest = { showShapesSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "إضافة شكل هندسي للقص",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                val shapeItems = listOf(
                    VectorShapeType.RECTANGLE to Icons.Default.AddBox,
                    VectorShapeType.ROUNDED_RECT to Icons.Default.CropFree,
                    VectorShapeType.CIRCLE to Icons.Default.CenterFocusStrong,
                    VectorShapeType.ISLAMIC_STAR to Icons.Default.AutoAwesome,
                    VectorShapeType.STAR to Icons.Default.AutoAwesome,
                    VectorShapeType.HEXAGON to Icons.Default.Category,
                    VectorShapeType.DIAMOND to Icons.Default.Category,
                    VectorShapeType.HEART to Icons.Default.AutoAwesome,
                    VectorShapeType.ARROW to Icons.AutoMirrored.Filled.RotateRight,
                    VectorShapeType.REGISTRATION_MARKS to Icons.Default.CropFree
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(290.dp)
                ) {
                    items(shapeItems) { (shapeType, icon) ->
                        Card(
                            onClick = {
                                viewModel.addShape(shapeType)
                                showShapesSheet = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.testTag("shape_option_${shapeType.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = shapeType.labelAr,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = shapeType.labelAr,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showInspectorSheet && state.selectedElement != null) {
        val el = state.selectedElement!!
        ModalBottomSheet(
            onDismissRequest = { showInspectorSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ElementPropertiesSheetContent(
                element = el,
                matWidthMm = state.matConfig.widthMm,
                matHeightMm = state.matConfig.heightMm,
                onUpdatePosition = { x, y ->
                    viewModel.updateSelectedElement {
                        it.copy(
                            xMm = x.coerceIn(0f, (state.matConfig.widthMm - it.widthMm).coerceAtLeast(0f)),
                            yMm = y.coerceIn(0f, (state.matConfig.heightMm - it.heightMm).coerceAtLeast(0f))
                        )
                    }
                },
                onUpdateSize = { w, h ->
                    viewModel.updateSelectedElement {
                        it.copy(
                            widthMm = w.coerceIn(8f, state.matConfig.widthMm),
                            heightMm = h.coerceIn(8f, state.matConfig.heightMm)
                        )
                    }
                },
                onUpdateRotation = { deg -> viewModel.updateSelectedElement { it.copy(rotationDeg = deg) } },
                onToggleMirror = { viewModel.toggleMirrorSelected() },
                onToggleVerticalMirror = { viewModel.toggleVerticalFlipSelected() },
                onToggleLock = { viewModel.toggleLockSelected() },
                onSnap = { viewModel.snapSelectedToGrid() },
                onClose = { showInspectorSheet = false }
            )
        }
    }

    if (showArrangeSheet && state.selectedElements.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showArrangeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ArrangeAndAlignSheetContent(
                selectionCount = state.selectedElements.size,
                onAlign = { action ->
                    viewModel.alignSelection(action)
                    showArrangeSheet = false
                },
                onBringToFront = {
                    viewModel.bringSelectedToFront()
                    showArrangeSheet = false
                },
                onSendToBack = {
                    viewModel.sendSelectedToBack()
                    showArrangeSheet = false
                }
            )
        }
    }

    if (showArrayCloneDialog && state.selectedElement != null) {
        ArrayCloneDialog(
            source = state.selectedElement!!,
            onDismiss = { showArrayCloneDialog = false },
            onConfirm = { rows, cols, gapX, gapY ->
                viewModel.generateArrayClone(rows, cols, gapX, gapY)
                showArrayCloneDialog = false
            }
        )
    }

    if (showColorPaletteSheet && state.selectedElements.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showColorPaletteSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ColorPaletteSheetContent(
                selectedElements = state.selectedElements,
                onSelectFillColor = { hex -> viewModel.updateSelectionFillColor(hex) },
                onSelectStrokeColor = { hex -> viewModel.updateSelectionStrokeColor(hex) },
                onClose = { showColorPaletteSheet = false }
            )
        }
    }

    if (showPathToolsSheet && state.selectedElements.isNotEmpty()) {
        val sel = state.selectedElements
        val pathStats = remember(sel) { viewModel.previewSelectionPathStats(sel) }
        ModalBottomSheet(
            onDismissRequest = { showPathToolsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            PathToolsBottomSheetContent(
                selectedElements = sel,
                pathStats = pathStats,
                previewSimplifiedCount = { tol -> viewModel.previewSimplifiedNodeCount(sel, tol) },
                onApplySimplify = { tol -> viewModel.simplifySelectedPaths(tol) },
                onSmoothCurves = { viewModel.smoothSelectedCurves() },
                onCloseOpenCurves = { viewModel.closeSelectedOpenCurves() },
                onBooleanOp = { op -> viewModel.applyBooleanOp(op) },
                onCreateContourOffset = { off ->
                    viewModel.createContourOffsetForSelected(off)
                    showPathToolsSheet = false
                },
                onToggleMirrorHorizontal = { viewModel.toggleMirrorSelected() },
                onToggleMirrorVertical = { viewModel.toggleVerticalFlipSelected() },
                onCloseSheet = { showPathToolsSheet = false }
            )
        }
    }

    if (showObjectManagerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showObjectManagerSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ObjectManagerSheetContent(
                elements = state.elements,
                selectedIds = state.selectedElementIds,
                onSelectElement = { id -> viewModel.selectElement(id) },
                onToggleVisibility = { id -> viewModel.toggleElementVisibility(id) },
                onToggleLock = { id -> viewModel.toggleElementLockById(id) },
                onMoveForward = { id -> viewModel.moveElementOrderById(id, moveForward = true) },
                onMoveBackward = { id -> viewModel.moveElementOrderById(id, moveForward = false) },
                onClose = { showObjectManagerSheet = false }
            )
        }
    }

    if (showMatSizeDialog) {
        MatDimensionsDialog(
            currentWidthMm = state.matConfig.widthMm,
            currentHeightMm = state.matConfig.heightMm,
            onDismiss = { showMatSizeDialog = false },
            onConfirm = { w, h ->
                viewModel.updateMatDimensions(w, h)
                zoomScale = 1f
                panOffset = Offset.Zero
                showMatSizeDialog = false
            }
        )
    }

    if (showSaveDialog) {
        var title by remember { mutableStateOf("تصميم ${state.elements.size} عناصر") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("حفظ المشروع في المكتبة") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم المشروع") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("save_project_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentProject(title)
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_project_button")
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun MatDimensionsDialog(
    currentWidthMm: Float,
    currentHeightMm: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float, Float) -> Unit
) {
    var widthText by remember { mutableStateOf(currentWidthMm.roundToInt().toString()) }
    var heightText by remember { mutableStateOf(currentHeightMm.roundToInt().toString()) }

    val presets = listOf(
        Triple("حصيرة 30×30 سم", 300f, 300f),
        Triple("حصيرة 30×60 سم", 300f, 600f),
        Triple("لوح 60×40 سم", 600f, 400f),
        Triple("رول عريض 120×60", 1200f, 600f),
        Triple("ورق A4", 210f, 297f),
        Triple("ورق A3", 297f, 420f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أبعاد الحصيرة ومساحة القص (مم)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("مقاسات جاهزة:", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { (label, w, h) ->
                                val isSel = widthText.toFloatOrNull() == w && heightText.toFloatOrNull() == h
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        widthText = w.toInt().toString()
                                        heightText = h.toInt().toString()
                                    },
                                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it.filter { c -> c.isDigit() } },
                        label = { Text("العرض (مم)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("mat_width_input")
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it.filter { c -> c.isDigit() } },
                        label = { Text("الارتفاع (مم)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("mat_height_input")
                    )
                }
                Text(
                    text = "النطاق المسموح: العرض 100–1600 مم · الارتفاع 100–3000 مم",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = widthText.toFloatOrNull() ?: currentWidthMm
                    val h = heightText.toFloatOrNull() ?: currentHeightMm
                    onConfirm(w, h)
                },
                modifier = Modifier.testTag("confirm_mat_size_button")
            ) {
                Text("تطبيق الأبعاد")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun TraceImageDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirm: (List<com.example.domain.model.Polyline2D>, Float) -> Unit
) {
    val autoOtsu = remember(bitmap) { ImageContourTracer.computeOtsuThreshold(bitmap) }
    var threshold by remember(bitmap) { mutableIntStateOf(autoOtsu) }
    var simplify by remember { mutableFloatStateOf(1.0f) }
    var minNoiseFilter by remember { mutableIntStateOf(6) }
    var invert by remember { mutableStateOf(false) }
    var traceMode by remember { mutableStateOf(ImageTraceMode.SILHOUETTE) }
    var isTracing by remember { mutableStateOf(true) }
    var traced by remember(bitmap) {
        mutableStateOf(TracedImageResult(emptyList(), bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1), 0))
    }

    LaunchedEffect(bitmap, threshold, simplify, invert, minNoiseFilter, traceMode) {
        isTracing = true
        val res = withContext(Dispatchers.Default) {
            ImageContourTracer.traceBitmap(
                bitmap = bitmap,
                threshold = threshold,
                invert = invert,
                simplifyEpsilonPx = simplify,
                minNoiseFilter = minNoiseFilter,
                traceMode = traceMode
            )
        }
        traced = res
        isTracing = false
    }

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val previewColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تتبع حدود الصورة بدقة عالية") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "معاينة الصورة المحددة",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .alpha(0.32f)
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        val aspect = traced.aspectRatio.coerceAtLeast(0.1f)
                        val availW = size.width
                        val availH = size.height
                        val drawW: Float
                        val drawH: Float
                        if (availW / availH > aspect) {
                            drawH = availH
                            drawW = drawH * aspect
                        } else {
                            drawW = availW
                            drawH = drawW / aspect
                        }
                        val ox = (availW - drawW) / 2f
                        val oy = (availH - drawH) / 2f

                        traced.normalizedPaths.forEach { poly ->
                            if (poly.points.size >= 2) {
                                val path = Path()
                                poly.points.forEachIndexed { idx, pt ->
                                    val sx = ox + pt.xMm * drawW
                                    val sy = oy + pt.yMm * drawH
                                    if (idx == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                                }
                                if (poly.closed) path.close()
                                drawPath(
                                    path = path,
                                    color = previewColor,
                                    style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                    if (isTracing) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                Text(
                    text = "المسارات: ${traced.normalizedPaths.size} · العقد: ${traced.totalNodes} · دقة ${bitmap.width}×${bitmap.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImageTraceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = traceMode == mode,
                            onClick = { traceMode = mode },
                            label = { Text(mode.labelAr, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("العتبة (تلقائي: $autoOtsu): $threshold", style = MaterialTheme.typography.labelLarge)
                        TextButton(
                            onClick = { threshold = autoOtsu },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("ضبط تلقائي")
                        }
                    }
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { threshold = it.roundToInt() },
                        valueRange = 25f..235f
                    )
                }

                Column {
                    Text(
                        "دقة التفاصيل وتنعيم العقد: ${String.format("%.1f", simplify)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = simplify,
                        onValueChange = { simplify = it },
                        valueRange = 0.35f..3.5f
                    )
                }

                Column {
                    Text("تجاهل النقاط والشوائب الصغيرة: $minNoiseFilter", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = minNoiseFilter.toFloat(),
                        onValueChange = { minNoiseFilter = it.roundToInt() },
                        valueRange = 4f..22f
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("عكس الألوان (تصميم فاتح على خلفية داكنة)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = invert, onCheckedChange = { invert = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(traced.normalizedPaths, traced.aspectRatio) },
                enabled = traced.normalizedPaths.isNotEmpty() && !isTracing,
                modifier = Modifier.testTag("confirm_trace_image_button")
            ) {
                Text("إضافة للتصميم")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}


@Composable
private fun AddArabicTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, DesignFontStyle, Float) -> Unit
) {
    var text by remember { mutableStateOf("بسم الله") }
    var fontStyle by remember { mutableStateOf(DesignFontStyle.AMIRI) }
    var heightMm by remember { mutableFloatStateOf(45f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة نص وتحويله لمسار قص") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("النص (عربي أو إنجليزي)") },
                    modifier = Modifier.fillMaxWidth().testTag("text_dialog_input")
                )
                Text("نمط الخط:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DesignFontStyle.entries.forEach { style ->
                        FilterChip(
                            selected = fontStyle == style,
                            onClick = { fontStyle = style },
                            label = { Text(style.labelAr) }
                        )
                    }
                }
                Text(
                    text = "ارتفاع النص: ${heightMm.roundToInt()} مم",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = heightMm,
                    onValueChange = { heightMm = it },
                    valueRange = 15f..150f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text, fontStyle, heightMm) },
                modifier = Modifier.testTag("confirm_add_text_button")
            ) {
                Text("إضافة للوحة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ElementPropertiesSheetContent(
    element: DesignElement,
    matWidthMm: Float,
    matHeightMm: Float,
    onUpdatePosition: (Float, Float) -> Unit,
    onUpdateSize: (Float, Float) -> Unit,
    onUpdateRotation: (Float) -> Unit,
    onToggleMirror: () -> Unit,
    onToggleVerticalMirror: () -> Unit,
    onToggleLock: () -> Unit,
    onSnap: () -> Unit,
    onClose: () -> Unit
) {
    var keepAspect by remember { mutableStateOf(true) }
    val aspect = remember(element.id) {
        (element.widthMm / element.heightMm.coerceAtLeast(1f)).coerceIn(0.05f, 20f)
    }
    var xText by remember(element.id, element.xMm) { mutableStateOf(element.xMm.roundToInt().toString()) }
    var yText by remember(element.id, element.yMm) { mutableStateOf(element.yMm.roundToInt().toString()) }
    var wText by remember(element.id, element.widthMm) { mutableStateOf(element.widthMm.roundToInt().toString()) }
    var hText by remember(element.id, element.heightMm) { mutableStateOf(element.heightMm.roundToInt().toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "خصائص: ${element.name}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            FilterChip(
                selected = keepAspect,
                onClick = { keepAspect = !keepAspect },
                label = { Text("قفل النسبة") },
                leadingIcon = {
                    Icon(
                        if (keepAspect) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        // Exact X / Y position fields
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = xText,
                onValueChange = { txt ->
                    val filtered = txt.filter { it.isDigit() }
                    xText = filtered
                    filtered.toFloatOrNull()?.let { nx -> onUpdatePosition(nx, element.yMm) }
                },
                label = { Text("الموقع X (مم)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = yText,
                onValueChange = { txt ->
                    val filtered = txt.filter { it.isDigit() }
                    yText = filtered
                    filtered.toFloatOrNull()?.let { ny -> onUpdatePosition(element.xMm, ny) }
                },
                label = { Text("الموقع Y (مم)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        // Exact Width / Height fields
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = wText,
                onValueChange = { txt ->
                    val filtered = txt.filter { it.isDigit() }
                    wText = filtered
                    filtered.toFloatOrNull()?.takeIf { it >= 8f }?.let { nw ->
                        val nh = if (keepAspect) (nw / aspect).coerceIn(8f, matHeightMm) else element.heightMm
                        onUpdateSize(nw, nh)
                    }
                },
                label = { Text("العرض (مم)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hText,
                onValueChange = { txt ->
                    val filtered = txt.filter { it.isDigit() }
                    hText = filtered
                    filtered.toFloatOrNull()?.takeIf { it >= 8f }?.let { nh ->
                        val nw = if (keepAspect) (nh * aspect).coerceIn(8f, matWidthMm) else element.widthMm
                        onUpdateSize(nw, nh)
                    }
                },
                label = { Text("الارتفاع (مم)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Column {
            Text("العرض: ${element.widthMm.roundToInt()} مم", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = element.widthMm.coerceIn(10f, matWidthMm),
                onValueChange = { nw ->
                    val nh = if (keepAspect) (nw / aspect).coerceIn(8f, matHeightMm) else element.heightMm
                    onUpdateSize(nw, nh)
                },
                valueRange = 10f..matWidthMm.coerceAtLeast(20f)
            )
        }

        Column {
            Text("الارتفاع: ${element.heightMm.roundToInt()} مم", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = element.heightMm.coerceIn(10f, matHeightMm),
                onValueChange = { nh ->
                    val nw = if (keepAspect) (nh * aspect).coerceIn(8f, matWidthMm) else element.widthMm
                    onUpdateSize(nw, nh)
                },
                valueRange = 10f..matHeightMm.coerceAtLeast(20f)
            )
        }

        Column {
            Text("التدوير: ${element.rotationDeg.roundToInt()}°", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = element.rotationDeg,
                onValueChange = onUpdateRotation,
                valueRange = 0f..360f
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onToggleMirror,
                modifier = Modifier.weight(1f),
                colors = if (element.mirrorHorizontal) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) else ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("أفقي", maxLines = 1)
            }
            OutlinedButton(
                onClick = onToggleVerticalMirror,
                modifier = Modifier.weight(1f),
                colors = if (element.mirrorVertical) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) else ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("عمودي", maxLines = 1)
            }
            OutlinedButton(
                onClick = onToggleLock,
                modifier = Modifier.weight(1f),
                colors = if (element.isLocked) ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) else ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(
                    if (element.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (element.isLocked) "مقفل" else "قفل", maxLines = 1)
            }
            OutlinedButton(
                onClick = onSnap,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("شبكة", maxLines = 1)
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("تم", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ArrangeAndAlignSheetContent(
    selectionCount: Int,
    onAlign: (AlignmentAction) -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "محاذاة وترتيب الطبقات ($selectionCount محدد)",
            style = MaterialTheme.typography.titleLarge
        )

        FilledTonalButton(
            onClick = { onAlign(AlignmentAction.CENTER_MAT) },
            modifier = Modifier.fillMaxWidth().testTag("align_center_mat_button")
        ) {
            Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("توسيط كامل في منتصف الحصيرة")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onAlign(AlignmentAction.ALIGN_LEFT) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AlignHorizontalLeft, contentDescription = "يسار", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("يسار")
            }
            OutlinedButton(onClick = { onAlign(AlignmentAction.CENTER_HORIZONTAL) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AlignHorizontalCenter, contentDescription = "وسط", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("أفقي")
            }
            OutlinedButton(onClick = { onAlign(AlignmentAction.ALIGN_RIGHT) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AlignHorizontalRight, contentDescription = "يمين", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("يمين")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { onAlign(AlignmentAction.ALIGN_TOP) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AlignVerticalTop, contentDescription = "أعلى", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("أعلى")
            }
            OutlinedButton(onClick = { onAlign(AlignmentAction.CENTER_VERTICAL) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AlignVerticalCenter, contentDescription = "عمودي", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("عمودي")
            }
            OutlinedButton(onClick = { onAlign(AlignmentAction.ALIGN_BOTTOM) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AlignVerticalBottom, contentDescription = "أسفل", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("أسفل")
            }
        }

        if (selectionCount >= 3) {
            Text("توزيع المسافات (3+ عناصر):", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAlign(AlignmentAction.DISTRIBUTE_HORIZONTAL) },
                    modifier = Modifier.weight(1f).testTag("distribute_horizontal_button")
                ) {
                    Text("توزيع أفقي متساوٍ", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { onAlign(AlignmentAction.DISTRIBUTE_VERTICAL) },
                    modifier = Modifier.weight(1f).testTag("distribute_vertical_button")
                ) {
                    Text("توزيع عمودي متساوٍ", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Text("ترتيب الطبقات:", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBringToFront,
                modifier = Modifier.weight(1f).testTag("bring_to_front_button")
            ) {
                Icon(Icons.Default.FlipToFront, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إحضار للأمام")
            }
            OutlinedButton(
                onClick = onSendToBack,
                modifier = Modifier.weight(1f).testTag("send_to_back_button")
            ) {
                Icon(Icons.Default.FlipToBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إرسال للخلف")
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ArrayCloneDialog(
    source: DesignElement,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Float, Float) -> Unit
) {
    var rows by remember { mutableIntStateOf(2) }
    var cols by remember { mutableIntStateOf(3) }
    var gapMm by remember { mutableFloatStateOf(5f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تكرار مصفوفة ملصقات") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "العنصر: ${source.name} (${source.widthMm.roundToInt()}×${source.heightMm.roundToInt()} مم)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("عدد الصفوف: $rows", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = rows.toFloat(),
                    onValueChange = { rows = it.roundToInt().coerceIn(1, 8) },
                    valueRange = 1f..8f,
                    steps = 6
                )
                Text("عدد الأعمدة: $cols", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = cols.toFloat(),
                    onValueChange = { cols = it.roundToInt().coerceIn(1, 8) },
                    valueRange = 1f..8f,
                    steps = 6
                )
                Text("المسافة البينية: ${gapMm.roundToInt()} مم", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = gapMm,
                    onValueChange = { gapMm = it.roundToInt().toFloat() },
                    valueRange = 1f..20f,
                    steps = 18
                )
                Text(
                    text = "سيتم إنشاء ${rows * cols} نسخة كحد أقصى ضمن حدود الحصيرة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(rows, cols, gapMm, gapMm) },
                modifier = Modifier.testTag("confirm_array_clone_button")
            ) {
                Text("توليد المصفوفة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun ColorPaletteSheetContent(
    selectedElements: List<DesignElement>,
    onSelectFillColor: (Long?) -> Unit,
    onSelectStrokeColor: (Long) -> Unit,
    onClose: () -> Unit
) {
    val activeFill = selectedElements.firstOrNull()?.fillColorHex
    val activeStroke = selectedElements.firstOrNull()?.strokeColorHex ?: 0xFF00D4B8L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "لوح ألوان التعبئة وخط القص",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                text = if (selectedElements.size == 1) selectedElements.first().name
                else "${selectedElements.size} عناصر",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Fill Color Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("لون التعبئة (لون الفينيل الداخلي):", style = MaterialTheme.typography.labelLarge)
                FilterChip(
                    selected = activeFill == null,
                    onClick = { onSelectFillColor(null) },
                    label = { Text("بدون تعبئة") },
                    leadingIcon = {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    modifier = Modifier.testTag("fill_none_button")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VinylColorPalettes.fillSwatches.forEachIndexed { idx, swatch ->
                    val isSelected = activeFill == swatch.hex
                    val color = Color(swatch.hex)
                    val checkTint = if (color.luminance() > 0.5f) Color.Black else Color.White
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.2.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { onSelectFillColor(swatch.hex) }
                                .testTag("fill_swatch_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = swatch.labelAr,
                                    tint = checkTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = swatch.labelAr,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Cut-Line Stroke Color Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("لون خط القص (مسار السكين):", style = MaterialTheme.typography.labelLarge)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VinylColorPalettes.cutLineSwatches.forEachIndexed { idx, swatch ->
                    val isSelected = activeStroke == swatch.hex
                    val color = Color(swatch.hex)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .border(
                                    width = if (isSelected) 4.dp else 2.5.dp,
                                    color = color,
                                    shape = CircleShape
                                )
                                .clickable { onSelectStrokeColor(swatch.hex) }
                                .testTag("stroke_swatch_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = swatch.labelAr,
                                    tint = if (color.luminance() > 0.55f) Color.Black else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = swatch.labelAr,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().testTag("close_color_palette_button")
        ) {
            Text("اعتماد الألوان", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

