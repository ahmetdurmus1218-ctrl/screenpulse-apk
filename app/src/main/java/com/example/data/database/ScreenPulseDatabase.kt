package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UsageHistoryEntity::class, BatteryLogEntity::class, BackgroundMediaLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ScreenPulseDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao

    companion object {
        @Volatile
        private var INSTANCE: ScreenPulseDatabase? = null

        // Adds the new background_media_logs table without touching usage_history or
        // battery_logs — existing daily/weekly history and charts survive this update.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `background_media_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): ScreenPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScreenPulseDatabase::class.java,
                    "screenpulse_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // safety net only if the migration above ever fails
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
