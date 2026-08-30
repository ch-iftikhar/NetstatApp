package com.yos.netstatapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NetworkLog::class, DailyUsage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkLogDao(): NetworkLogDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        const val DB_NAME = "netstat_db"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration()
                 .build().also { INSTANCE = it }
            }
        }

        fun checkpoint(context: Context) {
            getInstance(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)", emptyArray()).close()
        }

        @Synchronized
        fun closeAndReset() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
