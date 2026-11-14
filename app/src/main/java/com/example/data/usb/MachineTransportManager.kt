package com.example.data.usb

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Coordinates all real hardware machine transports:
 * 1. USB OTG / USB CDC-ACM (via OtgUsbManager)
 * 2. Wi-Fi TCP/IP Raw Socket (IP:Port)
 * 3. Bluetooth Classic Serial Port Profile (SPP RFCOMM)
 */
class MachineTransportManager(private val context: Context) {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    val usbManager = OtgUsbManager(context)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isUsbHostSupported: Boolean get() = usbManager.isUsbHostSupported
    val isBluetoothSupported: Boolean get() = bluetoothAdapter != null

    val attachedUsbDevices: StateFlow<List<UsbDeviceUiInfo>> = usbManager.attachedDevices
    val serialLogs: StateFlow<List<SerialLogEntry>> = usbManager.serialLogs

    private val _selectedTransport = MutableStateFlow(ConnectionTransport.USB_OTG_CDC)
    val selectedTransport: StateFlow<ConnectionTransport> = _selectedTransport.asStateFlow()

    private val _pairedBluetoothDevices = MutableStateFlow<List<BluetoothDeviceUiInfo>>(emptyList())
    val pairedBluetoothDevices: StateFlow<List<BluetoothDeviceUiInfo>> = _pairedBluetoothDevices.asStateFlow()

    private val _wirelessState = MutableStateFlow<MachineConnectionState>(MachineConnectionState.Disconnected)

    // Wi-Fi socket handles
    private var wifiSocket: Socket? = null
    private var wifiOut: OutputStream? = null
    private var wifiReadJob: Job? = null

    // Bluetooth socket handles
    private var btSocket: BluetoothSocket? = null
    private var btOut: OutputStream? = null
    private var btReadJob: Job? = null

    val connectionState: StateFlow<MachineConnectionState> = combine(
        usbManager.connectionState,
        _wirelessState
    ) { usbState, wirelessState ->
        when {
            wirelessState is MachineConnectionState.Connected -> wirelessState
            wirelessState is MachineConnectionState.Connecting -> wirelessState
            usbState is OtgConnectionState.Connected -> MachineConnectionState.Connected(
                transport = ConnectionTransport.USB_OTG_CDC,
                deviceName = usbState.device.productName,
                detailsAr = "${usbState.device.chipFamily.labelAr} · ${usbState.baudRate} bps"
            )
            usbState is OtgConnectionState.PermissionPending -> MachineConnectionState.Connecting(
                transport = ConnectionTransport.USB_OTG_CDC,
                labelAr = "بانتظار إذن USB لـ ${usbState.device.productName}..."
            )
            wirelessState is MachineConnectionState.Error -> wirelessState
            usbState is OtgConnectionState.Error -> MachineConnectionState.Error(
                transport = ConnectionTransport.USB_OTG_CDC,
                messageAr = usbState.messageAr
            )
            else -> MachineConnectionState.Disconnected
        }
    }.stateIn(scope, SharingStarted.Eagerly, MachineConnectionState.Disconnected)

    fun register() {
        usbManager.register()
        refreshBluetoothDevices()
    }

    fun unregister() {
        disconnectAll(null)
        usbManager.unregister()
    }

    fun selectTransport(transport: ConnectionTransport) {
        _selectedTransport.value = transport
        if (transport == ConnectionTransport.BLUETOOTH_SPP) {
            refreshBluetoothDevices()
        } else if (transport == ConnectionTransport.USB_OTG_CDC) {
            usbManager.refreshDevices()
        }
    }

    fun refreshUsbDevices() {
        usbManager.refreshDevices()
    }

    fun connectUsbDevice(deviceId: Int, baudRate: Int) {
        disconnectWireless(null)
        usbManager.connectToDevice(deviceId, baudRate)
    }

