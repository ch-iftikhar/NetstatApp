package com.yos.netstatapp

import kotlin.math.abs

object Formatters {

    fun formatRate(bytesPerSec: Long): String {
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSec.toDouble()
        var unitIndex = 0
        while (abs(value) >= 1024.0 && unitIndex < units.size - 1) {
            value /= 1024.0; unitIndex++
        }
        return if (unitIndex == 0) "${value.toLong()} ${units[unitIndex]}"
        else String.format("%.1f %s", value, units[unitIndex])
    }

    fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (abs(value) >= 1024.0 && unitIndex < units.size - 1) {
            value /= 1024.0; unitIndex++
        }
        return if (unitIndex == 0) "${value.toLong()} ${units[unitIndex]}"
        else String.format("%.1f %s", value, units[unitIndex])
    }

    fun formatFixedColumn(bytesPerSec: Long): String {
        val units = arrayOf("B/s ", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSec.toDouble()
        var unitIndex = 0
        while (abs(value) >= 1024.0 && unitIndex < units.size - 1) {
            value /= 1024.0; unitIndex++
        }
        val numText = if (unitIndex == 0) {
            value.toInt().toString().padStart(6)
        } else {
            String.format("%6.2f", value)
        }
        return "$numText ${units[unitIndex]}"
    }

    // Exact port of DataMonitor's formatNetworkSpeed (LiveNetworkMonitor.java) — produces
    // strings like "42 KB/s" or "9.3 MB/s" or "16 MB/s", matching their icon asset naming.
    fun formatDataMonitorStyle(bytesPerSec: Long): String {
        val speedKB = (bytesPerSec / 1024).toInt()
        return when {
            speedKB in 1000 until 1024 -> "1.0 MB/s"
            speedKB >= 1024 -> {
                val speedMB = speedKB / 1024f
                if (speedMB < 10) String.format("%.1f MB/s", speedMB)
                else "${speedKB / 1024} MB/s"
            }
            else -> "$speedKB KB/s"
        }
    }
}
