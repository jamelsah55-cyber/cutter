package com.cutterpro.desktop.state

import com.cutterpro.desktop.hardware.DesktopPlotterTransport
import com.cutterpro.desktop.model.AlignmentAction
import com.cutterpro.desktop.model.CutExecutionStatus
import com.cutterpro.desktop.model.CuttingMatConfig
import com.cutterpro.desktop.model.DefaultMaterialPresets
import com.cutterpro.desktop.model.DesignElement
import com.cutterpro.desktop.model.DesktopTransportType
import com.cutterpro.desktop.model.MaterialPreset
import com.cutterpro.desktop.model.NodeRef
import com.cutterpro.desktop.model.PlotterProtocol
import com.cutterpro.desktop.model.Point2D
import com.cutterpro.desktop.model.Polyline2D
import com.cutterpro.desktop.model.SerialLogEntry
import com.cutterpro.desktop.model.SerialPortEntry
import com.cutterpro.desktop.model.VectorBooleanOp
import com.cutterpro.desktop.model.VectorShapeType
import com.cutterpro.desktop.vector.CutPlan
import com.cutterpro.desktop.vector.DesktopImageTracer
import com.cutterpro.desktop.vector.DesktopPlotterCommandGenerator
import com.cutterpro.desktop.vector.DesktopSvgPathParser
import com.cutterpro.desktop.vector.DesktopVectorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

data class DesktopUiState(
    val elements: List<DesignElement> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val primaryId: String? = null,
    val isMultiSelect: Boolean = false,
    val isNodeEditMode: Boolean = false,
    val selectedNode: NodeRef? = null,
    val isFreehandMode: Boolean = false,
    val isWireframeMode: Boolean = false,
    val matConfig: CuttingMatConfig = CuttingMatConfig(),
    val protocol: PlotterProtocol = PlotterProtocol.HPGL,
    val baudRate: Int = 9600,
    val selectedMaterial: MaterialPreset = DefaultMaterialPresets.all.first(),
    val cutPlan: CutPlan = CutPlan(emptyList(), 0f, 0f, 0, emptyList()),
    val transportType: DesktopTransportType = DesktopTransportType.SERIAL_COM,
    val serialPorts: List<SerialPortEntry> = emptyList(),
    val connectedLabel: String? = null,
    val serialLogs: List<SerialLogEntry> = emptyList(),
    val cutStatus: CutExecutionStatus = CutExecutionStatus.IDLE,
    val cutProgress: Float = 0f,
    val simulatedHead: Point2D? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String? = null
) {
    val selectedElements: List<DesignElement> get() = elements.filter { it.id in selectedIds }
    val selectedElement: DesignElement? get() = elements.firstOrNull { it.id == primaryId }
}

class DesktopStudioController {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vectorEngine = DesktopVectorEngine()
    val commandGenerator = DesktopPlotterCommandGenerator(vectorEngine)
    val availableFonts: List<String> = vectorEngine.listAvailableFonts()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val transport = DesktopPlotterTransport(scope, commandGenerator) { msg ->
        _statusMessage.value = msg
    }

    private val initialElements = buildStarterWorkspace()
    private val _elements = MutableStateFlow(initialElements)
    private val _selectedIds = MutableStateFlow(setOf(initialElements.first().id))
    private val _primaryId = MutableStateFlow<String?>(initialElements.first().id)
    private val _flags = MutableStateFlow(listOf(false, false, false, false)) // multi, nodeEdit, freehand, wireframe
    private val _selectedNode = MutableStateFlow<NodeRef?>(null)
    private val _matConfig = MutableStateFlow(CuttingMatConfig())
    private val _protocol = MutableStateFlow(PlotterProtocol.HPGL)
    private val _baudRate = MutableStateFlow(9600)
    private val _material = MutableStateFlow(DefaultMaterialPresets.all.first())
    private val _transportType = MutableStateFlow(DesktopTransportType.SERIAL_COM)

    private val undoStack = ArrayDeque<List<DesignElement>>()
    private val redoStack = ArrayDeque<List<DesignElement>>()
    private val _undoRedo = MutableStateFlow(false to false)
    private var gestureSnapshot: List<DesignElement>? = null

