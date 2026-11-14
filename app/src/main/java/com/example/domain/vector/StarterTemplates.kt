package com.example.domain.vector

import com.example.domain.model.DesignElement
import com.example.domain.model.DesignFontStyle
import com.example.domain.model.VectorShapeType
import java.util.UUID

data class WorkshopTemplate(
    val id: String,
    val titleAr: String,
    val subtitleAr: String,
    val recommendedMaterialAr: String,
    val buildElements: (VectorGeometryEngine) -> List<DesignElement>
)

object StarterTemplates {

    fun buildInitialWorkspace(engine: VectorGeometryEngine): List<DesignElement> {
        val (textPaths, ratio) = engine.vectorizeText("إبداع", DesignFontStyle.AMIRI)
        val textW = 120f
        val textH = (textW / ratio).coerceIn(30f, 80f)
        return listOf(
            DesignElement(
                id = UUID.randomUUID().toString(),
                name = "إطار خارجي",
                type = VectorShapeType.ROUNDED_RECT,
                xMm = 40f,
                yMm = 50f,
                widthMm = 220f,
                heightMm = 140f,
                cornerRadiusMm = 14f,
                strokeColorHex = 0xFF00D4B8L
            ),
            DesignElement(
                id = UUID.randomUUID().toString(),
                name = "نجمة إسلامية",
                type = VectorShapeType.ISLAMIC_STAR,
                xMm = 120f,
                yMm = 64f,
                widthMm = 60f,
                heightMm = 60f,
                strokeColorHex = 0xFFFF7A2FL
            ),
            DesignElement(
                id = UUID.randomUUID().toString(),
                name = "نص: إبداع",
                type = VectorShapeType.TEXT,
                xMm = 90f,
                yMm = 132f,
                widthMm = textW,
                heightMm = textH,
                textContent = "إبداع",
                fontStyle = DesignFontStyle.AMIRI,
                normalizedPaths = textPaths,
                strokeColorHex = 0xFF00D4B8L
            )
        )
    }

    val templates: List<WorkshopTemplate> = listOf(
        WorkshopTemplate(
            id = "arabic_welcome",
            titleAr = "لافتة ترحيبية (أهلاً بكم)",
            subtitleAr = "خط عربي كلاسيكي مع إطار زخرفي مناسب للزجاج والجدران",
            recommendedMaterialAr = "فينيل لاصق لامع",
            buildElements = { engine ->
                val (paths, ratio) = engine.vectorizeText("أهلاً بكم", DesignFontStyle.AMIRI)
                val w = 160f
                val h = (w / ratio).coerceIn(32f, 80f)
                listOf(
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "إطار اللافتة",
                        type = VectorShapeType.ROUNDED_RECT,
                        xMm = 30f,
                        yMm = 40f,
                        widthMm = 240f,
                        heightMm = 120f,
                        cornerRadiusMm = 16f,
                        strokeColorHex = 0xFF00D4B8L
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "أهلاً بكم",
                        type = VectorShapeType.TEXT,
                        xMm = 70f,
                        yMm = 70f,
                        widthMm = w,
                        heightMm = h,
                        textContent = "أهلاً بكم",
                        fontStyle = DesignFontStyle.AMIRI,
                        normalizedPaths = paths,
                        strokeColorHex = 0xFFFF7A2FL
                    )
                )
            }
        ),
        WorkshopTemplate(
            id = "htv_tshirt_badge",
            titleAr = "شعار تيشيرت (عكس حراري جاهز)",
            subtitleAr = "نجمة هندسية ونص معكوس أفقياً للطباعة الحرارية HTV",
            recommendedMaterialAr = "فينيل حراري (HTV)",
            buildElements = { engine ->
                val (paths, ratio) = engine.vectorizeText("PRO", DesignFontStyle.CAIRO)
                val w = 110f
                val h = (w / ratio).coerceIn(28f, 65f)
                listOf(
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "شعار سداسي",
                        type = VectorShapeType.HEXAGON,
                        xMm = 70f,
                        yMm = 40f,
                        widthMm = 160f,
                        heightMm = 160f,
                        mirrorHorizontal = true,
                        strokeColorHex = 0xFFFF7A2FL
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "PRO (معكوس)",
                        type = VectorShapeType.TEXT,
                        xMm = 95f,
                        yMm = 95f,
                        widthMm = w,
                        heightMm = h,
                        textContent = "PRO",
                        fontStyle = DesignFontStyle.CAIRO,
                        mirrorHorizontal = true,
                        normalizedPaths = paths,
                        strokeColorHex = 0xFF00D4B8L
                    )
                )
            }
        ),
        WorkshopTemplate(
            id = "islamic_rosette_pack",
            titleAr = "طقم زخارف إسلامية للستيكر",
            subtitleAr = "3 وحدات زخرفية متناسقة جاهزة للقص والتفريغ",
            recommendedMaterialAr = "ورق ستيكر مطبوع",
            buildElements = { _ ->
                listOf(
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "زخرفة 1",
                        type = VectorShapeType.ISLAMIC_STAR,
                        xMm = 20f,
                        yMm = 30f,
                        widthMm = 80f,
                        heightMm = 80f,
                        strokeColorHex = 0xFF00D4B8L
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "زخرفة 2",
                        type = VectorShapeType.ISLAMIC_STAR,
                        xMm = 110f,
                        yMm = 30f,
                        widthMm = 80f,
                        heightMm = 80f,
                        strokeColorHex = 0xFF00D4B8L
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "زخرفة 3",
                        type = VectorShapeType.ISLAMIC_STAR,
                        xMm = 200f,
                        yMm = 30f,
                        widthMm = 80f,
                        heightMm = 80f,
                        strokeColorHex = 0xFF00D4B8L
                    )
                )
            }
        ),
        WorkshopTemplate(
            id = "print_cut_marks",
            titleAr = "علامات القص والملصقات (Print & Cut)",
            subtitleAr = "إطار علامات ضبط الليزر مع 4 دوائر قص",
            recommendedMaterialAr = "ورق ستيكر مطبوع",
            buildElements = { _ ->
                listOf(
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "علامات التوجيه",
                        type = VectorShapeType.REGISTRATION_MARKS,
                        xMm = 20f,
                        yMm = 20f,
                        widthMm = 240f,
                        heightMm = 200f,
                        strokeColorHex = 0xFFFF7A2FL
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "ملصق دائري 1",
                        type = VectorShapeType.CIRCLE,
                        xMm = 45f,
                        yMm = 45f,
                        widthMm = 80f,
                        heightMm = 80f
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "ملصق دائري 2",
                        type = VectorShapeType.CIRCLE,
                        xMm = 155f,
                        yMm = 45f,
                        widthMm = 80f,
                        heightMm = 80f
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "ملصق دائري 3",
                        type = VectorShapeType.CIRCLE,
                        xMm = 45f,
                        yMm = 135f,
                        widthMm = 80f,
                        heightMm = 80f
                    ),
                    DesignElement(
                        id = UUID.randomUUID().toString(),
                        name = "ملصق دائري 4",
                        type = VectorShapeType.CIRCLE,
                        xMm = 155f,
                        yMm = 135f,
                        widthMm = 80f,
                        heightMm = 80f
                    )
                )
            }
        )
    )
}
