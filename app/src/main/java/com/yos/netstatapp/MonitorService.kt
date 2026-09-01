package com.yos.netstatapp

import android.app.*
import android.content.Intent
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

class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "netstat_monitor_channel"
        const val NOTIF_ID = 1001

        // kept as thin pass-throughs so other files (adapters etc.) that already call
        // MonitorService.formatRate / formatBytes don't need updating
        fun formatRate(bytesPerSec: Long) = Formatters.formatRate(bytesPerSec)
        fun formatBytes(bytes: Long) = Formatters.formatBytes(bytes)
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
        startForeground(NOTIF_ID, buildNotification(0, 0, NotificationIconRenderer.createDominantIcon(this, 0, 0)))

        serviceScope.launch {
            val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            logDao.deleteOlderThan(cutoff)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.post(pollRunnable)
        return START_STICKY
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

        val report = "RX: ${Formatters.formatRate(rxRate)}\nTX: ${Formatters.formatRate(txRate)}\nTotal RX: ${Formatters.formatBytes(nowRx)}\nTotal TX: ${Formatters.formatBytes(nowTx)}"
        NetworkDataBus.postUpdate(report)

        val icon = NotificationIconRenderer.createDominantIcon(this, rxRate, txRate)
        notificationManager.notify(NOTIF_ID, buildNotification(rxRate, txRate, icon))

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

    // plain system font, tighter gap between the down/up columns
    private fun buildNotification(rxRate: Long, txRate: Long, icon: IconCompat): Notification {
        val title = "\u2193 ${Formatters.formatFixedColumn(rxRate)}   \u2191 ${Formatters.formatFixedColumn(txRate)}"

        val sessionRx = TrafficStats.getTotalRxBytes() - sessionStartRx
        val sessionTx = TrafficStats.getTotalTxBytes() - sessionStartTx
        val bottomLine = "Session \u2193${Formatters.formatBytes(sessionRx)}  \u2191${Formatters.formatBytes(sessionTx)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(bottomLine)
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
