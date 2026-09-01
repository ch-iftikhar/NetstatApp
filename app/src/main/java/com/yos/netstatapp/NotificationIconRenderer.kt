package com.yos.netstatapp

import android.content.Context
import androidx.core.graphics.drawable.IconCompat
import android.content.res.Resources

object NotificationIconRenderer {

    // Exact port of DataMonitor's icon name resolution logic (LiveNetworkMonitor.java).
    // Uses their actual pre-baked ic_signal_* PNG assets (GPLv3, see NOTICE.md) instead
    // of runtime Canvas drawing — this is why their icon always looks pixel-perfect.
    fun createDominantIcon(context: Context, downRate: Long, upRate: Long): IconCompat {
        val value = if (downRate >= upRate) downRate else upRate
        val speedText = Formatters.formatDataMonitorStyle(value)

        val parts = speedText.split(" ")
        val numberPart = parts[0]
        val unitPart = parts.getOrElse(1) { "KB/s" }

        val networkType = if (unitPart == "MB/s") "mb_" else "kb_"
        var iconSuffix = numberPart.replace(".", "_").replace(",", "_")

        if (!iconSuffix.contains("_")) {
            if (networkType == "mb_" && (iconSuffix.toIntOrNull() ?: 0) > 200) {
                iconSuffix = "200_plus"
            }
        }

        val iconName = "ic_signal_$networkType$iconSuffix"

        return try {
            val resId = context.resources.getIdentifier(iconName, "drawable", context.packageName)
            if (resId == 0) throw Resources.NotFoundException()
            IconCompat.createWithResource(context, resId)
        } catch (e: Exception) {
            val fallbackId = context.resources.getIdentifier("ic_signal_kb_0", "drawable", context.packageName)
            IconCompat.createWithResource(context, fallbackId)
        }
    }
}
