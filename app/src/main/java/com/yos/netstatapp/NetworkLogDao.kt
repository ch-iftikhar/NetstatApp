package com.yos.netstatapp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkLogDao {
    @Insert
    suspend fun insert(log: NetworkLog)

    @Query("SELECT * FROM network_log ORDER BY timestamp DESC LIMIT 200")
    fun getRecent(): Flow<List<NetworkLog>>

    @Query("DELETE FROM network_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
