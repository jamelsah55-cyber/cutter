package com.example.data.usb

import com.example.domain.model.PlotterProtocol

enum class ConnectionTransport(val labelAr: String) {
    USB_OTG_CDC("USB OTG / CDC"),
    WIFI_TCP("واي فاي (Wi-Fi)"),
    BLUETOOTH_SPP("بلوتوث (SPP)")
}

enum class UsbChipFamily(val labelAr: String) {
    FTDI("FTDI (أصلي/صناعي)"),
    CH340("CH340 / CH341"),
    CP210X("Silicon Labs CP210x"),
    PL2303("Prolific PL2303"),
    CDC_ACM("USB CDC-ACM قياسي"),
    DIRECT_PLOTTER("كتر بلوتر مباشر")
}

data class UsbDeviceUiInfo(
    val deviceId: Int,
    val deviceName: String,
    val productName: String,
    val manufacturerName: String,
    val vendorId: Int,
    val productId: Int,
    val chipFamily: UsbChipFamily,
    val hasPermission: Boolean
) {
    val vidPidFormatted: String
        get() = "VID:${vendorId.toString(16).uppercase().padStart(4, '0')} · PID:${productId.toString(16).uppercase().padStart(4, '0')}"
}

data class BluetoothDeviceUiInfo(
    val name: String,
    val address: String,
    val isBonded: Boolean = true
)

sealed class OtgConnectionState {
    data object Disconnected : OtgConnectionState()
    data class PermissionPending(val device: UsbDeviceUiInfo) : OtgConnectionState()
    data class Connected(val device: UsbDeviceUiInfo, val baudRate: Int) : OtgConnectionState()
    data class Error(val messageAr: String) : OtgConnectionState()
}

sealed class MachineConnectionState {
    data object Disconnected : MachineConnectionState()
    data class Connecting(val transport: ConnectionTransport, val labelAr: String) : MachineConnectionState()
    data class Connected(
        val transport: ConnectionTransport,
        val deviceName: String,
        val detailsAr: String
    ) : MachineConnectionState()
    data class Error(val transport: ConnectionTransport, val messageAr: String) : MachineConnectionState()
}

enum class CutExecutionStatus(val labelAr: String) {
    IDLE("جاهز"),
    CUTTING("جاري إرسال أوامر القص"),
    PAUSED("متوقف مؤقتاً"),
    DRY_RUN("معاينة المسار على الشاشة"),
    COMPLETED("اكتمل القص بنجاح"),
    STOPPED("تم الإيقاف الطارئ"),
    ERROR("خطأ في الإرسال")
}

data class SerialConfig(
    val baudRate: Int = 9600,
    val protocol: PlotterProtocol = PlotterProtocol.HPGL,
    val packetDelayMs: Long = 12L,
    val wifiHost: String = "192.168.1.100",
    val wifiPort: Int = 9100
)

data class SerialLogEntry(
    val id: Long,
    val timestampMs: Long,
    val direction: String, // "TX" | "RX" | "SYS"
    val message: String
)
