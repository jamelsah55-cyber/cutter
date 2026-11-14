package com.example.domain.usecase

import com.example.data.local.CutterRepository
import com.example.data.usb.CutExecutionStatus
import com.example.data.usb.MachineConnectionState
import com.example.data.usb.MachineTransportManager
import com.example.domain.model.CutJobRecord
import com.example.domain.model.MaterialPreset
import com.example.domain.model.PlotterProtocol
import com.example.domain.model.Point2D
import com.example.domain.vector.CutPlan
import com.example.domain.vector.PlotterCommandGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CutExecutionUiState(
    val status: CutExecutionStatus = CutExecutionStatus.IDLE,
    val progress: Float = 0f,
    val sentCommands: Int = 0,
    val totalCommands: Int = 0,
    val simulatedHeadMm: Point2D? = null
)

/**
 * Orchestrates real plotter packet streaming, pen-up boundary frame test,
 * visual dry-run simulation, pause/resume, emergency stop, and job history logging.
 */
class CutJobOrchestrator(
    private val scope: CoroutineScope,
    private val transportManager: MachineTransportManager,
    private val commandGenerator: PlotterCommandGenerator,
    private val repository: CutterRepository,
    private val onMessage: (String) -> Unit
) {
    private val _state = MutableStateFlow(CutExecutionUiState())
    val state: StateFlow<CutExecutionUiState> = _state.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    private var activeJob: Job? = null

    fun startRealCut(
        plan: CutPlan,
        jobTitle: String,
        elementCount: Int,
        protocol: PlotterProtocol,
        material: MaterialPreset,
        packetDelayMs: Long
    ) {
        val activeConn = transportManager.connectionState.value
        if (activeConn !is MachineConnectionState.Connected) {
            onMessage("الماكينة غير متصلة حالياً. اتصل عبر USB أو Wi-Fi أو Bluetooth أو استخدم المعاينة.")
            return
        }
        if (plan.commands.isEmpty() || elementCount == 0) {
            onMessage("لا توجد مسارات صالحة للقص")
            return
        }

        activeJob?.cancel()
        _isPaused.value = false
        _state.value = CutExecutionUiState(
            status = CutExecutionStatus.CUTTING,
            progress = 0f,
            sentCommands = 0,
            totalCommands = plan.commands.size,
            simulatedHeadMm = Point2D(0f, 0f)
        )

        activeJob = scope.launch {
            var sent = 0
            for (cmd in plan.commands) {
                while (_isPaused.value && isActive) {
                    delay(100)
                }
                if (!isActive) break

                val res = transportManager.sendCommand(cmd)
                if (res.isFailure) {
                    _state.value = _state.value.copy(status = CutExecutionStatus.ERROR)
                    onMessage(res.exceptionOrNull()?.message ?: "انقطع الإرسال مع الماكينة")
                    return@launch
                }
                sent++
                _state.value = _state.value.copy(
                    sentCommands = sent,
                    progress = sent.toFloat() / plan.commands.size.toFloat(),
                    simulatedHeadMm = parseHeadPositionMm(cmd, protocol) ?: _state.value.simulatedHeadMm
                )
                delay(packetDelayMs)
            }

            _state.value = _state.value.copy(
                status = CutExecutionStatus.COMPLETED,
                progress = 1f,
                simulatedHeadMm = Point2D(0f, 0f)
            )
            repository.logCutJob(
                CutJobRecord(
                    jobName = jobTitle,
                    elementCount = elementCount,
                    totalCutLengthMm = plan.cutDistanceMm,
                    protocolName = protocol.displayName,
                    materialName = material.nameAr,
                    timestamp = System.currentTimeMillis(),
                    status = "مكتمل (${activeConn.transport.labelAr})"
                )
            )
            onMessage("اكتمل إرسال أوامر القص بنجاح عبر ${activeConn.transport.labelAr}")
        }
    }

    fun startVisualDryRun(plan: CutPlan) {
        if (plan.previewPolylines.isEmpty()) {
            onMessage("لا توجد مسارات للمعاينة")
            return
        }
        activeJob?.cancel()
        _isPaused.value = false
        val points = plan.previewPolylines.flatMap { poly ->
            if (poly.closed && poly.points.isNotEmpty()) poly.points + poly.points.first() else poly.points
        }
        if (points.isEmpty()) return

        _state.value = CutExecutionUiState(
            status = CutExecutionStatus.DRY_RUN,
            progress = 0f,
            sentCommands = 0,
            totalCommands = points.size,
            simulatedHeadMm = points.first()
        )

        activeJob = scope.launch {
            points.forEachIndexed { idx, pt ->
                while (_isPaused.value && isActive) {
                    delay(80)
                }
                if (!isActive) return@launch
                _state.value = _state.value.copy(
                    simulatedHeadMm = pt,
                    sentCommands = idx + 1,
                    progress = (idx + 1).toFloat() / points.size.toFloat()
                )
                delay(18)
            }
            _state.value = CutExecutionUiState(status = CutExecutionStatus.IDLE)
            onMessage("انتهت معاينة مسار السكين على الشاشة")
        }
    }

    fun sendFrameBoundingBoxTest(plan: CutPlan, protocol: PlotterProtocol) {
        if (transportManager.connectionState.value !is MachineConnectionState.Connected) {
            onMessage("يرجى ربط الماكينة أولاً لتتبع الإطار الخارجي")
            return
        }
        val box = plan.boundingBoxMm ?: run {
            onMessage("لا توجد عناصر على الحصيرة")
            return
        }
        val cmds = commandGenerator.buildBoundingFrameCommands(protocol, box)
        scope.launch {
            cmds.forEach { cmd ->
                transportManager.sendCommand(cmd)
                delay(25)
            }
            onMessage("تم فحص حدود الإطار الخارجي للتصميم (${box.width.toInt()}×${box.height.toInt()} مم)")
        }
    }

    fun togglePause() {
        val cur = _state.value.status
        if (cur == CutExecutionStatus.CUTTING || cur == CutExecutionStatus.PAUSED) {
            val next = !_isPaused.value
            _isPaused.value = next
            _state.value = _state.value.copy(
                status = if (next) CutExecutionStatus.PAUSED else CutExecutionStatus.CUTTING
            )
        } else if (cur == CutExecutionStatus.DRY_RUN) {
            _isPaused.value = !_isPaused.value
        }
    }

    fun emergencyStop(protocol: PlotterProtocol) {
        activeJob?.cancel()
        activeJob = null
        _isPaused.value = false
        val wasActive = _state.value.status == CutExecutionStatus.CUTTING ||
            _state.value.status == CutExecutionStatus.PAUSED
        _state.value = CutExecutionUiState(
            status = if (wasActive) CutExecutionStatus.STOPPED else CutExecutionStatus.IDLE,
            simulatedHeadMm = null
        )
        if (transportManager.connectionState.value is MachineConnectionState.Connected) {
            val stopCmds = commandGenerator.buildEmergencyStopCommand(protocol)
            scope.launch {
                stopCmds.forEach { transportManager.sendCommand(it) }
            }
        }
    }

    fun cancelAll() {
        activeJob?.cancel()
        activeJob = null
    }

    private fun parseHeadPositionMm(cmd: String, protocol: PlotterProtocol): Point2D? {
        val nums = Regex("""[-+]?\d*\.?\d+""").findAll(cmd).mapNotNull { it.value.toFloatOrNull() }.toList()
        if (nums.size < 2) return null
        val scale = when (protocol) {
            PlotterProtocol.HPGL -> 40f
            PlotterProtocol.GPGL -> 20f
            PlotterProtocol.GRBL -> 1f
        }
        return Point2D(nums[0] / scale, nums[1] / scale)
    }
}
