package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.CutJobRecord
import com.example.domain.model.MaterialPreset
import com.example.domain.model.SavedProjectSummary

@Entity(tableName = "material_presets")
data class MaterialPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameAr: String,
    val categoryAr: String,
    val forceGrams: Int,
    val speedMmPerSec: Int,
    val passes: Int,
    val bladeOffsetMm: Float,
    val isBuiltIn: Boolean
) {
    fun toDomain() = MaterialPreset(id, nameAr, categoryAr, forceGrams, speedMmPerSec, passes, bladeOffsetMm, isBuiltIn)
}

fun MaterialPreset.toEntity() = MaterialPresetEntity(
    id = id,
    nameAr = nameAr,
    categoryAr = categoryAr,
    forceGrams = forceGrams,
    speedMmPerSec = speedMmPerSec,
    passes = passes,
    bladeOffsetMm = bladeOffsetMm,
    isBuiltIn = isBuiltIn
)

@Entity(tableName = "saved_projects")
data class SavedProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val elementCount: Int,
    val matWidthMm: Float,
    val matHeightMm: Float,
    val elementsJson: String,
    val updatedAt: Long
) {
    fun toDomain() = SavedProjectSummary(id, title, elementCount, matWidthMm, matHeightMm, elementsJson, updatedAt)
}

@Entity(tableName = "cut_job_history")
data class CutJobHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobName: String,
    val elementCount: Int,
    val totalCutLengthMm: Float,
    val protocolName: String,
    val materialName: String,
    val timestamp: Long,
    val status: String
) {
    fun toDomain() = CutJobRecord(id, jobName, elementCount, totalCutLengthMm, protocolName, materialName, timestamp, status)
}