    val uiState: StateFlow<DesktopUiState> = combine(
        _elements,
        _selectedIds,
        _primaryId,
        _flags,
        _selectedNode,
        _matConfig,
        _protocol,
        _baudRate,
        _material,
        _transportType,
        transport.serialPorts,
        transport.connectedLabel,
        transport.logs,
        transport.cutStatus,
        transport.cutProgress,
        transport.simulatedHead,
        _undoRedo,
        _statusMessage
    ) { arr: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val els = arr[0] as List<DesignElement>
        @Suppress("UNCHECKED_CAST")
        val selIds = arr[1] as Set<String>
        val pId = arr[2] as String?
        @Suppress("UNCHECKED_CAST")
        val flags = arr[3] as List<Boolean>
        val node = arr[4] as NodeRef?
        val mat = arr[5] as CuttingMatConfig
        val proto = arr[6] as PlotterProtocol
        val baud = arr[7] as Int
        val preset = arr[8] as MaterialPreset
        val trType = arr[9] as DesktopTransportType
        @Suppress("UNCHECKED_CAST")
        val ports = arr[10] as List<SerialPortEntry>
        val conn = arr[11] as String?
        @Suppress("UNCHECKED_CAST")
        val logs = arr[12] as List<SerialLogEntry>
        val cStatus = arr[13] as CutExecutionStatus
        val cProg = arr[14] as Float
        val simHead = arr[15] as Point2D?
        @Suppress("UNCHECKED_CAST")
        val ur = arr[16] as Pair<Boolean, Boolean>
        val msg = arr[17] as String?

        val plan = commandGenerator.compileCutPlan(els, mat, preset, proto, selIds)
        DesktopUiState(
            elements = els,
            selectedIds = selIds,
            primaryId = pId,
            isMultiSelect = flags[0],
            isNodeEditMode = flags[1] && pId != null,
            selectedNode = node,
            isFreehandMode = flags[2],
            isWireframeMode = flags[3],
            matConfig = mat,
            protocol = proto,
            baudRate = baud,
            selectedMaterial = preset,
            cutPlan = plan,
            transportType = trType,
            serialPorts = ports,
            connectedLabel = conn,
            serialLogs = logs,
            cutStatus = cStatus,
            cutProgress = cProg,
            simulatedHead = simHead,
            canUndo = ur.first,
            canRedo = ur.second,
            statusMessage = msg
        )
    }.stateIn(scope, SharingStarted.Eagerly, DesktopUiState(elements = initialElements))

    init {
        transport.refreshSerialPorts()
    }

    private fun buildStarterWorkspace(): List<DesignElement> {
        val defaultFont = availableFonts.first()
        val (paths, aspect) = vectorEngine.vectorizeText("كتر برو", defaultFont)
        val star = DesignElement(
            id = UUID.randomUUID().toString(),
            name = "شعار نجمة إسلامية",
            type = VectorShapeType.ISLAMIC_STAR,
            xMm = 35f,
            yMm = 45f,
            widthMm = 90f,
            heightMm = 90f,
            strokeColorHex = 0xFF00D4B8L,
            fillColorHex = 0xFF00D4B8L
        )
        val txt = DesignElement(
            id = UUID.randomUUID().toString(),
            name = "نص: كتر برو",
            type = VectorShapeType.TEXT,
            xMm = 140f,
            yMm = 68f,
            widthMm = (44f * aspect).coerceIn(50f, 130f),
            heightMm = 44f,
            textContent = "كتر برو",
            fontFamily = defaultFont,
            normalizedPaths = paths,
            strokeColorHex = 0xFFFF7A2FL,
            fillColorHex = 0xFFFF7A2FL
        )
        return listOf(star, txt)
    }

