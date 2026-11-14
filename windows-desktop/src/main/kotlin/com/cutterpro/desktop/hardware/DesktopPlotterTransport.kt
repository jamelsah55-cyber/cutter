package com.cutterpro.desktop.hardware

import com.cutterpro.desktop.model.CutExecutionStatus
import com.cutterpro.desktop.model.DesktopTransportType
import com.cutterpro.desktop.model.MaterialPreset
import com.cutterpro.desktop.model.PlotterProtocol
import com.cutterpro.desktop.model.Point2D
import com.cutterpro.desktop.model.SerialLogEntry
import com.cutterpro.desktop.model.SerialPortEntry
import com.cutterpro.desktop.vector.CutPlan
import com.cutterpro.desktop.vector.DesktopPlotterCommandGenerator
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class DesktopPlotterTransport(
    private val scope: CoroutineScope,
    private val generator: DesktopPlotterCommandGenerator,
    private val onStatusMessage: (String) -> Unit
) {
    private val _serialPorts = MutableStateFlow<List<SerialPortEntry>>(emptyList())
    val serialPorts: StateFlow<List<SerialPortEntry>> = _serialPorts.asStateFlow()

    private val _connectedLabel = MutableStateFlow<String?>(null)
    val connectedLabel: StateFlow<String?> = _connectedLabel.asStateFlow()

    private val _logs = MutableStateFlow<List<SerialLogEntry>>(emptyList())
    val logs: StateFlow<List<SerialLogEntry>> = _logs.asStateFlow()

    private val _cutStatus = MutableStateFlow(CutExecutionStatus.IDLE)
    val cutStatus: StateFlow<CutExecutionStatus> = _cutStatus.asStateFlow()

    private val _cutProgress = MutableStateFlow(0f)
    val cutProgress: StateFlow<Float> = _cutProgress.asStateFlow()

    private val _simulatedHead = MutableStateFlow<Point2D?>(null)
    val simulatedHead: StateFlow<Point2D?> = _simulatedHead.asStateFlow()

    private var activeSerialPort: SerialPort? = null
    private var activeSocket: Socket? = null
    private var activeOutput: OutputStream? = null
    private var job: Job? = null
    @Volatile private var paused = false

    fun refreshSerialPorts() {
        val list = runCatching {
            SerialPort.getCommPorts().map {
                SerialPortEntry(it.systemPortName, "${it.systemPortName} - ${it.descriptivePortName}")
            }
        }.getOrDefault(emptyList())
        _serialPorts.value = list
        appendLog("SYS", "تم العثور على ${list.size} منفذ COM")
    }

    fun connectSerial(portName: String, baudRate: Int) {
        disconnect()
        scope.launch(Dispatchers.IO) {
            val port = SerialPort.getCommPort(portName).apply {
                this.baudRate = baudRate
                numDataBits = 8
                numStopBits = SerialPort.ONE_STOP_BIT
                parity = SerialPort.NO_PARITY
                setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING, 1500, 1500)
            }
            if (port.openPort()) {
                activeSerialPort = port
                activeOutput = port.outputStream
                _connectedLabel.value = "COM ($portName · $baudRate)"
                appendLog("SYS", "تم الاتصال بالمنفذ $portName بنجاح")
                onStatusMessage("تم الاتصال بالماكينة عبر $portName")
            } else {
                appendLog("ERR", "تعذر فتح المنفذ $portName")
                onStatusMessage("تعذر فتح المنفذ $portName — تأكد أن البلوتر غير مستخدم ببرنامج آخر")
            }
        }
    }

    fun connectWifi(host: String, port: Int) {
        disconnect()
        scope.launch(Dispatchers.IO) {
            runCatching {
                val socket = Socket()
                socket.connect(InetSocketAddress(host.trim(), port.coerceIn(1, 65535)), 4000)
                activeSocket = socket
                activeOutput = socket.getOutputStream()
                _connectedLabel.value = "Wi-Fi ($host:$port)"
                appendLog("SYS", "تم الاتصال بالشبكة $host:$port")
                onStatusMessage("تم الاتصال عبر الشبكة $host:$port")
            }.onFailure {
                appendLog("ERR", "فشل اتصال الشبكة: ${it.message}")
                onStatusMessage("تعذر الاتصال بالماكينة على $host:$port")
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        _cutStatus.value = CutExecutionStatus.IDLE
        _simulatedHead.value = null
        runCatching { activeOutput?.close() }
        runCatching { activeSerialPort?.closePort() }
        runCatching { activeSocket?.close() }
        activeOutput = null
        activeSerialPort = null
        activeSocket = null
        _connectedLabel.value = null
    }

    suspend fun sendCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        val out = activeOutput ?: return@withContext false
        return@withContext runCatching {
            val bytes = (cmd + "\r\n").toByteArray(Charsets.US_ASCII)
            out.write(bytes)
            out.flush()
            appendLog("TX", cmd)
            true
        }.getOrElse {
            appendLog("ERR", "خطأ إرسال: ${it.message}")
            false
        }
    }

    fun sendImmediate(commands: List<String>) {
        if (activeOutput == null) {
            onStatusMessage("يرجى الاتصال بالماكينة أولاً عبر USB/COM أو Wi-Fi")
            return
        }
        scope.launch { commands.forEach { sendCommand(it); delay(15) } }
    }

    fun startRealCut(plan: CutPlan, protocol: PlotterProtocol, material: MaterialPreset, packetDelayMs: Long = 8L) {
        if (activeOutput == null) {
            onStatusMessage("يرجى ربط الماكينة أولاً قبل بدء القص")
            return
        }
        if (plan.commands.isEmpty()) return
        job?.cancel()
        paused = false
        _cutStatus.value = CutExecutionStatus.CUTTING
        _cutProgress.value = 0f

        job = scope.launch {
            val total = plan.commands.size
            for (i in plan.commands.indices) {
                while (paused) delay(100)
                if (!sendCommand(plan.commands[i])) {
                    _cutStatus.value = CutExecutionStatus.IDLE
                    return@launch
                }
                _cutProgress.value = (i + 1f) / total
                delay(packetDelayMs)
            }
            _cutStatus.value = CutExecutionStatus.IDLE
            onStatusMessage("اكتمل إرسال مهمة القص (${material.nameAr})")
        }
    }

    fun startDryRun(plan: CutPlan) {
        if (plan.previewPolylines.isEmpty()) return
        job?.cancel()
        paused = false
        _cutStatus.value = CutExecutionStatus.DRY_RUN
        _cutProgress.value = 0f
        job = scope.launch {
            val pts = plan.previewPolylines.flatMap { if (it.closed) it.points + it.points.first() else it.points }
            pts.forEachIndexed { idx, pt ->
                while (paused) delay(80)
                _simulatedHead.value = pt
                _cutProgress.value = (idx + 1f) / pts.size.coerceAtLeast(1)
                delay(22)
            }
            _simulatedHead.value = null
            _cutStatus.value = CutExecutionStatus.IDLE
        }
    }

    fun togglePause() {
        paused = !paused
        _cutStatus.value = if (paused) CutExecutionStatus.PAUSED else CutExecutionStatus.CUTTING
    }

    fun emergencyStop(protocol: PlotterProtocol) {
        job?.cancel()
        paused = false
        _cutStatus.value = CutExecutionStatus.IDLE
        _simulatedHead.value = null
        scope.launch {
            generator.buildEmergencyStopCommand(protocol).forEach { sendCommand(it) }
        }
        onStatusMessage("تم إرسال أمر الإيقاف الطارئ للماكينة")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun appendLog(dir: String, msg: String) {
        _logs.value = (_logs.value + SerialLogEntry(dir, msg)).takeLast(120)
    }
}
