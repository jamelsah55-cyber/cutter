package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CutterDatabase
import com.example.data.local.CutterRepository
import com.example.data.usb.BluetoothDeviceUiInfo
import com.example.data.usb.ConnectionTransport
import com.example.data.usb.CutExecutionStatus
import com.example.data.usb.MachineConnectionState
import com.example.data.usb.MachineTransportManager
import com.example.data.usb.SerialConfig
import com.example.data.usb.SerialLogEntry
import com.example.data.usb.UsbDeviceUiInfo
import com.example.domain.model.AlignmentAction
import com.example.domain.model.CutJobRecord
import com.example.domain.model.CuttingMatConfig
import com.example.domain.model.DesignElement
import com.example.domain.model.DesignFontStyle
import com.example.domain.model.MaterialPreset
import com.example.domain.model.NodeRef
import com.example.domain.model.PlotterProtocol
import com.example.domain.model.Point2D
import com.example.domain.model.Polyline2D
import com.example.domain.model.SavedProjectSummary
import com.example.domain.model.VectorBooleanOp
import com.example.domain.model.VectorShapeType
import com.example.domain.usecase.CutExecutionUiState
import com.example.domain.usecase.CutJobOrchestrator
import com.example.domain.usecase.DesignTransformUseCases
import com.example.domain.vector.CutPlan
import com.example.domain.vector.PathEditingEngine
import com.example.domain.vector.PlotterCommandGenerator
import com.example.domain.vector.StarterTemplates
import com.example.domain.vector.SvgPathParser
import com.example.domain.vector.VectorGeometryEngine
import com.example.domain.vector.WorkshopTemplate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

data class NodeEditSession(
    val active: Boolean = false,
    val selectedNode: NodeRef? = null
)

data class SelectionState(
    val primaryId: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false
)

