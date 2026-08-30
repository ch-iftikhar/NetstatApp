package com.yos.netstatapp

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "netstat_monitor_channel"
        const val NOTIF_ID = 1001

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

        fun formatSig3(value: Double): String {
            return when {
                value >= 100 -> value.roundToInt().toString()
                value >= 10 -> String.format("%.1f", value)
                else -> String.format("%.2f", value)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pollIntervalMs = 1000L
    private val dbWriteIntervalMs = 5000L
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private var lastRx = TrafficStats.getTotalRxBytes()
    private var lastTx = TrafficStats.getTotalTxBytes()
    private var lastMobileRx = TrafficStats.getMobileRxBytes()
    private var lastMobileTx = TrafficStats.getMobileTxBytes()
    private var lastTime = System.currentTimeMillis()
    private var lastDbWrite = 0L

    private val sessionStartRx = TrafficStats.getTotalRxBytes()
    private val sessionStartTx = TrafficStats.getTotalTxBytes()

    private lateinit var notificationManager: NotificationManager
    private lateinit var logDao: NetworkLogDao
    private lateinit var dailyDao: DailyUsageDao

    private val pollRunnable = object : Runnable {
        override fun run() {
            pollNetworkStats()
            handler.postDelayed(this, pollIntervalMs)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        val db = AppDatabase.getInstance(this)
        logDao = db.networkLogDao()
        dailyDao = db.dailyUsageDao()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("↓ 0 B/s   ↑ 0 B/s", "Session: 0 B", createDominantIcon(0, 0)))

        serviceScope.launch {
            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            logDao.deleteOlderThan(cutoff)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(pollRunnable)
        return START_STICKY
    }

    // ALPHA_8 mask bitmap — the format Android notification small icons actually expect,
    // matches how real FOSS traffic monitors (NetMonitor, Data Monitor) render their icons.
    private fun createDominantIcon(downRate: Long, upRate: Long): IconCompat {
        val value = if (downRate >= upRate) downRate else upRate

        val units = arrayOf("B", "K", "M", "G")
        var v = value.toDouble()
        var unitIndex = 0
        while (abs(v) >= 1024.0 && unitIndex < units.size - 1) {
            v /= 1024.0; unitIndex++
        }
        val numberText = formatSig3(v)
        val unitText = units[unitIndex]

        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)

        val typeface = try {
            Typeface.create("sans-serif-black", Typeface.BOLD)
        } catch (e: Exception) {
            Typeface.DEFAULT_BOLD
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = size * 0.02f
            this.typeface = typeface
        }

        val bounds = Rect()
        val margin = size * 0.03f

        // unit: fixed proportion, bottom-anchored
        val unitSize = size * 0.19f
        paint.textSize = unitSize
        paint.getTextBounds(unitText, 0, unitText.length, bounds)
        val unitBaselineY = size - margin
        val unitTopY = unitBaselineY - bounds.height() - (size * 0.02f)

        // number: auto-fit into remaining space above the unit, hard-capped so it can never overflow
        val numberTargetWidth = size * 0.94f
        val numberMaxHeight = (unitTopY - margin).coerceAtLeast(10f)
        var numberSize = 12f
        while (numberSize < 400f) {
            paint.textSize = numberSize
            paint.getTextBounds(numberText, 0, numberText.length, bounds)
            if (bounds.width() >= numberTargetWidth || bounds.height() >= numberMaxHeight) break
            numberSize += 1f
        }
        // step back one increment to guarantee we're inside bounds, not right at the edge
        numberSize -= 1f
        paint.textSize = numberSize.coerceAtLeast(12f)
        paint.getTextBounds(numberText, 0, numberText.length, bounds)
        val numberBaselineY = margin + bounds.height()

        canvas.drawText(numberText, size / 2f, numberBaselineY, paint)
        paint.textSize = unitSize
        canvas.drawText(unitText, size / 2f, unitBaselineY, paint)

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun pollNetworkStats() {
        val nowRx = TrafficStats.getTotalRxBytes()
        val nowTx = TrafficStats.getTotalTxBytes()
        val nowMobileRx = TrafficStats.getMobileRxBytes()
        val nowMobileTx = TrafficStats.getMobileTxBytes()
        val now = System.currentTimeMillis()

        val elapsedSec = (now - lastTime) / 1000.0
        val rxDelta = nowRx - lastRx
        val txDelta = nowTx - lastTx
        val rxRate = if (elapsedSec > 0) (rxDelta / elapsedSec).toLong() else 0
        val txRate = if (elapsedSec > 0) (txDelta / elapsedSec).toLong() else 0

        val mobileDelta = (nowMobileRx - lastMobileRx) + (nowMobileTx - lastMobileTx)
        val totalDelta = rxDelta + txDelta
        val wifiDelta = (totalDelta - mobileDelta).coerceAtLeast(0)

        lastRx = nowRx; lastTx = nowTx
        lastMobileRx = nowMobileRx; lastMobileTx = nowMobileTx
        lastTime = now

        val report = "RX: ${formatRate(rxRate)}\nTX: ${formatRate(txRate)}\nTotal RX: ${formatBytes(nowRx)}\nTotal TX: ${formatBytes(nowTx)}"
        NetworkDataBus.postUpdate(report)

        val topLine = "↓ ${formatRate(rxRate)}   ↑ ${formatRate(txRate)}"
        val sessionRx = nowRx - sessionStartRx
        val sessionTx = nowTx - sessionStartTx
        val bottomLine = "Session ↓${formatBytes(sessionRx)}  ↑${formatBytes(sessionTx)}"
        val icon = createDominantIcon(rxRate, txRate)
        notificationManager.notify(NOTIF_ID, buildNotification(topLine, bottomLine, icon))

        if (now - lastDbWrite >= dbWriteIntervalMs) {
            val mobileDeltaSnapshot = mobileDelta
            val wifiDeltaSnapshot = wifiDelta
            lastDbWrite = now
            serviceScope.launch {
                logDao.insert(NetworkLog(timestamp = now, rxRate = rxRate, txRate = txRate, totalRx = nowRx, totalTx = nowTx))

                val today = dateFormat.format(Date(now))
                val existing = dailyDao.getForDate(today)
                val updated = DailyUsage(
                    date = today,
                    mobileBytes = (existing?.mobileBytes ?: 0) + mobileDeltaSnapshot,
                    wifiBytes = (existing?.wifiBytes ?: 0) + wifiDeltaSnapshot
                )
                dailyDao.upsert(updated)
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Network Monitor", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String, icon: IconCompat): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        serviceJob.cancel()
    }
}