    fun connectWifi(hostIp: String, port: Int) {
        val cleanIp = hostIp.trim()
        if (cleanIp.isEmpty() || port !in 1..65535) {
            _wirelessState.value = MachineConnectionState.Error(
                ConnectionTransport.WIFI_TCP,
                "يرجى إدخال عنوان IP ومنفذ TCP صحيحين (مثلاً 192.168.1.100:9100)"
            )
            return
        }
        disconnectAll(null)
        _wirelessState.value = MachineConnectionState.Connecting(
            ConnectionTransport.WIFI_TCP,
            "جاري الاتصال عبر واي فاي بـ $cleanIp:$port..."
        )
        usbManager.appendLog("SYS", "محاولة اتصال Wi-Fi TCP بـ $cleanIp:$port...")

        scope.launch {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(cleanIp, port), 5000)
                sock.tcpNoDelay = true
                sock.soTimeout = 350
                val out = sock.getOutputStream()
                val input = sock.getInputStream()

                wifiSocket = sock
                wifiOut = out
                _wirelessState.value = MachineConnectionState.Connected(
                    transport = ConnectionTransport.WIFI_TCP,
                    deviceName = "$cleanIp:$port",
                    detailsAr = "اتصال شبكة Wi-Fi (TCP Socket)"
                )
                usbManager.appendLog("SYS", "تم الاتصال بنجاح عبر واي فاي: $cleanIp:$port")
                startWirelessReaderLoop(input, ConnectionTransport.WIFI_TCP) {
                    wifiSocket === sock
                }
            } catch (e: Exception) {
                _wirelessState.value = MachineConnectionState.Error(
                    ConnectionTransport.WIFI_TCP,
                    "تعذر الاتصال عبر واي فاي ($cleanIp:$port): ${e.localizedMessage ?: "تأكد من الشبكة"}"
                )
                usbManager.appendLog("SYS", "فشل اتصال Wi-Fi: ${e.message}")
            }
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetoothDevices() {
        val adapter = bluetoothAdapter
        if (adapter == null || !hasBluetoothPermissions()) {
            _pairedBluetoothDevices.value = emptyList()
            return
        }
        try {
            val bonded = adapter.bondedDevices.orEmpty().map { dev ->
                BluetoothDeviceUiInfo(
                    name = dev.name?.takeIf { it.isNotBlank() } ?: "جهاز بلوتوث",
                    address = dev.address,
                    isBonded = true
                )
            }.sortedBy { it.name }
            _pairedBluetoothDevices.value = bonded
        } catch (_: SecurityException) {
            _pairedBluetoothDevices.value = emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectBluetoothDevice(address: String) {
        val adapter = bluetoothAdapter ?: run {
            _wirelessState.value = MachineConnectionState.Error(
                ConnectionTransport.BLUETOOTH_SPP,
                "البلوتوث غير مدعوم على هذا الجهاز"
            )
            return
        }
        if (!hasBluetoothPermissions()) {
            _wirelessState.value = MachineConnectionState.Error(
                ConnectionTransport.BLUETOOTH_SPP,
                "يرجى منح إذن البلوتوث للاتصال بالماكينة"
            )
            return
        }
        if (!adapter.isEnabled) {
            _wirelessState.value = MachineConnectionState.Error(
                ConnectionTransport.BLUETOOTH_SPP,
                "يرجى تفعيل البلوتوث في إعدادات الهاتف أولاً"
            )
            return
        }

        disconnectAll(null)
        _wirelessState.value = MachineConnectionState.Connecting(
            ConnectionTransport.BLUETOOTH_SPP,
            "جاري الاتصال عبر بلوتوث بـ $address..."
        )
        usbManager.appendLog("SYS", "محاولة اتصال Bluetooth SPP بالعنوان $address...")

        scope.launch {
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    try {
                        adapter.cancelDiscovery()
                    } catch (_: Exception) {
                    }
                }
                val device = adapter.getRemoteDevice(address)
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                sock.connect()
                val out = sock.outputStream
                val input = sock.inputStream

                btSocket = sock
                btOut = out
                val devName = device.name?.takeIf { it.isNotBlank() } ?: address
                _wirelessState.value = MachineConnectionState.Connected(
                    transport = ConnectionTransport.BLUETOOTH_SPP,
                    deviceName = devName,
                    detailsAr = "بلوتوث SPP ($address)"
                )
                usbManager.appendLog("SYS", "متصل بنجاح عبر بلوتوث: $devName")
                startWirelessReaderLoop(input, ConnectionTransport.BLUETOOTH_SPP) {
                    btSocket === sock
                }
            } catch (e: Exception) {
                _wirelessState.value = MachineConnectionState.Error(
                    ConnectionTransport.BLUETOOTH_SPP,
                    "فشل الاتصال بالبلوتوث: ${e.localizedMessage ?: "تأكد أن الماكينة قريبة ومقترنة"}"
                )
                usbManager.appendLog("SYS", "فشل اتصال Bluetooth: ${e.message}")
            }
        }
    }

