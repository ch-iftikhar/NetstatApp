package com.yos.netstatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_log")
data class NetworkLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val rxRate: Long,
    val txRate: Long,
    val totalRx: Long,
    val totalTx: Long
)
