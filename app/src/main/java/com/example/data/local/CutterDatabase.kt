package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MaterialPresetEntity::class,
        SavedProjectEntity::class,
        CutJobHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CutterDatabase : RoomDatabase() {
    abstract fun materialPresetDao(): MaterialPresetDao
    abstract fun savedProjectDao(): SavedProjectDao
    abstract fun cutJobHistoryDao(): CutJobHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: CutterDatabase? = null

        fun getInstance(context: Context): CutterDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CutterDatabase::class.java,
                    "cutter_pro_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