    private fun commit(next: List<DesignElement>, selIds: Set<String> = _selectedIds.value, primaryId: String? = _primaryId.value) {
        undoStack.addLast(_elements.value)
        if (undoStack.size > 35) undoStack.removeFirst()
        redoStack.clear()
        _elements.value = next
        val valid = selIds.filter { id -> next.any { it.id == id } }.toSet()
        _selectedIds.value = valid
        _primaryId.value = primaryId?.takeIf { it in valid } ?: valid.lastOrNull()
        _undoRedo.value = undoStack.isNotEmpty() to redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_elements.value)
        _elements.value = prev
        val fallback = prev.lastOrNull()?.id
        _selectedIds.value = fallback?.let { setOf(it) } ?: emptySet()
        _primaryId.value = fallback
        _undoRedo.value = undoStack.isNotEmpty() to redoStack.isNotEmpty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_elements.value)
        _elements.value = next
        _undoRedo.value = undoStack.isNotEmpty() to redoStack.isNotEmpty()
    }

    fun selectElement(id: String?) {
        if (id == null) {
            _selectedIds.value = emptySet()
            _primaryId.value = null
            setFlag(1, false)
            return
        }
        val el = _elements.value.firstOrNull { it.id == id } ?: return
        val groupIds = if (el.groupId != null) _elements.value.filter { it.groupId == el.groupId }.map { it.id }.toSet() else setOf(id)
        if (_flags.value[0]) {
            val next = if (groupIds.all { it in _selectedIds.value }) _selectedIds.value - groupIds else _selectedIds.value + groupIds
            _selectedIds.value = next
            _primaryId.value = if (id in next) id else next.lastOrNull()
        } else {
            _selectedIds.value = groupIds
            _primaryId.value = id
        }
    }

    private fun setFlag(index: Int, value: Boolean) {
        _flags.value = _flags.value.toMutableList().apply { this[index] = value }
    }

    fun toggleMultiSelect() = setFlag(0, !_flags.value[0])
    fun toggleFreehand() {
        val next = !_flags.value[2]
        if (next) setFlag(1, false)
        setFlag(2, next)
    }
    fun toggleWireframe() = setFlag(3, !_flags.value[3])

    fun enterNodeEditMode() {
        val sel = uiState.value.selectedElement ?: return
        val baked = vectorEngine.bakeToEditablePaths(sel)
        if (baked != sel) commit(_elements.value.map { if (it.id == baked.id) baked else it }, setOf(baked.id), baked.id)
        _selectedNode.value = NodeRef(0, 0)
        setFlag(1, true); setFlag(2, false)
    }

    fun exitNodeEditMode() {
        setFlag(1, false)
        _selectedNode.value = null
    }

    fun selectNode(ref: NodeRef?) { _selectedNode.value = ref }

    fun moveNodeDelta(polyIdx: Int, ptIdx: Int, dxMm: Float, dyMm: Float, commitSnap: Boolean) {
        val cur = _elements.value
        val sel = cur.firstOrNull { it.id == _primaryId.value } ?: return
        if (sel.isLocked) return
        if (!commitSnap && gestureSnapshot == null) gestureSnapshot = cur
        val nd = vectorEngine.worldDeltaToNormalizedDelta(sel, dxMm, dyMm)
        val paths = sel.normalizedPaths.mapIndexed { pi, poly ->
            if (pi != polyIdx) poly else poly.copy(points = poly.points.mapIndexed { i, pt ->
                if (i == ptIdx) Point2D(pt.xMm + nd.xMm, pt.yMm + nd.yMm) else pt
            })
        }
        val updated = cur.map { if (it.id == sel.id) it.copy(normalizedPaths = paths) else it }
        finishGesture(updated, commitSnap)
    }

    fun addShape(type: VectorShapeType) {
        val mat = _matConfig.value
        val w = if (type == VectorShapeType.REGISTRATION_MARKS) 160f else 65f
        val h = if (type == VectorShapeType.REGISTRATION_MARKS) 160f else 65f
        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = type.labelAr,
            type = type,
            xMm = ((mat.widthMm - w) / 2f).coerceAtLeast(10f),
            yMm = ((mat.heightMm - h) / 2f).coerceAtLeast(10f),
            widthMm = w,
            heightMm = h
        )
        commit(_elements.value + el, setOf(el.id), el.id)
    }

    fun addText(text: String, fontFamily: String, heightMm: Float) {
        val clean = text.trim().ifEmpty { "تصميم جديد" }
        val (paths, aspect) = vectorEngine.vectorizeText(clean, fontFamily)
        val mat = _matConfig.value
        val h = heightMm.coerceIn(12f, 180f)
        val w = (h * aspect).coerceIn(20f, mat.widthMm - 10f)
        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = "نص: ${clean.take(14)}",
            type = VectorShapeType.TEXT,
            xMm = ((mat.widthMm - w) / 2f).coerceAtLeast(10f),
            yMm = ((mat.heightMm - h) / 2f).coerceAtLeast(10f),
            widthMm = w,
            heightMm = h,
            textContent = clean,
            fontFamily = fontFamily,
            normalizedPaths = paths,
            strokeColorHex = 0xFF00D4B8L,
            fillColorHex = 0xFF00D4B8L
        )
        commit(_elements.value + el, setOf(el.id), el.id)
    }

    fun importVectorFile(file: File) {
        val content = runCatching { file.readText() }.getOrNull() ?: return
        val parsed = DesktopSvgPathParser.parseFileContent(file.name, content)
        if (parsed == null || parsed.normalizedPaths.isEmpty()) {
            _statusMessage.value = "تعذر قراءة مسارات قص من الملف ${file.name}"
            return
        }
        val mat = _matConfig.value
        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = file.nameWithoutExtension.take(20),
            type = VectorShapeType.IMPORTED_SVG,
            xMm = ((mat.widthMm - parsed.widthMm) / 2f).coerceAtLeast(5f),
            yMm = ((mat.heightMm - parsed.heightMm) / 2f).coerceAtLeast(5f),
            widthMm = parsed.widthMm,
            heightMm = parsed.heightMm,
            normalizedPaths = parsed.normalizedPaths
        )
        commit(_elements.value + el, setOf(el.id), el.id)
        _statusMessage.value = "تم استيراد ${file.name}"
    }

    fun traceImageFile(file: File) {
        val traced = DesktopImageTracer.loadAndTraceImageFile(file)
        if (traced == null) {
            _statusMessage.value = "لم يتم العثور على حدود واضحة في الصورة"
            return
        }
        val (paths, aspect) = traced
        val mat = _matConfig.value
        val maxSide = (minOf(mat.widthMm, mat.heightMm) * 0.45f).coerceIn(40f, 140f)
        val w = if (aspect >= 1f) maxSide else maxSide * aspect
        val h = if (aspect >= 1f) maxSide / aspect else maxSide
        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = "تتبع: ${file.nameWithoutExtension.take(12)}",
            type = VectorShapeType.TRACED_IMAGE,
            xMm = ((mat.widthMm - w) / 2f).coerceAtLeast(5f),
            yMm = ((mat.heightMm - h) / 2f).coerceAtLeast(5f),
            widthMm = w,
            heightMm = h,
            normalizedPaths = paths
        )
        commit(_elements.value + el, setOf(el.id), el.id)
        _statusMessage.value = "تم تتبع الصورة بنجاح (${paths.size} مسار)"
    }

    fun commitFreehandStroke(pts: List<Point2D>) {
        setFlag(2, false)
        if (pts.size < 3) return
        val minX = pts.minOf { it.xMm }; val minY = pts.minOf { it.yMm }
        val maxX = pts.maxOf { it.xMm }; val maxY = pts.maxOf { it.yMm }
        val w = (maxX - minX).coerceAtLeast(6f)
        val h = (maxY - minY).coerceAtLeast(6f)
        val closed = hypot(pts.last().xMm - pts.first().xMm, pts.last().yMm - pts.first().yMm) <= maxOf(6f, maxOf(w, h) * 0.14f)
        val norm = pts.map { Point2D((it.xMm - minX) / w, (it.yMm - minY) / h) }
        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = "رسم حر",
            type = VectorShapeType.CUSTOM_PATH,
            xMm = minX,
            yMm = minY,
            widthMm = w,
            heightMm = h,
            normalizedPaths = listOf(Polyline2D(norm, closed = closed))
        )
        commit(_elements.value + el, setOf(el.id), el.id)
    }

    fun moveElementDelta(id: String, dxMm: Float, dyMm: Float, commitSnap: Boolean) {
        val cur = _elements.value
        val targetsIds = if (id in _selectedIds.value) _selectedIds.value else setOf(id)
        if (!commitSnap && gestureSnapshot == null) gestureSnapshot = cur
        val mat = _matConfig.value
        val updated = cur.map { el ->
            if (el.id in targetsIds && !el.isLocked) {
                el.copy(
                    xMm = (el.xMm + dxMm).coerceIn(0f, (mat.widthMm - el.widthMm).coerceAtLeast(0f)),
                    yMm = (el.yMm + dyMm).coerceIn(0f, (mat.heightMm - el.heightMm).coerceAtLeast(0f))
                )
            } else el
        }
        finishGesture(updated, commitSnap)
    }

    fun resizeElementBounds(id: String, nx: Float, ny: Float, nw: Float, nh: Float, commitSnap: Boolean) {
        val cur = _elements.value
        if (!commitSnap && gestureSnapshot == null) gestureSnapshot = cur
        val mat = _matConfig.value
        val w = nw.coerceIn(8f, mat.widthMm)
        val h = nh.coerceIn(8f, mat.heightMm)
        val updated = cur.map { el ->
            if (el.id == id && !el.isLocked) {
                el.copy(
                    xMm = nx.coerceIn(0f, (mat.widthMm - w).coerceAtLeast(0f)),
                    yMm = ny.coerceIn(0f, (mat.heightMm - h).coerceAtLeast(0f)),
                    widthMm = w,
                    heightMm = h
                )
            } else el
        }
        finishGesture(updated, commitSnap)
    }

    fun rotateElement(id: String, deg: Float, commitSnap: Boolean) {
        val cur = _elements.value
        if (!commitSnap && gestureSnapshot == null) gestureSnapshot = cur
        val norm = ((deg % 360f) + 360f) % 360f
        val updated = cur.map { if (it.id == id && !it.isLocked) it.copy(rotationDeg = norm) else it }
        finishGesture(updated, commitSnap)
    }

    private fun finishGesture(updated: List<DesignElement>, commitSnap: Boolean) {
        if (commitSnap) {
            val snap = gestureSnapshot ?: _elements.value
            gestureSnapshot = null
            undoStack.addLast(snap)
            if (undoStack.size > 35) undoStack.removeFirst()
            redoStack.clear()
            _elements.value = updated
            _undoRedo.value = undoStack.isNotEmpty() to redoStack.isNotEmpty()
        } else {
            _elements.value = updated
        }
    }

    fun updateSelectedNumeric(x: Float? = null, y: Float? = null, w: Float? = null, h: Float? = null, rot: Float? = null) {
        val id = _primaryId.value ?: return
        val mat = _matConfig.value
        val updated = _elements.value.map { el ->
            if (el.id == id && !el.isLocked) {
                val nw = (w ?: el.widthMm).coerceIn(5f, mat.widthMm)
                val nh = (h ?: el.heightMm).coerceIn(5f, mat.heightMm)
                el.copy(
                    xMm = (x ?: el.xMm).coerceIn(0f, (mat.widthMm - nw).coerceAtLeast(0f)),
                    yMm = (y ?: el.yMm).coerceIn(0f, (mat.heightMm - nh).coerceAtLeast(0f)),
                    widthMm = nw,
                    heightMm = nh,
                    rotationDeg = ((rot ?: el.rotationDeg) % 360f + 360f) % 360f
                )
            } else el
        }
        commit(updated)
    }

    fun applyBooleanOp(op: VectorBooleanOp) {
        val targets = uiState.value.selectedElements
        val res = vectorEngine.booleanOpElements(targets, op) ?: run {
            _statusMessage.value = "حدد عنصرين متداخلين على الأقل"
            return
        }
        val firstIdx = _elements.value.indexOfFirst { it.id in _selectedIds.value }.coerceAtLeast(0)
        val remaining = _elements.value.filterNot { it.id in _selectedIds.value }.toMutableList()
        remaining.add(firstIdx.coerceAtMost(remaining.size), res)
        commit(remaining, setOf(res.id), res.id)
    }

    fun simplifySelected(tolMm: Float = 0.7f) {
        val sel = _selectedIds.value
        val updated = _elements.value.map { if (it.id in sel) vectorEngine.simplifyElement(it, tolMm).first else it }
        commit(updated)
    }

    fun smoothSelected() {
        val sel = _selectedIds.value
        commit(_elements.value.map { if (it.id in sel) vectorEngine.smoothElement(it) else it })
    }

    fun closeSelectedCurves() {
        val sel = _selectedIds.value
        commit(_elements.value.map { if (it.id in sel) vectorEngine.closeOpenCurves(it).first else it })
    }

    fun createContourOffset(offsetMm: Float = 4f) {
        val src = uiState.value.selectedElement ?: return
        val offset = vectorEngine.createContourOffsetElement(src, offsetMm) ?: return
        val idx = _elements.value.indexOfFirst { it.id == src.id }.coerceAtLeast(0)
        val next = _elements.value.toMutableList().apply { add(idx, offset) }
        commit(next, setOf(offset.id), offset.id)
    }

    fun align(action: AlignmentAction) {
        commit(vectorEngine.alignSelection(_elements.value, _selectedIds.value, _matConfig.value, action))
    }

    fun duplicateSelected() {
        val targets = uiState.value.selectedElements
        if (targets.isEmpty()) return
        val copies = targets.map {
            it.copy(id = UUID.randomUUID().toString(), name = "${it.name} (نسخة)", xMm = it.xMm + 12f, yMm = it.yMm + 12f, groupId = null)
        }
        commit(_elements.value + copies, copies.map { it.id }.toSet(), copies.first().id)
    }

    fun deleteSelected() {
        val next = _elements.value.filterNot { it.id in _selectedIds.value }
        val fallback = next.lastOrNull()?.id
        commit(next, fallback?.let { setOf(it) } ?: emptySet(), fallback)
    }

    fun toggleMirrorHorizontal() = commit(_elements.value.map { if (it.id in _selectedIds.value) it.copy(mirrorHorizontal = !it.mirrorHorizontal) else it })
    fun toggleMirrorVertical() = commit(_elements.value.map { if (it.id in _selectedIds.value) it.copy(mirrorVertical = !it.mirrorVertical) else it })
    fun updateFillColor(hex: Long?) = commit(_elements.value.map { if (it.id in _selectedIds.value) it.copy(fillColorHex = hex) else it })
    fun updateStrokeColor(hex: Long) = commit(_elements.value.map { if (it.id in _selectedIds.value) it.copy(strokeColorHex = hex) else it })
    fun toggleVisibility(id: String) = commit(_elements.value.map { if (it.id == id) it.copy(isVisible = !it.isVisible) else it })
    fun toggleLock(id: String) = commit(_elements.value.map { if (it.id == id) it.copy(isLocked = !it.isLocked) else it })

    fun moveOrder(id: String, forward: Boolean) {
        val list = _elements.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        val dst = if (forward) idx + 1 else idx - 1
        if (idx == -1 || dst !in list.indices) return
        val item = list.removeAt(idx)
        list.add(dst, item)
        commit(list)
    }

    fun updateMatSize(wMm: Float, hMm: Float) {
        _matConfig.value = _matConfig.value.copy(widthMm = wMm.coerceIn(100f, 1600f), heightMm = hMm.coerceIn(100f, 3000f))
    }

    fun toggleSnap() { _matConfig.value = _matConfig.value.copy(snapToGrid = !_matConfig.value.snapToGrid) }
    fun toggleWeeding() { _matConfig.value = _matConfig.value.copy(weedingBoxEnabled = !_matConfig.value.weedingBoxEnabled) }
    fun toggleOptimizeOrder() { _matConfig.value = _matConfig.value.copy(optimizeCutOrder = !_matConfig.value.optimizeCutOrder) }
    fun updateOvercut(mm: Float) { _matConfig.value = _matConfig.value.copy(overcutMm = mm.coerceIn(0f, 1.5f)) }
    fun updateCutColorFilter(hex: Long?) { _matConfig.value = _matConfig.value.copy(cutColorFilterHex = hex) }
    fun selectProtocol(p: PlotterProtocol) { _protocol.value = p }
    fun selectBaudRate(b: Int) { _baudRate.value = b }
    fun selectMaterial(m: MaterialPreset) { _material.value = m }
    fun updateMaterialParams(force: Int? = null, speed: Int? = null) {
        val c = _material.value
        _material.value = c.copy(forceGrams = (force ?: c.forceGrams).coerceIn(10, 450), speedMmPerSec = (speed ?: c.speedMmPerSec).coerceIn(20, 600))
    }
    fun selectTransport(t: DesktopTransportType) { _transportType.value = t }

    fun exportContent(ext: String): String = when (ext.lowercase()) {
        "svg" -> commandGenerator.exportAsSvg(_elements.value, _matConfig.value)
        else -> uiState.value.cutPlan.commands.joinToString("\n")
    }

    fun clearStatusMessage() { _statusMessage.value = null }
}