    private fun startWirelessReaderLoop(
        input: InputStream,
        transport: ConnectionTransport,
        stillActive: () -> Boolean
    ) {
        val job = scope.launch {
            val buf = ByteArray(256)
            while (isActive && stillActive()) {
                try {
                    val read = input.read(buf)
                    if (read == -1) {
                        disconnectWireless("انقطع الاتصال عبر ${transport.labelAr}")
                        break
                    }
                    if (read > 0) {
                        val text = String(buf, 0, read, Charsets.US_ASCII).trim()
                        if (text.isNotEmpty()) usbManager.appendLog("RX", text)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Normal read polling timeout for responsive cancellation
                } catch (_: Exception) {
                    if (stillActive()) {
                        disconnectWireless("انقطع اتصال ${transport.labelAr}")
                    }
                    break
                }
            }
        }
        if (transport == ConnectionTransport.WIFI_TCP) {
            wifiReadJob?.cancel()
            wifiReadJob = job
        } else {
            btReadJob?.cancel()
            btReadJob = job
        }
    }

    suspend fun sendCommand(command: String): Result<Int> = withContext(Dispatchers.IO) {
        when (val state = connectionState.value) {
            is MachineConnectionState.Connected -> when (state.transport) {
                ConnectionTransport.USB_OTG_CDC -> usbManager.sendCommand(command)
                ConnectionTransport.WIFI_TCP -> sendStreamCommand(wifiOut, command, "Wi-Fi")
                ConnectionTransport.BLUETOOTH_SPP -> sendStreamCommand(btOut, command, "Bluetooth")
            }
            else -> Result.failure(IllegalStateException("لا توجد ماكينة متصلة حالياً"))
        }
    }

    private fun sendStreamCommand(
        out: OutputStream?,
        command: String,
        label: String
    ): Result<Int> {
        val stream = out ?: return Result.failure(IllegalStateException("قناة الإرسال غير متاحة"))
        return try {
            val payload = (if (command.endsWith("\r") || command.endsWith("\n")) command else "$command\r\n")
                .toByteArray(Charsets.US_ASCII)
            stream.write(payload)
            stream.flush()
            usbManager.appendLog("TX", command.trim())
            Result.success(payload.size)
        } catch (e: Exception) {
            usbManager.appendLog("SYS", "فشل إرسال البيانات عبر $label: ${e.message}")
            Result.failure(IllegalStateException("تعذر الإرسال عبر $label: ${e.localizedMessage}"))
        }
    }

    fun disconnectAll(reasonAr: String?) {
        usbManager.disconnect(null)
        disconnectWireless(reasonAr)
    }

    private fun disconnectWireless(reasonAr: String?) {
        wifiReadJob?.cancel()
        wifiReadJob = null
        btReadJob?.cancel()
        btReadJob = null
        try {
            wifiOut?.close()
            wifiSocket?.close()
        } catch (_: Exception) {
        }
        try {
            btOut?.close()
            btSocket?.close()
        } catch (_: Exception) {
        }
        wifiOut = null
        wifiSocket = null
        btOut = null
        btSocket = null
        if (_wirelessState.value != MachineConnectionState.Disconnected) {
            _wirelessState.value = MachineConnectionState.Disconnected
        }
        if (!reasonAr.isNullOrBlank()) {
            usbManager.appendLog("SYS", reasonAr)
        }
    }

    fun clearLogs() = usbManager.clearLogs()
}
