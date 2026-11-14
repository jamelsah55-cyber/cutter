package com.example.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

class OtgUsbManager(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.aistudio.otgcutter.vktmrx.USB_PERMISSION"
    }

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logIdCounter = AtomicLong(1L)

    val isUsbHostSupported: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    private val _attachedDevices = MutableStateFlow<List<UsbDeviceUiInfo>>(emptyList())
    val attachedDevices: StateFlow<List<UsbDeviceUiInfo>> = _attachedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow<OtgConnectionState>(OtgConnectionState.Disconnected)
    val connectionState: StateFlow<OtgConnectionState> = _connectionState.asStateFlow()

    private val _serialLogs = MutableStateFlow<List<SerialLogEntry>>(emptyList())
    val serialLogs: StateFlow<List<SerialLogEntry>> = _serialLogs.asStateFlow()

    private var activeDevice: UsbDevice? = null
    private var activeConnection: UsbDeviceConnection? = null
    private var activeControlInterface: UsbInterface? = null
    private var activeDataInterface: UsbInterface? = null
    private var endpointOut: UsbEndpoint? = null
    private var endpointIn: UsbEndpoint? = null
    private var readerJob: Job? = null
    private var pendingBaudRate: Int = 9600
    private var receiversRegistered = false

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            refreshDevices()
            if (granted && device != null) {
                appendLog("SYS", "تم منح إذن USB OTG للجهاز: ${device.productName ?: device.deviceName}")
                openAndConfigureDevice(device, pendingBaudRate)
            } else {
                _connectionState.value = OtgConnectionState.Error("تم رفض إذن الوصول إلى منفذ USB OTG / CDC")
                appendLog("SYS", "تم رفض إذن الوصول إلى USB OTG")
            }
        }
    }

    private val systemUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshDevices()
                    appendLog("SYS", "تم توصيل جهاز USB / CDC جديد")
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val detached: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (detached != null && detached.deviceId == activeDevice?.deviceId) {
                        disconnect("تم فصل كابل USB عن الجهاز")
                    }
                    refreshDevices()
                }
            }
        }
    }

    fun register() {
        if (receiversRegistered) return
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val systemFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            systemUsbReceiver,
            systemFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiversRegistered = true
        refreshDevices()
    }

    fun unregister() {
        if (!receiversRegistered) return
        try {
            context.unregisterReceiver(permissionReceiver)
            context.unregisterReceiver(systemUsbReceiver)
        } catch (_: IllegalArgumentException) {
        }
        receiversRegistered = false
        disconnect(null)
    }

    fun refreshDevices() {
        val manager = usbManager ?: run {
            _attachedDevices.value = emptyList()
            return
        }
        val list = manager.deviceList.values.map { dev ->
            dev.toUiInfo(manager.hasPermission(dev))
        }
        _attachedDevices.value = list
    }

    fun connectToDevice(deviceId: Int, baudRate: Int) {
        val manager = usbManager ?: run {
            _connectionState.value = OtgConnectionState.Error("خدمة USB غير متوفرة على هذا الجهاز")
            return
        }
        val device = manager.deviceList.values.firstOrNull { it.deviceId == deviceId } ?: run {
            _connectionState.value = OtgConnectionState.Error("الجهاز غير متصل حالياً")
            refreshDevices()
            return
        }

        pendingBaudRate = baudRate
        if (!manager.hasPermission(device)) {
            _connectionState.value = OtgConnectionState.PermissionPending(device.toUiInfo(false))
            val explicitIntent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getBroadcast(context, 0, explicitIntent, flags)
            appendLog("SYS", "جاري طلب إذن نظام Android للوصول إلى USB OTG / CDC...")
            manager.requestPermission(device, pi)
        } else {
            openAndConfigureDevice(device, baudRate)
        }
    }

    private fun openAndConfigureDevice(device: UsbDevice, baudRate: Int) {
        disconnect(null)
        val manager = usbManager ?: return
        val connection = manager.openDevice(device) ?: run {
            _connectionState.value = OtgConnectionState.Error("تعذر فتح قناة الاتصال مع جهاز USB")
            appendLog("SYS", "فشل openDevice لجهاز VID:${device.vendorId}")
            return
        }

        // Look explicitly for CDC Control interface + CDC Data (or generic Bulk OUT/IN) interface
        var commInterface: UsbInterface? = null
        var dataInterface: UsbInterface? = null
        var bulkOut: UsbEndpoint? = null
        var bulkIn: UsbEndpoint? = null

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_COMM && commInterface == null) {
                commInterface = iface
            }
            var foundOut: UsbEndpoint? = null
            var foundIn: UsbEndpoint? = null
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_OUT) foundOut = ep
                    if (ep.direction == UsbConstants.USB_DIR_IN) foundIn = ep
                }
            }
            if (foundOut != null) {
                val preferCdcData = iface.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
                if (dataInterface == null || preferCdcData) {
                    dataInterface = iface
                    bulkOut = foundOut
                    bulkIn = foundIn
                }
            }
        }

        if (dataInterface == null || bulkOut == null) {
            connection.close()
            _connectionState.value = OtgConnectionState.Error("لم يتم العثور على منفذ بيانات تسلسلي (USB CDC / Bulk OUT)")
            return
        }

        if (commInterface != null && commInterface.id != dataInterface.id) {
            connection.claimInterface(commInterface, true)
        }
        if (!connection.claimInterface(dataInterface, true)) {
            if (commInterface != null && commInterface.id != dataInterface.id) {
                connection.releaseInterface(commInterface)
            }
            connection.close()
            _connectionState.value = OtgConnectionState.Error("تعذر حجز واجهة USB CDC/Serial (تأكد من عدم استخدامها)")
            return
        }

        val chip = detectChipFamily(device)
        val controlIfaceId = commInterface?.id ?: dataInterface.id
        configureSerialBaudRate(connection, controlIfaceId, chip, baudRate)

        activeDevice = device
        activeConnection = connection
        activeControlInterface = commInterface?.takeIf { it.id != dataInterface.id }
        activeDataInterface = dataInterface
        endpointOut = bulkOut
        endpointIn = bulkIn

        val uiInfo = device.toUiInfo(true)
        _connectionState.value = OtgConnectionState.Connected(uiInfo, baudRate)
        appendLog("SYS", "متصل بنجاح عبر USB: ${uiInfo.productName} (${chip.labelAr}) @ $baudRate bps")
        startReaderLoop(connection, bulkIn)
    }

    private fun configureSerialBaudRate(
        conn: UsbDeviceConnection,
        ifaceId: Int,
        chip: UsbChipFamily,
        baudRate: Int
    ) {
        try {
            when (chip) {
                UsbChipFamily.FTDI -> {
                    conn.controlTransfer(0x40, 0x00, 0x0000, ifaceId, null, 0, 500)
                    val divisor = (3_000_000 / baudRate).coerceIn(1, 0x3FFF)
                    conn.controlTransfer(0x40, 0x03, divisor, ifaceId, null, 0, 500)
                    conn.controlTransfer(0x40, 0x04, 0x0008, ifaceId, null, 0, 500)
                }
                UsbChipFamily.CH340 -> {
                    conn.controlTransfer(0x40, 0xA1, 0x0000, 0x0000, null, 0, 500)
                    val (factor, prescaler) = when (baudRate) {
                        115200 -> 0xCC to 0x03
                        57600 -> 0x98 to 0x03
                        38400 -> 0x64 to 0x03
                        19200 -> 0xB2 to 0x02
                        else -> 0xB2 to 0x01
                    }
                    conn.controlTransfer(0x40, 0x9A, 0x1312, (factor shl 8) or prescaler, null, 0, 500)
                    conn.controlTransfer(0x40, 0x9A, 0x2518, 0x00C3, null, 0, 500)
                }
                UsbChipFamily.CP210X -> {
                    conn.controlTransfer(0x41, 0x00, 0x0001, ifaceId, null, 0, 500)
                    val baudBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(baudRate).array()
                    conn.controlTransfer(0x41, 0x1E, 0x0000, ifaceId, baudBytes, 4, 500)
                }
                UsbChipFamily.CDC_ACM, UsbChipFamily.PL2303, UsbChipFamily.DIRECT_PLOTTER -> {
                    // USB CDC-ACM SET_LINE_CODING (8N1) + SET_CONTROL_LINE_STATE (DTR + RTS ON)
                    val lineCoding = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(baudRate)
                        .put(0.toByte()) // 1 stop bit
                        .put(0.toByte()) // No parity
                        .put(8.toByte()) // 8 data bits
                        .array()
                    conn.controlTransfer(0x21, 0x20, 0x0000, ifaceId, lineCoding, 7, 500)
                    conn.controlTransfer(0x21, 0x22, 0x0003, ifaceId, null, 0, 500)
                }
            }
        } catch (e: Exception) {
            appendLog("SYS", "ملاحظة تهيئة المنفذ: ${e.message ?: "الوضع الافتراضي"}")
        }
    }

    private fun startReaderLoop(conn: UsbDeviceConnection, epIn: UsbEndpoint?) {
        readerJob?.cancel()
        if (epIn == null) return
        readerJob = scope.launch {
            val buf = ByteArray(256)
            while (isActive && activeConnection === conn) {
                val read = conn.bulkTransfer(epIn, buf, buf.size, 250)
                if (read > 0) {
                    val text = String(buf, 0, read, Charsets.US_ASCII).trim()
                    if (text.isNotEmpty()) {
                        appendLog("RX", text)
                    }
                }
                delay(40)
            }
        }
    }

    suspend fun sendCommand(command: String): Result<Int> = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext Result.failure(
            IllegalStateException("لا توجد ماكينة متصلة عبر USB OTG / CDC")
        )
        val epOut = endpointOut ?: return@withContext Result.failure(
            IllegalStateException("نقطة إرسال البيانات غير متاحة")
        )
        val payload = (if (command.endsWith("\r") || command.endsWith("\n")) command else "$command\r\n")
            .toByteArray(Charsets.US_ASCII)

        var offset = 0
        val maxPacket = epOut.maxPacketSize.coerceIn(16, 64)
        while (offset < payload.size) {
            val len = minOf(maxPacket, payload.size - offset)
            val slice = payload.copyOfRange(offset, offset + len)
            val transferred = conn.bulkTransfer(epOut, slice, slice.size, 1500)
            if (transferred < 0) {
                appendLog("SYS", "فشل إرسال الحزمة عبر USB")
                return@withContext Result.failure(IllegalStateException("فشل إرسال البيانات إلى منفذ USB"))
            }
            offset += transferred
        }
        appendLog("TX", command.trim())
        Result.success(offset)
    }

    fun disconnect(reasonAr: String?) {
        readerJob?.cancel()
        readerJob = null
        try {
            activeDataInterface?.let { activeConnection?.releaseInterface(it) }
            activeControlInterface?.let { activeConnection?.releaseInterface(it) }
            activeConnection?.close()
        } catch (_: Exception) {
        }
        activeConnection = null
        activeControlInterface = null
        activeDataInterface = null
        activeDevice = null
        endpointOut = null
        endpointIn = null
        _connectionState.value = OtgConnectionState.Disconnected
        if (!reasonAr.isNullOrBlank()) {
            appendLog("SYS", reasonAr)
        }
    }

    fun clearLogs() {
        _serialLogs.value = emptyList()
    }

    fun appendLog(direction: String, message: String) {
        val entry = SerialLogEntry(
            id = logIdCounter.getAndIncrement(),
            timestampMs = System.currentTimeMillis(),
            direction = direction,
            message = message
        )
        _serialLogs.value = (_serialLogs.value + entry).takeLast(120)
    }

    private fun detectChipFamily(device: UsbDevice): UsbChipFamily {
        val vidBased = when (device.vendorId) {
            0x0403 -> UsbChipFamily.FTDI
            0x1A86 -> UsbChipFamily.CH340
            0x10C4 -> UsbChipFamily.CP210X
            0x067B -> UsbChipFamily.PL2303
            0x0B4D, 0x0B4E, 0x0B4F, 0x0B57 -> UsbChipFamily.DIRECT_PLOTTER
            else -> null
        }
        if (vidBased != null) return vidBased
        for (i in 0 until device.interfaceCount) {
            val cls = device.getInterface(i).interfaceClass
            if (cls == UsbConstants.USB_CLASS_COMM || cls == UsbConstants.USB_CLASS_CDC_DATA) {
                return UsbChipFamily.CDC_ACM
            }
        }
        return UsbChipFamily.CDC_ACM
    }

    private fun UsbDevice.toUiInfo(hasPermission: Boolean): UsbDeviceUiInfo = UsbDeviceUiInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        productName = productName?.takeIf { it.isNotBlank() } ?: "جهاز USB / CDC تسلسلي",
        manufacturerName = manufacturerName?.takeIf { it.isNotBlank() } ?: "مصنع غير محدد",
        vendorId = vendorId,
        productId = productId,
        chipFamily = detectChipFamily(this),
        hasPermission = hasPermission
    )
}
