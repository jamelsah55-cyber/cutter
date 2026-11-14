package com.cutterpro.desktop.model

data class Point2D(
    val xMm: Float,
    val yMm: Float
)

data class Polyline2D(
    val points: List<Point2D>,
    val closed: Boolean = false
)

enum class VectorShapeType(val labelAr: String) {
    RECTANGLE("مستطيل"),
    ROUNDED_RECT("مستطيل دائري"),
    CIRCLE("دائرة / بيضاوي"),
    STAR("نجمة"),
    HEXAGON("سداسي"),
    DIAMOND("معين"),
    HEART("قلب"),
    ISLAMIC_STAR("نجمة إسلامية"),
    ARROW("سهم"),
    REGISTRATION_MARKS("علامات قص"),
    TEXT("نص"),
    IMPORTED_SVG("ملف مستورد"),
    TRACED_IMAGE("صورة متتبعة"),
    CUSTOM_PATH("مسار مخصص")
}

data class NodeRef(
    val polylineIndex: Int,
    val pointIndex: Int
)

data class PaletteSwatch(
    val hex: Long,
    val labelAr: String
)

object VinylColorPalettes {
    val fillSwatches = listOf(
        PaletteSwatch(0xFF00D4B8L, "تركواز"),
        PaletteSwatch(0xFFFF4D6DL, "أحمر مرجاني"),
        PaletteSwatch(0xFF3A86FFL, "أزرق ملكي"),
        PaletteSwatch(0xFFFF7A2FL, "برتقالي"),
        PaletteSwatch(0xFFEAB308L, "ذهبي لامع"),
        PaletteSwatch(0xFF22C55EL, "أخضر"),
        PaletteSwatch(0xFF9B5DE5L, "بنفسجي"),
        PaletteSwatch(0xFFF472B6L, "وردي"),
        PaletteSwatch(0xFF38BDF8L, "سماوي"),
        PaletteSwatch(0xFFDC2626L, "عنابي"),
        PaletteSwatch(0xFFCBD5E1L, "فضي"),
        PaletteSwatch(0xFFFFFFFFL, "أبيض"),
        PaletteSwatch(0xFF1E293BL, "أسود مطفي")
    )

    val cutLineSwatches = listOf(
        PaletteSwatch(0xFF00D4B8L, "قص أساسي"),
        PaletteSwatch(0xFFFF4D6DL, "خط قص (أحمر)"),
        PaletteSwatch(0xFF3A86FFL, "تسطير / ثني"),
        PaletteSwatch(0xFFFF7A2FL, "نقش / رسم"),
        PaletteSwatch(0xFFFFD166L, "قص داخلي"),
        PaletteSwatch(0xFF9B5DE5L, "قطع عميق"),
        PaletteSwatch(0xFF06D6A0L, "قص سريع"),
        PaletteSwatch(0xFFFF006EL, "تحديد مزدوج"),
        PaletteSwatch(0xFFF8FAFCL, "أبيض"),
        PaletteSwatch(0xFF0F172AL, "أسود")
    )
}

data class DesignElement(
    val id: String,
    val name: String,
    val type: VectorShapeType,
    val xMm: Float,
    val yMm: Float,
    val widthMm: Float,
    val heightMm: Float,
    val rotationDeg: Float = 0f,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val textContent: String = "",
    val fontFamily: String = "Arial",
    val cornerRadiusMm: Float = 8f,
    val starPoints: Int = 5,
    val normalizedPaths: List<Polyline2D> = emptyList(),
    val strokeColorHex: Long = 0xFF00D4B8L,
    val fillColorHex: Long? = null,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val groupId: String? = null
)

enum class VectorBooleanOp(val labelAr: String) {
    UNION("لحام (دمج)"),
    DIFFERENCE("تفريغ (قص متداخل)"),
    INTERSECT("تقاطع")
}

enum class PlotterProtocol(
    val displayName: String,
    val subtitleAr: String,
    val fileExtension: String
) {
    HPGL("HPGL", "ماكينات الكتر القياسية (Roland / Refine / PCut)", "plt"),
    GPGL("GPGL", "ماكينات Graphtec و Silhouette", "gpgl"),
    GRBL("GRBL / G-Code", "ماكينات الليزر والراسمات (G-Code)", "gcode")
}

enum class AlignmentAction(val labelAr: String) {
    ALIGN_LEFT("محاذاة لليسار"),
    CENTER_HORIZONTAL("توسيط أفقي"),
    ALIGN_RIGHT("محاذاة لليمين"),
    ALIGN_TOP("محاذاة للأعلى"),
    CENTER_VERTICAL("توسيط عمودي"),
    ALIGN_BOTTOM("محاذاة للأسفل"),
    CENTER_MAT("توسيط بالحصيرة"),
    DISTRIBUTE_HORIZONTAL("توزيع أفقي متساوٍ"),
    DISTRIBUTE_VERTICAL("توزيع عمودي متساوٍ")
}

data class CuttingMatConfig(
    val widthMm: Float = 300f,
    val heightMm: Float = 300f,
    val gridStepMm: Float = 10f,
    val snapToGrid: Boolean = true,
    val weedingBoxEnabled: Boolean = false,
    val weedingMarginMm: Float = 5f,
    val optimizeCutOrder: Boolean = true,
    val overcutMm: Float = 0.4f,
    val cutOnlySelected: Boolean = false,
    val cutColorFilterHex: Long? = null
)

data class MaterialPreset(
    val nameAr: String,
    val categoryAr: String,
    val forceGrams: Int,
    val speedMmPerSec: Int,
    val passes: Int = 1,
    val bladeOffsetMm: Float = 0.25f
)

object DefaultMaterialPresets {
    val all = listOf(
        MaterialPreset("فينيل لاصق لامع", "لافتات وإعلانات", 95, 250, 1, 0.25f),
        MaterialPreset("فينيل حراري (HTV)", "تيشيرتات وأقمشة", 115, 200, 1, 0.25f),
        MaterialPreset("استيكر ورق / كوشيه", "طباعة وقص", 145, 180, 1, 0.30f),
        MaterialPreset("فينيل عاكس", "سلامة مرورية", 210, 130, 2, 0.45f),
        MaterialPreset("كرتون مقوى 250 جم", "تغليف وكروت", 280, 110, 2, 0.50f)
    )
}

data class BoundingBoxMm(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float
) {
    val width: Float get() = (maxX - minX).coerceAtLeast(0f)
    val height: Float get() = (maxY - minY).coerceAtLeast(0f)
}

enum class DesktopTransportType(val labelAr: String) {
    SERIAL_COM("كابل USB / منفذ COM"),
    WIFI_TCP("شبكة Wi-Fi (TCP/IP)")
}

data class SerialPortEntry(
    val systemPortName: String,
    val descriptiveName: String
)

enum class CutExecutionStatus(val labelAr: String) {
    IDLE("جاهز للقص"),
    DRY_RUN("معاينة المسار"),
    CUTTING("جاري القص"),
    PAUSED("متوقف مؤقتاً")
}

data class SerialLogEntry(
    val direction: String,
    val message: String
)
