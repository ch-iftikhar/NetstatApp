package com.yos.netstatapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun getForDate(date: String): DailyUsage?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: DailyUsage)

    @Query("SELECT * FROM daily_usage WHERE date LIKE :monthPrefix || '%' ORDER BY date DESC")
    fun getMonthRows(monthPrefix: String): Flow<List<DailyUsage>>
}
