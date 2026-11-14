package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialPresetDao {
    @Query("SELECT * FROM material_presets ORDER BY isBuiltIn DESC, id ASC")
    fun observeAll(): Flow<List<MaterialPresetEntity>>

    @Query("SELECT COUNT(*) FROM material_presets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MaterialPresetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MaterialPresetEntity): Long

    @Update
    suspend fun update(item: MaterialPresetEntity)

    @Query("DELETE FROM material_presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustom(id: Long)
}

@Dao
interface SavedProjectDao {
    @Query("SELECT * FROM saved_projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SavedProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: SavedProjectEntity): Long

    @Query("DELETE FROM saved_projects WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CutJobHistoryDao {
    @Query("SELECT * FROM cut_job_history ORDER BY timestamp DESC LIMIT 40")
    fun observeRecent(): Flow<List<CutJobHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: CutJobHistoryEntity): Long

    @Query("DELETE FROM cut_job_history")
    suspend fun clearAll()
}
