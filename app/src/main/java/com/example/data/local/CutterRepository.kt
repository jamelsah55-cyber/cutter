package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.CutJobRecord
import com.example.domain.model.CuttingMatConfig
import com.example.domain.model.DesignElement
import com.example.domain.model.MaterialPreset
import com.example.domain.model.PlotterProtocol
import com.example.domain.model.SavedProjectSummary
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cutterPrefsDataStore by preferencesDataStore(name = "cutter_pro_prefs")

data class MachinePreferences(
    val matWidthMm: Float = 300f,
    val matHeightMm: Float = 300f,
    val gridStepMm: Float = 10f,
    val snapToGrid: Boolean = true,
    val defaultProtocol: PlotterProtocol = PlotterProtocol.HPGL,
    val baudRate: Int = 9600,
    val wifiHost: String = "192.168.1.100",
    val wifiPort: Int = 9100,
    val darkTheme: Boolean = true
)

class CutterRepository(
    private val context: Context,
    private val db: CutterDatabase
) {
    private val materialDao = db.materialPresetDao()
    private val projectDao = db.savedProjectDao()
    private val historyDao = db.cutJobHistoryDao()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val elementListType = Types.newParameterizedType(List::class.java, DesignElement::class.java)
    private val elementsAdapter = moshi.adapter<List<DesignElement>>(elementListType)

    private object Keys {
        val MAT_WIDTH = floatPreferencesKey("mat_width_mm")
        val MAT_HEIGHT = floatPreferencesKey("mat_height_mm")
        val GRID_STEP = floatPreferencesKey("grid_step_mm")
        val SNAP_GRID = booleanPreferencesKey("snap_to_grid")
        val PROTOCOL = stringPreferencesKey("default_protocol")
        val BAUD_RATE = intPreferencesKey("baud_rate")
        val WIFI_HOST = stringPreferencesKey("wifi_host")
        val WIFI_PORT = intPreferencesKey("wifi_port")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    val preferencesFlow: Flow<MachinePreferences> = context.cutterPrefsDataStore.data.map { prefs ->
        val protoName = prefs[Keys.PROTOCOL] ?: PlotterProtocol.HPGL.name
        val proto = PlotterProtocol.entries.firstOrNull { it.name == protoName } ?: PlotterProtocol.HPGL
        MachinePreferences(
            matWidthMm = prefs[Keys.MAT_WIDTH] ?: 300f,
            matHeightMm = prefs[Keys.MAT_HEIGHT] ?: 300f,
            gridStepMm = prefs[Keys.GRID_STEP] ?: 10f,
            snapToGrid = prefs[Keys.SNAP_GRID] ?: true,
            defaultProtocol = proto,
            baudRate = prefs[Keys.BAUD_RATE] ?: 9600,
            wifiHost = prefs[Keys.WIFI_HOST] ?: "192.168.1.100",
            wifiPort = prefs[Keys.WIFI_PORT] ?: 9100,
            darkTheme = prefs[Keys.DARK_THEME] ?: true
        )
    }

    val materialPresetsFlow: Flow<List<MaterialPreset>> =
        materialDao.observeAll().map { list -> list.map { it.toDomain() } }

    val savedProjectsFlow: Flow<List<SavedProjectSummary>> =
        projectDao.observeAll().map { list -> list.map { it.toDomain() } }

    val cutJobHistoryFlow: Flow<List<CutJobRecord>> =
        historyDao.observeRecent().map { list -> list.map { it.toDomain() } }

    suspend fun ensureSeededMaterials() {
        if (materialDao.count() == 0) {
            val defaults = listOf(
                MaterialPresetEntity(
                    nameAr = "فينيل لاصق لامع",
                    categoryAr = "لافتات وإعلانات",
                    forceGrams = 95,
                    speedMmPerSec = 250,
                    passes = 1,
                    bladeOffsetMm = 0.25f,
                    isBuiltIn = true
                ),
                MaterialPresetEntity(
                    nameAr = "فينيل حراري (HTV)",
                    categoryAr = "طباعة ملابس",
                    forceGrams = 115,
                    speedMmPerSec = 220,
                    passes = 1,
                    bladeOffsetMm = 0.25f,
                    isBuiltIn = true
                ),
                MaterialPresetEntity(
                    nameAr = "ورق ستيكر مطبوع",
                    categoryAr = "ملصقات",
                    forceGrams = 140,
                    speedMmPerSec = 180,
                    passes = 1,
                    bladeOffsetMm = 0.25f,
                    isBuiltIn = true
                ),
                MaterialPresetEntity(
                    nameAr = "فينيل عاكس للسلامة",
                    categoryAr = "إشارات طرق",
                    forceGrams = 230,
                    speedMmPerSec = 120,
                    passes = 2,
                    bladeOffsetMm = 0.45f,
                    isBuiltIn = true
                ),
                MaterialPresetEntity(
                    nameAr = "كرتون خفيف 200 جم",
                    categoryAr = "علب وتغليف",
                    forceGrams = 260,
                    speedMmPerSec = 110,
                    passes = 2,
                    bladeOffsetMm = 0.45f,
                    isBuiltIn = true
                ),
                MaterialPresetEntity(
                    nameAr = "فينيل فلوك مخملي",
                    categoryAr = "تيشيرتات بارزة",
                    forceGrams = 175,
                    speedMmPerSec = 150,
                    passes = 1,
                    bladeOffsetMm = 0.35f,
                    isBuiltIn = true
                )
            )
            materialDao.insertAll(defaults)
        }
    }

    suspend fun saveMaterialPreset(preset: MaterialPreset) {
        if (preset.id == 0L) {
            materialDao.insert(preset.toEntity())
        } else {
            materialDao.update(preset.toEntity())
        }
    }

    suspend fun deleteCustomMaterial(id: Long) {
        materialDao.deleteCustom(id)
    }

    suspend fun saveProject(title: String, elements: List<DesignElement>, mat: CuttingMatConfig) {
        val json = elementsAdapter.toJson(elements)
        projectDao.insert(
            SavedProjectEntity(
                title = title.ifBlank { "تصميم بدون عنوان" },
                elementCount = elements.size,
                matWidthMm = mat.widthMm,
                matHeightMm = mat.heightMm,
                elementsJson = json,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun deserializeElements(json: String): List<DesignElement> {
        return try {
            elementsAdapter.fromJson(json) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun deleteProject(id: Long) {
        projectDao.deleteById(id)
    }

    suspend fun logCutJob(record: CutJobRecord) {
        historyDao.insert(
            CutJobHistoryEntity(
                jobName = record.jobName,
                elementCount = record.elementCount,
                totalCutLengthMm = record.totalCutLengthMm,
                protocolName = record.protocolName,
                materialName = record.materialName,
                timestamp = record.timestamp,
                status = record.status
            )
        )
    }

    suspend fun clearCutHistory() {
        historyDao.clearAll()
    }

    suspend fun updateMatDimensions(widthMm: Float, heightMm: Float) {
        context.cutterPrefsDataStore.edit {
            it[Keys.MAT_WIDTH] = widthMm
            it[Keys.MAT_HEIGHT] = heightMm
        }
    }

    suspend fun updateMatSettings(widthMm: Float, heightMm: Float, gridStepMm: Float) {
        context.cutterPrefsDataStore.edit {
            it[Keys.MAT_WIDTH] = widthMm
            it[Keys.MAT_HEIGHT] = heightMm
            it[Keys.GRID_STEP] = gridStepMm
        }
    }

    suspend fun updateSnapToGrid(enabled: Boolean) {
        context.cutterPrefsDataStore.edit { it[Keys.SNAP_GRID] = enabled }
    }

    suspend fun updateSerialDefaults(protocol: PlotterProtocol, baudRate: Int) {
        context.cutterPrefsDataStore.edit {
            it[Keys.PROTOCOL] = protocol.name
            it[Keys.BAUD_RATE] = baudRate
        }
    }

    suspend fun updateWifiDefaults(hostIp: String, port: Int) {
        context.cutterPrefsDataStore.edit {
            it[Keys.WIFI_HOST] = hostIp
            it[Keys.WIFI_PORT] = port
        }
    }

    suspend fun updateDarkTheme(dark: Boolean) {
        context.cutterPrefsDataStore.edit { it[Keys.DARK_THEME] = dark }
    }
}