data class CutterUiState(
    val elements: List<DesignElement> = emptyList(),
    val selectedElementId: String? = null,
    val selectedElementIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isFreehandDrawMode: Boolean = false,
    val isWireframeMode: Boolean = false,
    val nodeEditSession: NodeEditSession = NodeEditSession(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val matConfig: CuttingMatConfig = CuttingMatConfig(),
    val serialConfig: SerialConfig = SerialConfig(),
    val materialPresets: List<MaterialPreset> = emptyList(),
    val selectedMaterial: MaterialPreset = MaterialPreset(
        nameAr = "فينيل لاصق لامع",
        categoryAr = "لافتات وإعلانات",
        forceGrams = 95,
        speedMmPerSec = 250
    ),
    val savedProjects: List<SavedProjectSummary> = emptyList(),
    val cutHistory: List<CutJobRecord> = emptyList(),
    val cutPlan: CutPlan = CutPlan(emptyList(), 0f, 0f, 0, emptyList()),
    val isUsbHostSupported: Boolean = true,
    val isBluetoothSupported: Boolean = true,
    val selectedTransport: ConnectionTransport = ConnectionTransport.USB_OTG_CDC,
    val attachedDevices: List<UsbDeviceUiInfo> = emptyList(),
    val pairedBluetoothDevices: List<BluetoothDeviceUiInfo> = emptyList(),
    val connectionState: MachineConnectionState = MachineConnectionState.Disconnected,
    val serialLogs: List<SerialLogEntry> = emptyList(),
    val cutStatus: CutExecutionStatus = CutExecutionStatus.IDLE,
    val cutProgress: Float = 0f,
    val cutSentCommands: Int = 0,
    val cutTotalCommands: Int = 0,
    val simulatedHeadMm: Point2D? = null,
    val darkTheme: Boolean = true,
    val snackbarMessage: String? = null
) {
    val selectedElement: DesignElement?
        get() = elements.firstOrNull { it.id == selectedElementId }

    val selectedElements: List<DesignElement>
        get() = elements.filter { it.id in selectedElementIds }

    val isGroupedSelection: Boolean
        get() = selectedElements.size >= 2 &&
            selectedElements.all { it.groupId != null } &&
            selectedElements.mapNotNull { it.groupId }.distinct().size == 1

    val canGroup: Boolean
        get() = selectedElements.size >= 2 && !isGroupedSelection

    val canUngroup: Boolean
        get() {
            if (selectedElements.any { it.groupId != null }) return true
            val single = selectedElements.singleOrNull() ?: return false
            return single.normalizedPaths.size > 1 ||
                single.type == VectorShapeType.ISLAMIC_STAR ||
                single.type == VectorShapeType.REGISTRATION_MARKS ||
                single.type == VectorShapeType.TEXT
        }

    val canWeld: Boolean
        get() = selectedElements.size >= 2 || (selectedElements.singleOrNull()?.let {
            it.normalizedPaths.size > 1 ||
                it.type == VectorShapeType.TEXT ||
                it.type == VectorShapeType.ISLAMIC_STAR
        } == true)

    val isNodeEditMode: Boolean
        get() = nodeEditSession.active && selectedElement != null && selectedElements.size <= 1

    val selectedNode: NodeRef?
        get() = if (isNodeEditMode) nodeEditSession.selectedNode else null
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val geometryEngine = VectorGeometryEngine(application.applicationContext)
    private val pathEditor = PathEditingEngine(geometryEngine)
    private val commandGenerator = PlotterCommandGenerator(geometryEngine)
    private val designTransforms = DesignTransformUseCases()
    private val repository = CutterRepository(
        application.applicationContext,
        CutterDatabase.getInstance(application.applicationContext)
    )
    private val transportManager = MachineTransportManager(application.applicationContext)
    private val cutOrchestrator = CutJobOrchestrator(
        scope = viewModelScope,
        transportManager = transportManager,
        commandGenerator = commandGenerator,
        repository = repository,
        onMessage = { showMessage(it) }
    )

    private val _elements = MutableStateFlow(StarterTemplates.buildInitialWorkspace(geometryEngine))
    private val _selection = MutableStateFlow(
        _elements.value.firstOrNull()?.let { SelectionState(it.id, setOf(it.id)) } ?: SelectionState()
    )
    private val _nodeEditSession = MutableStateFlow(NodeEditSession())
    private val _canvasFlags = MutableStateFlow(false to false) // first = freehandDraw, second = wireframe
    private val _matConfig = MutableStateFlow(CuttingMatConfig())
    private val _serialConfig = MutableStateFlow(SerialConfig())
    private val _selectedMaterial = MutableStateFlow(
        MaterialPreset(
            id = 1,
            nameAr = "فينيل لاصق لامع",
            categoryAr = "لافتات وإعلانات",
            forceGrams = 95,
            speedMmPerSec = 250,
            passes = 1,
            bladeOffsetMm = 0.25f,
            isBuiltIn = true
        )
    )
    private val _darkTheme = MutableStateFlow(true)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    private val undoStack = ArrayDeque<List<DesignElement>>()
    private val redoStack = ArrayDeque<List<DesignElement>>()
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)

    private var materialInitialized = false
    private var preGestureSnapshot: List<DesignElement>? = null

    val uiState: StateFlow<CutterUiState> = combine(
        _elements,
        _selection,
        _nodeEditSession,
        _matConfig,
        _serialConfig,
        _selectedMaterial,
        repository.materialPresetsFlow.onStart { emit(emptyList()) },
        repository.savedProjectsFlow.onStart { emit(emptyList()) },
        repository.cutJobHistoryFlow.onStart { emit(emptyList()) },
        transportManager.selectedTransport,
        transportManager.attachedUsbDevices,
        transportManager.pairedBluetoothDevices,
        transportManager.connectionState,
        transportManager.serialLogs,
        cutOrchestrator.state,
        _darkTheme,
        _snackbarMessage,
        _canUndo,
        _canRedo,
        _canvasFlags
    ) { values: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val elements = values[0] as List<DesignElement>
        val selection = values[1] as SelectionState
        val nodeSession = values[2] as NodeEditSession
        val matConfig = values[3] as CuttingMatConfig
        val serialConfig = values[4] as SerialConfig
        val selectedMat = values[5] as MaterialPreset
        @Suppress("UNCHECKED_CAST")
        val presets = values[6] as List<MaterialPreset>
        @Suppress("UNCHECKED_CAST")
        val projects = values[7] as List<SavedProjectSummary>
        @Suppress("UNCHECKED_CAST")
        val history = values[8] as List<CutJobRecord>
        val selTransport = values[9] as ConnectionTransport
        @Suppress("UNCHECKED_CAST")
        val usbDevices = values[10] as List<UsbDeviceUiInfo>
        @Suppress("UNCHECKED_CAST")
        val btDevices = values[11] as List<BluetoothDeviceUiInfo>
        val connState = values[12] as MachineConnectionState
        @Suppress("UNCHECKED_CAST")
        val logs = values[13] as List<SerialLogEntry>
        val cutExec = values[14] as CutExecutionUiState
        val dark = values[15] as Boolean
        val snack = values[16] as String?
        val canUndo = values[17] as Boolean
        val canRedo = values[18] as Boolean
        @Suppress("UNCHECKED_CAST")
        val canvasFlags = values[19] as Pair<Boolean, Boolean>

        val plan = commandGenerator.compileCutPlan(
            elements = elements,
            matConfig = matConfig,
            material = selectedMat,
            protocol = serialConfig.protocol,
            selectedIds = selection.selectedIds
        )

        CutterUiState(
            elements = elements,
            selectedElementId = selection.primaryId,
            selectedElementIds = selection.selectedIds,
            isMultiSelectMode = selection.isMultiSelectMode,
            isFreehandDrawMode = canvasFlags.first,
            isWireframeMode = canvasFlags.second,
            nodeEditSession = nodeSession,
            canUndo = canUndo,
            canRedo = canRedo,
            matConfig = matConfig,
            serialConfig = serialConfig,
            materialPresets = presets,
            selectedMaterial = selectedMat,
            savedProjects = projects,
            cutHistory = history,
            cutPlan = plan,
            isUsbHostSupported = transportManager.isUsbHostSupported,
            isBluetoothSupported = transportManager.isBluetoothSupported,
            selectedTransport = selTransport,
            attachedDevices = usbDevices,
            pairedBluetoothDevices = btDevices,
            connectionState = connState,
            serialLogs = logs,
            cutStatus = cutExec.status,
            cutProgress = cutExec.progress,
            cutSentCommands = cutExec.sentCommands,
            cutTotalCommands = cutExec.totalCommands,
            simulatedHeadMm = cutExec.simulatedHeadMm,
            darkTheme = dark,
            snackbarMessage = snack
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CutterUiState(
            elements = _elements.value,
            selectedElementId = _selection.value.primaryId,
            selectedElementIds = _selection.value.selectedIds,
            isUsbHostSupported = transportManager.isUsbHostSupported,
            isBluetoothSupported = transportManager.isBluetoothSupported
        )
    )

    init {
        transportManager.register()
        viewModelScope.launch {
            repository.ensureSeededMaterials()
        }
        viewModelScope.launch {
            repository.preferencesFlow.collect { prefs ->
                _matConfig.value = _matConfig.value.copy(
                    widthMm = prefs.matWidthMm,
                    heightMm = prefs.matHeightMm,
                    gridStepMm = prefs.gridStepMm,
                    snapToGrid = prefs.snapToGrid
                )
                _serialConfig.value = _serialConfig.value.copy(
                    protocol = prefs.defaultProtocol,
                    baudRate = prefs.baudRate,
                    wifiHost = prefs.wifiHost,
                    wifiPort = prefs.wifiPort
                )
                _darkTheme.value = prefs.darkTheme
            }
        }
        viewModelScope.launch {
            repository.materialPresetsFlow.collect { list ->
                if (!materialInitialized && list.isNotEmpty()) {
                    _selectedMaterial.value = list.first()
                    materialInitialized = true
                }
            }
        }
    }

    override fun onCleared() {
        cutOrchestrator.cancelAll()
        transportManager.unregister()
        super.onCleared()
    }

    // --- History / Mutation helpers ---

    private fun pushSnapshotForUndo(snapshot: List<DesignElement>) {
        undoStack.addLast(snapshot)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    private fun resolveGroupIds(elements: List<DesignElement>, targetId: String): Set<String> {
        val el = elements.firstOrNull { it.id == targetId } ?: return emptySet()
        val gid = el.groupId
        return if (gid != null) {
            elements.filter { it.groupId == gid }.map { it.id }.toSet()
        } else {
            setOf(targetId)
        }
    }

    private fun commitElementsChange(
        newList: List<DesignElement>,
        selectId: String? = _selection.value.primaryId
    ) {
        val ids = if (selectId != null) resolveGroupIds(newList, selectId) else emptySet()
        commitElementsChange(newList, ids, selectId)
    }

    private fun commitElementsChange(
        newList: List<DesignElement>,
        selectedIds: Set<String>,
        primaryId: String?
    ) {
        pushSnapshotForUndo(_elements.value)
        _elements.value = newList
        val validIds = selectedIds.filter { id -> newList.any { it.id == id } }.toSet()
        val validPrimary = primaryId?.takeIf { id -> validIds.contains(id) } ?: validIds.lastOrNull()
        _selection.value = _selection.value.copy(
            primaryId = validPrimary,
            selectedIds = validIds,
            isMultiSelectMode = _selection.value.isMultiSelectMode && validIds.size > 1
        )
    }

    private fun commitGestureIfNeeded(finalList: List<DesignElement>) {
        val snap = preGestureSnapshot ?: _elements.value
        preGestureSnapshot = null
        pushSnapshotForUndo(snap)
        _elements.value = finalList
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_elements.value)
        _elements.value = prev
        val validIds = _selection.value.selectedIds.filter { id -> prev.any { it.id == id } }.toSet()
        if (validIds.isEmpty()) {
            val fallback = prev.lastOrNull()?.id
            _selection.value = SelectionState(
                primaryId = fallback,
                selectedIds = fallback?.let { resolveGroupIds(prev, it) } ?: emptySet()
            )
            _nodeEditSession.value = NodeEditSession()
        } else {
            val primary = _selection.value.primaryId?.takeIf { it in validIds } ?: validIds.lastOrNull()
            _selection.value = _selection.value.copy(primaryId = primary, selectedIds = validIds)
        }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_elements.value)
        _elements.value = next
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun selectElement(id: String?) {
        if (id != _selection.value.primaryId && _nodeEditSession.value.active) {
            exitNodeEditMode()
        }
        if (id == null) {
            _selection.value = _selection.value.copy(primaryId = null, selectedIds = emptySet())
            return
        }
        val groupIds = resolveGroupIds(_elements.value, id)
        val cur = _selection.value
        if (cur.isMultiSelectMode) {
            val nextIds = if (groupIds.all { it in cur.selectedIds }) {
                cur.selectedIds - groupIds
            } else {
                cur.selectedIds + groupIds
            }
            val nextPrimary = if (id in nextIds) id else nextIds.lastOrNull()
            _selection.value = cur.copy(primaryId = nextPrimary, selectedIds = nextIds)
        } else {
            _selection.value = cur.copy(primaryId = id, selectedIds = groupIds)
        }
    }

    fun toggleMultiSelectMode() {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val cur = _selection.value
        val nextMulti = !cur.isMultiSelectMode
        if (!nextMulti && cur.selectedIds.size > 1) {
            val primary = cur.primaryId ?: cur.selectedIds.lastOrNull()
            val singleOrGroup = if (primary != null) resolveGroupIds(_elements.value, primary) else emptySet()
            _selection.value = SelectionState(primary, singleOrGroup, isMultiSelectMode = false)
        } else {
            _selection.value = cur.copy(isMultiSelectMode = nextMulti)
        }
    }

    fun selectAllElements() {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val allIds = _elements.value.map { it.id }.toSet()
        _selection.value = SelectionState(
            primaryId = _elements.value.lastOrNull()?.id,
            selectedIds = allIds,
            isMultiSelectMode = allIds.size > 1
        )
    }

    fun addShape(type: VectorShapeType) {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val mat = _matConfig.value
        val w = if (type == VectorShapeType.REGISTRATION_MARKS) 160f else 60f
        val h = if (type == VectorShapeType.REGISTRATION_MARKS) 160f else 60f
        val x = ((mat.widthMm - w) / 2f).coerceAtLeast(10f)
        val y = ((mat.heightMm - h) / 2f).coerceAtLeast(10f)
        val newEl = DesignElement(
            id = UUID.randomUUID().toString(),
            name = type.labelAr,
            type = type,
            xMm = x,
            yMm = y,
            widthMm = w,
            heightMm = h,
            strokeColorHex = if (type == VectorShapeType.ISLAMIC_STAR) 0xFFFF7A2FL else 0xFF00D4B8L
        )
        commitElementsChange(_elements.value + newEl, newEl.id)
    }

    fun addTextElement(text: String, fontStyle: DesignFontStyle, heightMm: Float) {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val clean = text.trim().ifEmpty { "تصميم جديد" }
        val (paths, aspect) = geometryEngine.vectorizeText(clean, fontStyle)
        val clampedH = heightMm.coerceIn(12f, 180f)
        val calcW = (clampedH * aspect).coerceIn(20f, _matConfig.value.widthMm - 10f)
        val x = ((_matConfig.value.widthMm - calcW) / 2f).coerceAtLeast(10f)
        val y = ((_matConfig.value.heightMm - clampedH) / 2f).coerceAtLeast(10f)

        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = "نص: ${clean.take(14)}",
            type = VectorShapeType.TEXT,
            xMm = x,
            yMm = y,
            widthMm = calcW,
            heightMm = clampedH,
            textContent = clean,
            fontStyle = fontStyle,
            normalizedPaths = paths,
            strokeColorHex = 0xFF00D4B8L
        )
        commitElementsChange(_elements.value + el, el.id)
    }

    fun addTracedImageElement(name: String, paths: List<Polyline2D>, aspectRatio: Float) {
        if (paths.isEmpty()) {
            showMessage("لم يتم العثور على حدود واضحة في الصورة. جرب تعديل العتبة.")
            return
        }
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val mat = _matConfig.value
        val maxSide = (minOf(mat.widthMm, mat.heightMm) * 0.45f).coerceIn(40f, 140f)
        val w = if (aspectRatio >= 1f) maxSide else (maxSide * aspectRatio).coerceAtLeast(20f)
        val h = if (aspectRatio >= 1f) (maxSide / aspectRatio).coerceAtLeast(20f) else maxSide
        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "صورة متتبعة" },
            type = VectorShapeType.TRACED_IMAGE,
            xMm = ((mat.widthMm - w) / 2f).coerceAtLeast(5f),
            yMm = ((mat.heightMm - h) / 2f).coerceAtLeast(5f),
            widthMm = w,
            heightMm = h,
            normalizedPaths = paths,
            strokeColorHex = 0xFF00D4B8L
        )
        commitElementsChange(_elements.value + el, el.id)
        showMessage("تم تتبع حدود الصورة وإضافتها (${paths.size} مسار)")
    }

    fun importVectorFile(fileName: String, content: String) {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val parsed = SvgPathParser.parseFileContent(fileName, content)
        if (parsed == null || parsed.normalizedPaths.isEmpty()) {
            showMessage("تعذر قراءة مسارات القص من الملف المحدد. تأكد أنه ملف SVG أو PLT صالح.")
            return
        }
        val mat = _matConfig.value
        val w = parsed.widthMm.coerceAtMost(mat.widthMm - 10f)
        val h = parsed.heightMm.coerceAtMost(mat.heightMm - 10f)
        val el = DesignElement(
            id = UUID.randomUUID().toString(),
            name = fileName.substringBeforeLast('.').take(20),
            type = VectorShapeType.IMPORTED_SVG,
            xMm = ((mat.widthMm - w) / 2f).coerceAtLeast(5f),
            yMm = ((mat.heightMm - h) / 2f).coerceAtLeast(5f),
            widthMm = w,
            heightMm = h,
            normalizedPaths = parsed.normalizedPaths,
            strokeColorHex = 0xFF5CA8FFL
        )
        commitElementsChange(_elements.value + el, el.id)
        showMessage("تم استيراد ${parsed.normalizedPaths.size} مسار قص من $fileName")
    }

    fun moveElementDelta(id: String, dxMm: Float, dyMm: Float, commitSnapshot: Boolean = false) {
        val current = _elements.value
        val curSel = _selection.value
        val targetIds = if (id in curSel.selectedIds) curSel.selectedIds else resolveGroupIds(current, id)
        val targets = current.filter { it.id in targetIds && !it.isLocked }
        if (targets.isEmpty()) return

        if (!commitSnapshot && preGestureSnapshot == null) {
            preGestureSnapshot = current
        }

        val mat = _matConfig.value
        val minAllowedDx = targets.maxOf { -it.xMm }
        val maxAllowedDx = targets.minOf { (mat.widthMm - it.widthMm - it.xMm).coerceAtLeast(0f) }
        val minAllowedDy = targets.maxOf { -it.yMm }
        val maxAllowedDy = targets.minOf { (mat.heightMm - it.heightMm - it.yMm).coerceAtLeast(0f) }

        var appliedDx = if (minAllowedDx <= maxAllowedDx) dxMm.coerceIn(minAllowedDx, maxAllowedDx) else 0f
        var appliedDy = if (minAllowedDy <= maxAllowedDy) dyMm.coerceIn(minAllowedDy, maxAllowedDy) else 0f

        if (commitSnapshot && mat.snapToGrid) {
            val anchor = targets.first()
            val snappedX = snapValue(anchor.xMm + appliedDx, mat.gridStepMm)
                .coerceIn(0f, (mat.widthMm - anchor.widthMm).coerceAtLeast(0f))
            val snappedY = snapValue(anchor.yMm + appliedDy, mat.gridStepMm)
                .coerceIn(0f, (mat.heightMm - anchor.heightMm).coerceAtLeast(0f))
            appliedDx = snappedX - anchor.xMm
            appliedDy = snappedY - anchor.yMm
        }

        val updated = current.map { el ->
            if (el.id in targetIds && !el.isLocked) {
                el.copy(
                    xMm = (el.xMm + appliedDx).coerceIn(0f, (mat.widthMm - el.widthMm).coerceAtLeast(0f)),
                    yMm = (el.yMm + appliedDy).coerceIn(0f, (mat.heightMm - el.heightMm).coerceAtLeast(0f))
                )
            } else el
        }
        if (commitSnapshot) {
            commitGestureIfNeeded(updated)
        } else {
            _elements.value = updated
        }
    }

    fun resizeElementBounds(
        id: String,
        newXMm: Float,
        newYMm: Float,
        newWidthMm: Float,
        newHeightMm: Float,
        commitSnapshot: Boolean = false
    ) {
        val current = _elements.value
        val curSel = _selection.value
        val selectedList = current.filter { it.id in curSel.selectedIds && !it.isLocked }
        if (selectedList.size > 1 && id in curSel.selectedIds) {
            resizeSelectionBounds(newXMm, newYMm, newWidthMm, newHeightMm, commitSnapshot)
            return
        }

        val target = current.firstOrNull { it.id == id } ?: return
        if (target.isLocked) return

        if (!commitSnapshot && preGestureSnapshot == null) {
            preGestureSnapshot = current
        }

        val mat = _matConfig.value
        val clampedW = newWidthMm.coerceIn(8f, mat.widthMm)
        val clampedH = newHeightMm.coerceIn(8f, mat.heightMm)
        val clampedX = newXMm.coerceIn(0f, (mat.widthMm - clampedW).coerceAtLeast(0f))
        val clampedY = newYMm.coerceIn(0f, (mat.heightMm - clampedH).coerceAtLeast(0f))

        val finalW = if (commitSnapshot && mat.snapToGrid) snapValue(clampedW, 1f) else clampedW
        val finalH = if (commitSnapshot && mat.snapToGrid) snapValue(clampedH, 1f) else clampedH

        val updated = current.map {
            if (it.id == id) {
                it.copy(
                    xMm = clampedX,
                    yMm = clampedY,
                    widthMm = finalW.coerceAtLeast(8f),
                    heightMm = finalH.coerceAtLeast(8f)
                )
            } else it
        }
        if (commitSnapshot) {
            commitGestureIfNeeded(updated)
        } else {
            _elements.value = updated
        }
    }

    fun resizeSelectionBounds(
        newXMm: Float,
        newYMm: Float,
        newWidthMm: Float,
        newHeightMm: Float,
        commitSnapshot: Boolean = false
    ) {
        val current = _elements.value
        val selIds = _selection.value.selectedIds
        val targets = current.filter { it.id in selIds && !it.isLocked }
        if (targets.isEmpty()) return
        if (targets.size == 1) {
            resizeElementBounds(targets.first().id, newXMm, newYMm, newWidthMm, newHeightMm, commitSnapshot)
            return
        }

        if (!commitSnapshot && preGestureSnapshot == null) {
            preGestureSnapshot = current
        }

        val oldMinX = targets.minOf { it.xMm }
        val oldMinY = targets.minOf { it.yMm }
        val oldMaxX = targets.maxOf { it.xMm + it.widthMm }
        val oldMaxY = targets.maxOf { it.yMm + it.heightMm }
        val oldW = (oldMaxX - oldMinX).coerceAtLeast(1f)
        val oldH = (oldMaxY - oldMinY).coerceAtLeast(1f)

        val mat = _matConfig.value
        val targetW = newWidthMm.coerceIn(12f, mat.widthMm)
        val targetH = newHeightMm.coerceIn(12f, mat.heightMm)
        val targetX = newXMm.coerceIn(0f, (mat.widthMm - targetW).coerceAtLeast(0f))
        val targetY = newYMm.coerceIn(0f, (mat.heightMm - targetH).coerceAtLeast(0f))

        val sx = targetW / oldW
        val sy = targetH / oldH

        val updated = current.map { el ->
            if (el.id in selIds && !el.isLocked) {
                val nx = targetX + (el.xMm - oldMinX) * sx
                val ny = targetY + (el.yMm - oldMinY) * sy
                val nw = (el.widthMm * sx).coerceAtLeast(5f)
                val nh = (el.heightMm * sy).coerceAtLeast(5f)
                el.copy(
                    xMm = nx.coerceIn(0f, (mat.widthMm - nw).coerceAtLeast(0f)),
                    yMm = ny.coerceIn(0f, (mat.heightMm - nh).coerceAtLeast(0f)),
                    widthMm = nw,
                    heightMm = nh
                )
            } else el
        }
        if (commitSnapshot) {
            commitGestureIfNeeded(updated)
        } else {
            _elements.value = updated
        }
    }

    fun rotateElementAbsolute(id: String, rotationDeg: Float, commitSnapshot: Boolean = false) {
        val current = _elements.value
        val target = current.firstOrNull { it.id == id } ?: return
        if (target.isLocked) return

        if (!commitSnapshot && preGestureSnapshot == null) {
            preGestureSnapshot = current
        }

        val normalizedDeg = ((rotationDeg % 360f) + 360f) % 360f
        val updated = current.map {
            if (it.id == id) it.copy(rotationDeg = normalizedDeg) else it
        }
        if (commitSnapshot) {
            commitGestureIfNeeded(updated)
        } else {
            _elements.value = updated
        }
    }

    // --- Node Editing Mode ---

    fun enterNodeEditMode() {
        val selected = uiState.value.selectedElement ?: return
        val baked = geometryEngine.bakeToEditablePaths(selected)
        if (baked.normalizedPaths.isEmpty()) {
            showMessage("هذا العنصر لا يحتوي على عقد قابلة للتعديل")
            return
        }
        if (baked != selected) {
            val updated = _elements.value.map { if (it.id == baked.id) baked else it }
            commitElementsChange(updated, baked.id)
        }
        _nodeEditSession.value = NodeEditSession(
            active = true,
            selectedNode = NodeRef(0, 0)
        )
    }

    fun exitNodeEditMode() {
        val selected = uiState.value.selectedElement
        if (selected != null) {
            val tidied = geometryEngine.renormalizeBoundsIfUnrotated(selected)
            if (tidied != selected) {
                _elements.value = _elements.value.map { if (it.id == tidied.id) tidied else it }
            }
        }
        _nodeEditSession.value = NodeEditSession(active = false, selectedNode = null)
    }

    fun selectNode(nodeRef: NodeRef?) {
        _nodeEditSession.value = _nodeEditSession.value.copy(selectedNode = nodeRef)
    }

    fun moveNodeDelta(
        polylineIndex: Int,
        pointIndex: Int,
        worldDxMm: Float,
        worldDyMm: Float,
        commitSnapshot: Boolean = false
    ) {
        val current = _elements.value
        val selected = current.firstOrNull { it.id == _selection.value.primaryId } ?: return
        if (selected.isLocked) return

        if (!commitSnapshot && preGestureSnapshot == null) {
            preGestureSnapshot = current
        }

        val normDelta = geometryEngine.worldDeltaToNormalizedDelta(selected, worldDxMm, worldDyMm)
        val updatedPaths = selected.normalizedPaths.mapIndexed { pIdx, poly ->
            if (pIdx != polylineIndex) {
                poly
            } else {
                val updatedPts = poly.points.mapIndexed { idx, pt ->
                    if (idx == pointIndex) {
                        Point2D(pt.xMm + normDelta.xMm, pt.yMm + normDelta.yMm)
                    } else pt
                }
                poly.copy(points = updatedPts)
            }
        }
        val updated = current.map {
            if (it.id == selected.id) it.copy(normalizedPaths = updatedPaths) else it
        }
        if (commitSnapshot) {
            commitGestureIfNeeded(updated)
        } else {
            _elements.value = updated
        }
    }

    fun insertNodeAfterSelected() {
        val selected = uiState.value.selectedElement ?: return
        val ref = _nodeEditSession.value.selectedNode ?: NodeRef(0, 0)
        val poly = selected.normalizedPaths.getOrNull(ref.polylineIndex) ?: return
        if (poly.points.size < 2) return

        val i0 = ref.pointIndex.coerceIn(0, poly.points.lastIndex)
        val i1 = if (i0 == poly.points.lastIndex) {
            if (poly.closed) 0 else i0 - 1
        } else i0 + 1
        val p0 = poly.points[i0]
        val p1 = poly.points[i1]
        val mid = Point2D((p0.xMm + p1.xMm) / 2f, (p0.yMm + p1.yMm) / 2f)

        val insertAt = if (i0 == poly.points.lastIndex && !poly.closed) i0 else i0 + 1
        val newPts = poly.points.toMutableList().apply { add(insertAt, mid) }
        val newPaths = selected.normalizedPaths.mapIndexed { idx, pl ->
            if (idx == ref.polylineIndex) pl.copy(points = newPts) else pl
        }
        commitElementsChange(
            _elements.value.map { if (it.id == selected.id) it.copy(normalizedPaths = newPaths) else it },
            selected.id
        )
        _nodeEditSession.value = _nodeEditSession.value.copy(
            selectedNode = NodeRef(ref.polylineIndex, insertAt)
        )
    }

    fun deleteSelectedNode() {
        val selected = uiState.value.selectedElement ?: return
        val ref = _nodeEditSession.value.selectedNode ?: return
        val poly = selected.normalizedPaths.getOrNull(ref.polylineIndex) ?: return
        val minPts = if (poly.closed) 3 else 2
        if (poly.points.size <= minPts) {
            showMessage("لا يمكن تقليل المسار لأقل من $minPts نقاط")
            return
        }
        val rmIdx = ref.pointIndex.coerceIn(0, poly.points.lastIndex)
        val newPts = poly.points.toMutableList().apply { removeAt(rmIdx) }
        val newPaths = selected.normalizedPaths.mapIndexed { idx, pl ->
            if (idx == ref.polylineIndex) pl.copy(points = newPts) else pl
        }
        commitElementsChange(
            _elements.value.map { if (it.id == selected.id) it.copy(normalizedPaths = newPaths) else it },
            selected.id
        )
        _nodeEditSession.value = _nodeEditSession.value.copy(
            selectedNode = NodeRef(ref.polylineIndex, rmIdx.coerceAtMost(newPts.lastIndex))
        )
    }

    fun toggleSelectedPolylineClosed() {
        val selected = uiState.value.selectedElement ?: return
        val ref = _nodeEditSession.value.selectedNode ?: NodeRef(0, 0)
        val targetPoly = selected.normalizedPaths.getOrNull(ref.polylineIndex) ?: return
        if (!targetPoly.closed) {
            val (updatedEl, closedCount) = pathEditor.closeOpenCurves(selected, ref.polylineIndex)
            if (closedCount > 0) {
                commitElementsChange(
                    _elements.value.map { if (it.id == selected.id) updatedEl else it },
                    selected.id
                )
                showMessage("تم إغلاق المنحنى بنجاح")
            }
        } else {
            val newPaths = selected.normalizedPaths.mapIndexed { idx, pl ->
                if (idx == ref.polylineIndex) pl.copy(closed = false) else pl
            }
            commitElementsChange(
                _elements.value.map { if (it.id == selected.id) it.copy(normalizedPaths = newPaths) else it },
                selected.id
            )
            showMessage("تم فتح المسار")
        }
    }

    fun toggleFreehandDrawMode() {
        val next = !_canvasFlags.value.first
        if (next && _nodeEditSession.value.active) {
            exitNodeEditMode()
        }
        _canvasFlags.value = next to _canvasFlags.value.second
    }

    fun toggleWireframeMode() {
        val cur = _canvasFlags.value
        _canvasFlags.value = cur.first to !cur.second
    }

    fun commitFreehandStroke(worldPointsMm: List<Point2D>) {
        val el = pathEditor.buildFreehandElement(worldPointsMm)
        _canvasFlags.value = false to _canvasFlags.value.second
        if (el == null) {
            showMessage("ارسم خطاً أطول لإنشاء مسار قص")
            return
        }
        commitElementsChange(_elements.value + el, el.id)
        val count = el.normalizedPaths.sumOf { it.points.size }
        val stateLabel = if (el.normalizedPaths.firstOrNull()?.closed == true) "مغلق" else "مفتوح"
        showMessage("تم إضافة رسم حر ($stateLabel · $count عقدة)")
    }

    fun previewSelectionPathStats(elements: List<DesignElement>): Pair<Int, Int> {
        var total = 0
        var openCount = 0
        elements.forEach { el ->
            val (t, o) = pathEditor.inspectStats(el)
            total += t
            openCount += o
        }
        return total to openCount
    }

    fun previewSimplifiedNodeCount(elements: List<DesignElement>, toleranceMm: Float): Int {
        return elements.sumOf { el ->
            pathEditor.simplifyElement(el, toleranceMm).nodesAfter
        }
    }

    fun simplifySelectedPaths(toleranceMm: Float = 0.6f) {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val activePolyIdx = if (uiState.value.isNodeEditMode) {
            _nodeEditSession.value.selectedNode?.polylineIndex
        } else null

        var totalBefore = 0
        var totalAfter = 0
        val updated = _elements.value.map { el ->
            if (el.id in selIds) {
                val res = pathEditor.simplifyElement(el, toleranceMm, activePolyIdx)
                totalBefore += res.nodesBefore
                totalAfter += res.nodesAfter
                res.element
            } else el
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
        clampNodeSelectionAfterEdit(updated)
        showMessage("تم تبسيط العقد من $totalBefore إلى $totalAfter نقطة")
    }

    fun smoothSelectedCurves() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val activePolyIdx = if (uiState.value.isNodeEditMode) {
            _nodeEditSession.value.selectedNode?.polylineIndex
        } else null

        val updated = _elements.value.map { el ->
            if (el.id in selIds) {
                pathEditor.smoothElement(el, activePolyIdx).first
            } else el
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
        clampNodeSelectionAfterEdit(updated)
        showMessage("تم تنعيم زوايا المنحنيات لقص أكثر سلاسة")
    }

    fun closeSelectedOpenCurves() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val activePolyIdx = if (uiState.value.isNodeEditMode) {
            _nodeEditSession.value.selectedNode?.polylineIndex
        } else null

        var closedTotal = 0
        val updated = _elements.value.map { el ->
            if (el.id in selIds) {
                val (resEl, cnt) = pathEditor.closeOpenCurves(el, activePolyIdx)
                closedTotal += cnt
                resEl
            } else el
        }
        if (closedTotal == 0) {
            showMessage("جميع المنحنيات المحددة مغلقة بالفعل")
            return
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
        clampNodeSelectionAfterEdit(updated)
        showMessage("تم إغلاق $closedTotal منحنى مفتوح")
    }

    fun createContourOffsetForSelected(offsetMm: Float) {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val source = uiState.value.selectedElement ?: return
        val offsetEl = pathEditor.createContourOffsetElement(source, offsetMm)
        if (offsetEl == null) {
            showMessage("تعذر توليد حد خارجي لهذا العنصر")
            return
        }
        val idx = _elements.value.indexOfFirst { it.id == source.id }.coerceAtLeast(0)
        val next = _elements.value.toMutableList().apply { add(idx, offsetEl) }
        commitElementsChange(next, setOf(offsetEl.id), offsetEl.id)
        showMessage("تم إنشاء حد خارجي للملصق (+${offsetMm.roundToInt()} مم)")
    }

    fun toggleVerticalFlipSelected() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val updated = _elements.value.map {
            if (it.id in selIds) it.copy(mirrorVertical = !it.mirrorVertical) else it
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
    }

    private fun clampNodeSelectionAfterEdit(elements: List<DesignElement>) {
        if (!_nodeEditSession.value.active) return
        val ref = _nodeEditSession.value.selectedNode ?: return
        val primary = elements.firstOrNull { it.id == _selection.value.primaryId } ?: return
        val pIdx = ref.polylineIndex.coerceIn(0, (primary.normalizedPaths.size - 1).coerceAtLeast(0))
        val ptsSize = primary.normalizedPaths.getOrNull(pIdx)?.points?.size ?: 0
        if (ptsSize == 0) {
            _nodeEditSession.value = _nodeEditSession.value.copy(selectedNode = null)
        } else {
            _nodeEditSession.value = _nodeEditSession.value.copy(
                selectedNode = NodeRef(pIdx, ref.pointIndex.coerceIn(0, ptsSize - 1))
            )
        }
    }

    fun snapSelectedToGrid() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val mat = _matConfig.value
        val updated = _elements.value.map {
            if (it.id in selIds && !it.isLocked) {
                it.copy(
                    xMm = snapValue(it.xMm, mat.gridStepMm),
                    yMm = snapValue(it.yMm, mat.gridStepMm)
                )
            } else it
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
    }

    fun updateSelectedElement(transform: (DesignElement) -> DesignElement) {
        val id = _selection.value.primaryId ?: return
        val updated = _elements.value.map { el ->
            if (el.id == id) {
                val mod = transform(el)
                if (mod.type == VectorShapeType.TEXT &&
                    (mod.textContent != el.textContent || mod.fontStyle != el.fontStyle)
                ) {
                    val (paths, _) = geometryEngine.vectorizeText(mod.textContent, mod.fontStyle)
                    mod.copy(normalizedPaths = paths, name = "نص: ${mod.textContent.take(14)}")
                } else {
                    mod
                }
            } else el
        }
        commitElementsChange(updated, _selection.value.selectedIds, id)
    }

    fun toggleMirrorSelected() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val updated = _elements.value.map {
            if (it.id in selIds) it.copy(mirrorHorizontal = !it.mirrorHorizontal) else it
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
    }

    fun rotateSelectedBy(deltaDeg: Float) {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val updated = _elements.value.map {
            if (it.id in selIds) it.copy(rotationDeg = (it.rotationDeg + deltaDeg) % 360f) else it
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
    }

    fun duplicateSelected() {
        val selIds = _selection.value.selectedIds
        val targets = _elements.value.filter { it.id in selIds }
        if (targets.isEmpty()) return
        val mat = _matConfig.value
        val newGroupId = if (targets.size > 1 && targets.all { it.groupId != null }) {
            UUID.randomUUID().toString()
        } else null
        val copies = targets.map { el ->
            el.copy(
                id = UUID.randomUUID().toString(),
                name = "${el.name} (نسخة)",
                xMm = (el.xMm + 15f).coerceAtMost((mat.widthMm - el.widthMm).coerceAtLeast(0f)),
                yMm = (el.yMm + 15f).coerceAtMost((mat.heightMm - el.heightMm).coerceAtLeast(0f)),
                groupId = newGroupId
            )
        }
        val copyIds = copies.map { it.id }.toSet()
        commitElementsChange(_elements.value + copies, copyIds, copies.first().id)
    }

    fun deleteSelected() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val next = _elements.value.filterNot { it.id in selIds }
        val fallbackId = next.lastOrNull()?.id
        commitElementsChange(
            next,
            fallbackId?.let { resolveGroupIds(next, it) } ?: emptySet(),
            fallbackId
        )
    }

    fun groupSelectedElements() {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val selIds = _selection.value.selectedIds
        val targets = _elements.value.filter { it.id in selIds }
        if (targets.size < 2) {
            showMessage("حدد عنصرين على الأقل لتنفيذ التجميع")
            return
        }
        val newGroupId = UUID.randomUUID().toString()
        val updated = _elements.value.map { el ->
            if (el.id in selIds) el.copy(groupId = newGroupId) else el
        }
        commitElementsChange(updated, selIds, targets.first().id)
        showMessage("تم تجميع ${targets.size} عناصر في مجموعة واحدة")
    }

    fun ungroupSelectedElements() {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val selIds = _selection.value.selectedIds
        val targets = _elements.value.filter { it.id in selIds }
        if (targets.isEmpty()) return

        if (targets.any { it.groupId != null }) {
            val groupIds = targets.mapNotNull { it.groupId }.toSet()
            val updated = _elements.value.map { el ->
                if (el.id in selIds || el.groupId in groupIds) el.copy(groupId = null) else el
            }
            val remainingIds = updated.filter { it.id in selIds }.map { it.id }.toSet()
            commitElementsChange(updated, remainingIds, targets.first().id)
            showMessage("تم تفكيك المجموعة إلى عناصر مستقلة")
            return
        }

        if (targets.size == 1) {
            val single = targets.first()
            val broken = geometryEngine.breakApartElement(single)
            if (broken.size > 1) {
                val idx = _elements.value.indexOfFirst { it.id == single.id }
                val next = _elements.value.toMutableList()
                next.removeAt(idx)
                next.addAll(idx, broken)
                val brokenIds = broken.map { it.id }.toSet()
                commitElementsChange(next, brokenIds, broken.first().id)
                _selection.value = _selection.value.copy(isMultiSelectMode = true)
                showMessage("تم تفكيك المسار إلى ${broken.size} أجزاء مستقلة")
                return
            }
        }
        showMessage("لا توجد مجموعة أو مسارات متعددة لتفكيكها")
    }

    fun weldSelectedElements() = applyBooleanOp(VectorBooleanOp.UNION)

    fun applyBooleanOp(op: VectorBooleanOp) {
        if (_nodeEditSession.value.active) exitNodeEditMode()
        val selIds = _selection.value.selectedIds
        val targets = _elements.value.filter { it.id in selIds }
        if (targets.isEmpty()) return

        val result = geometryEngine.booleanOpElements(targets, op)
        if (result == null) {
            showMessage("تعذر تنفيذ العملية الهندسية على العناصر المحددة")
            return
        }
        val firstIndex = _elements.value.indexOfFirst { it.id in selIds }.coerceAtLeast(0)
        val remaining = _elements.value.filterNot { it.id in selIds }.toMutableList()
        remaining.add(firstIndex.coerceAtMost(remaining.size), result)
        commitElementsChange(remaining, setOf(result.id), result.id)
        val msg = when (op) {
            VectorBooleanOp.UNION -> "تم لحام العناصر في مسار قص موحد"
            VectorBooleanOp.DIFFERENCE -> "تم تفريغ الشكل العلوي من العنصر الأساسي"
            VectorBooleanOp.INTERSECT -> "تم استخراج منطقة التقاطع بين العناصر"
        }
        showMessage(msg)
    }

    fun toggleElementVisibility(id: String) {
        val updated = _elements.value.map { el ->
            if (el.id == id) el.copy(isVisible = !el.isVisible) else el
        }
        commitElementsChange(updated, _selection.value.selectedIds, _selection.value.primaryId)
    }

    fun toggleElementLockById(id: String) {
        val updated = _elements.value.map { el ->
            if (el.id == id) el.copy(isLocked = !el.isLocked) else el
        }
        commitElementsChange(updated, _selection.value.selectedIds, _selection.value.primaryId)
    }

    fun moveElementOrderById(id: String, moveForward: Boolean) {
        val list = _elements.value.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx == -1) return
        val targetIdx = if (moveForward) idx + 1 else idx - 1
        if (targetIdx !in list.indices) return
        val item = list.removeAt(idx)
        list.add(targetIdx, item)
        commitElementsChange(list, _selection.value.selectedIds, _selection.value.primaryId)
    }

    fun clearCanvas() {
        if (_elements.value.isNotEmpty()) {
            commitElementsChange(emptyList(), null)
        }
    }

    fun alignSelection(action: AlignmentAction) {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val updated = designTransforms.alignSelection(_elements.value, selIds, _matConfig.value, action)
        commitElementsChange(updated, selIds, _selection.value.primaryId)
        showMessage("تم تنفيذ: ${action.labelAr}")
    }

    fun bringSelectedToFront() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        commitElementsChange(
            designTransforms.bringSelectionToFront(_elements.value, selIds),
            selIds,
            _selection.value.primaryId
        )
    }

    fun sendSelectedToBack() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        commitElementsChange(
            designTransforms.sendSelectionToBack(_elements.value, selIds),
            selIds,
            _selection.value.primaryId
        )
    }

    fun toggleLockSelected() {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val (updated, locked) = designTransforms.toggleLockSelection(_elements.value, selIds)
        commitElementsChange(updated, selIds, _selection.value.primaryId)
        showMessage(if (locked) "تم قفل العنصر لمنع التحريك غير المقصود" else "تم فك قفل العنصر")
    }

    fun updateSelectionFillColor(fillHex: Long?) {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val updated = _elements.value.map { el ->
            if (el.id in selIds) el.copy(fillColorHex = fillHex) else el
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
    }

    fun updateSelectionStrokeColor(strokeHex: Long) {
        val selIds = _selection.value.selectedIds
        if (selIds.isEmpty()) return
        val updated = _elements.value.map { el ->
            if (el.id in selIds) el.copy(strokeColorHex = strokeHex) else el
        }
        commitElementsChange(updated, selIds, _selection.value.primaryId)
    }

    fun generateArrayClone(rows: Int, cols: Int, gapXMm: Float, gapYMm: Float) {
        val source = uiState.value.selectedElement ?: return
        val copies = designTransforms.generateArrayCopies(
            source = source,
            rows = rows,
            cols = cols,
            gapXMm = gapXMm,
            gapYMm = gapYMm,
            matConfig = _matConfig.value
        )
        if (copies.isEmpty()) {
            showMessage("المساحة المتبقية على الحصيرة لا تكفي لنسخ إضافية بهذه المقاسات")
            return
        }
        val allNewIds = setOf(source.id) + copies.map { it.id }
        commitElementsChange(_elements.value + copies, allNewIds, source.id)
        showMessage("تم إنشاء مصفوفة (${copies.size + 1} ملصق إجمالاً)")
    }

    fun toggleSnapToGrid() {
        val next = !_matConfig.value.snapToGrid
        _matConfig.value = _matConfig.value.copy(snapToGrid = next)
        viewModelScope.launch { repository.updateSnapToGrid(next) }
    }

    fun toggleWeedingBox() {
        val next = !_matConfig.value.weedingBoxEnabled
        _matConfig.value = _matConfig.value.copy(weedingBoxEnabled = next)
    }

    fun toggleOptimizeCutOrder() {
        _matConfig.value = _matConfig.value.copy(optimizeCutOrder = !_matConfig.value.optimizeCutOrder)
    }

    fun updateOvercutMm(overcutMm: Float) {
        _matConfig.value = _matConfig.value.copy(overcutMm = overcutMm.coerceIn(0f, 1.5f))
    }

    fun toggleCutOnlySelected() {
        _matConfig.value = _matConfig.value.copy(cutOnlySelected = !_matConfig.value.cutOnlySelected)
    }

    fun updateCutColorFilter(colorHex: Long?) {
        _matConfig.value = _matConfig.value.copy(cutColorFilterHex = colorHex)
    }

    fun loadTemplate(template: WorkshopTemplate) {
        val built = template.buildElements(geometryEngine)
        commitElementsChange(built, built.firstOrNull()?.id)
        showMessage("تم تحميل قالب: ${template.titleAr}")
    }

    fun saveCurrentProject(title: String) {
        viewModelScope.launch {
            repository.saveProject(title, _elements.value, _matConfig.value)
            showMessage("تم حفظ المشروع في المكتبة")
        }
    }

    fun loadSavedProject(project: SavedProjectSummary) {
        val parsed = repository.deserializeElements(project.elementsJson)
        _matConfig.value = _matConfig.value.copy(
            widthMm = project.matWidthMm,
            heightMm = project.matHeightMm
        )
        commitElementsChange(parsed, parsed.firstOrNull()?.id)
        showMessage("تم فتح المشروع: ${project.title}")
    }

    fun deleteSavedProject(id: Long) {
        viewModelScope.launch { repository.deleteProject(id) }
    }

    // --- Machine / Material / USB OTG Controls ---

    fun selectMaterialPreset(preset: MaterialPreset) {
        _selectedMaterial.value = preset
    }

    fun updateActiveCutParameters(forceGrams: Int? = null, speedMmSec: Int? = null, passes: Int? = null) {
        val cur = _selectedMaterial.value
        _selectedMaterial.value = cur.copy(
            forceGrams = (forceGrams ?: cur.forceGrams).coerceIn(10, 500),
            speedMmPerSec = (speedMmSec ?: cur.speedMmPerSec).coerceIn(20, 800),
            passes = (passes ?: cur.passes).coerceIn(1, 5)
        )
    }

    fun saveCustomMaterialPreset(nameAr: String, categoryAr: String, force: Int, speed: Int, passes: Int) {
        viewModelScope.launch {
            repository.saveMaterialPreset(
                MaterialPreset(
                    nameAr = nameAr.ifBlank { "خامة مخصصة" },
                    categoryAr = categoryAr.ifBlank { "إعداد شخصي" },
                    forceGrams = force.coerceIn(10, 500),
                    speedMmPerSec = speed.coerceIn(20, 800),
                    passes = passes.coerceIn(1, 5),
                    bladeOffsetMm = 0.25f,
                    isBuiltIn = false
                )
            )
            showMessage("تم حفظ إعداد الخامة الجديد")
        }
    }

    fun deleteCustomMaterial(id: Long) {
        viewModelScope.launch { repository.deleteCustomMaterial(id) }
    }

    fun updateProtocol(protocol: PlotterProtocol) {
        val next = _serialConfig.value.copy(protocol = protocol)
        _serialConfig.value = next
        viewModelScope.launch { repository.updateSerialDefaults(protocol, next.baudRate) }
    }

    fun updateBaudRate(baud: Int) {
        val next = _serialConfig.value.copy(baudRate = baud)
        _serialConfig.value = next
        viewModelScope.launch { repository.updateSerialDefaults(next.protocol, baud) }
    }

    fun updatePacketDelay(delayMs: Long) {
        _serialConfig.value = _serialConfig.value.copy(packetDelayMs = delayMs.coerceIn(2L, 100L))
    }

    fun updateMatDimensions(widthMm: Float, heightMm: Float) {
        val w = widthMm.coerceIn(100f, 1600f)
        val h = heightMm.coerceIn(100f, 3000f)
        _matConfig.value = _matConfig.value.copy(widthMm = w, heightMm = h)
        viewModelScope.launch { repository.updateMatDimensions(w, h) }
    }

    fun toggleDarkTheme() {
        val next = !_darkTheme.value
        _darkTheme.value = next
        viewModelScope.launch { repository.updateDarkTheme(next) }
    }

    fun selectTransport(transport: ConnectionTransport) {
        transportManager.selectTransport(transport)
    }

    fun refreshUsbDevices() {
        transportManager.refreshUsbDevices()
    }

    fun connectUsbDevice(deviceId: Int) {
        transportManager.connectUsbDevice(deviceId, _serialConfig.value.baudRate)
    }

    fun connectWifi(hostIp: String, port: Int) {
        val cleanIp = hostIp.trim()
        val validPort = port.coerceIn(1, 65535)
        _serialConfig.value = _serialConfig.value.copy(wifiHost = cleanIp, wifiPort = validPort)
        viewModelScope.launch { repository.updateWifiDefaults(cleanIp, validPort) }
        transportManager.connectWifi(cleanIp, validPort)
    }

    fun refreshBluetoothDevices() {
        transportManager.refreshBluetoothDevices()
    }

    fun hasBluetoothPermissions(): Boolean = transportManager.hasBluetoothPermissions()

    fun connectBluetoothDevice(address: String) {
        transportManager.connectBluetoothDevice(address)
    }

    fun disconnectMachine() {
        emergencyStop()
        transportManager.disconnectAll("تم قطع الاتصال يدوياً")
    }

    fun clearSerialLogs() {
        transportManager.clearLogs()
    }

    fun sendJog(dxMm: Float, dyMm: Float) {
        if (transportManager.connectionState.value !is MachineConnectionState.Connected) {
            showMessage("يرجى ربط الماكينة أولاً عبر USB أو Wi-Fi أو Bluetooth")
            return
        }
        val cmds = commandGenerator.buildJogCommand(_serialConfig.value.protocol, dxMm, dyMm)
        viewModelScope.launch {
            cmds.forEach { transportManager.sendCommand(it) }
        }
    }

    fun sendHome() {
        if (transportManager.connectionState.value !is MachineConnectionState.Connected) {
            showMessage("يرجى ربط الماكينة أولاً عبر USB أو Wi-Fi أو Bluetooth")
            return
        }
        val cmds = commandGenerator.buildHomeCommand(_serialConfig.value.protocol)
        viewModelScope.launch { cmds.forEach { transportManager.sendCommand(it) } }
    }

    fun sendSetOrigin() {
        if (transportManager.connectionState.value !is MachineConnectionState.Connected) {
            showMessage("يرجى ربط الماكينة أولاً عبر USB أو Wi-Fi أو Bluetooth")
            return
        }
        val cmds = commandGenerator.buildSetOriginCommand(_serialConfig.value.protocol)
        viewModelScope.launch {
            cmds.forEach { transportManager.sendCommand(it) }
            showMessage("تم تعيين نقطة الأصل (0,0) في الماكينة")
        }
    }

    fun sendTestSquare() {
        if (transportManager.connectionState.value !is MachineConnectionState.Connected) {
            showMessage("يرجى ربط الماكينة أولاً لإجراء قص الاختبار")
            return
        }
        val cmds = commandGenerator.buildTestSquareCommand(_serialConfig.value.protocol, _selectedMaterial.value)
        viewModelScope.launch {
            cmds.forEach {
                transportManager.sendCommand(it)
                delay(15)
            }
            showMessage("تم إرسال مربع الاختبار (20×20 مم)")
        }
    }

    fun startRealOtgCut() {
        val snapshot = uiState.value
        val activeCount = if (snapshot.matConfig.cutOnlySelected && snapshot.selectedElementIds.isNotEmpty()) {
            snapshot.selectedElements.size
        } else {
            snapshot.elements.size
        }
        cutOrchestrator.startRealCut(
            plan = snapshot.cutPlan,
            jobTitle = snapshot.elements.firstOrNull()?.name ?: "مشروع قص",
            elementCount = activeCount,
            protocol = _serialConfig.value.protocol,
            material = _selectedMaterial.value,
            packetDelayMs = _serialConfig.value.packetDelayMs
        )
    }

    fun sendFrameBoundingBoxTest() {
        cutOrchestrator.sendFrameBoundingBoxTest(
            plan = uiState.value.cutPlan,
            protocol = _serialConfig.value.protocol
        )
    }

    fun startVisualDryRun() {
        cutOrchestrator.startVisualDryRun(uiState.value.cutPlan)
    }

    fun togglePauseCut() {
        cutOrchestrator.togglePause()
    }

    fun emergencyStop() {
        cutOrchestrator.emergencyStop(_serialConfig.value.protocol)
    }

    fun buildExportContent(format: String): String {
        return when (format.lowercase()) {
            "svg" -> commandGenerator.exportAsSvg(_elements.value, _matConfig.value)
            else -> uiState.value.cutPlan.commands.joinToString("\n")
        }
    }

    fun clearCutHistory() {
        viewModelScope.launch { repository.clearCutHistory() }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun dismissMessage() {
        _snackbarMessage.value = null
    }

    private fun snapValue(v: Float, step: Float): Float {
        if (step <= 0f) return v
        return ((v / step).roundToInt()) * step
    }
}
