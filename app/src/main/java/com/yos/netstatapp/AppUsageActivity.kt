package com.yos.netstatapp

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageItem(
    val uid: Int,
    val label: String,
    val icon: Drawable?,
    val rxBytes: Long,
    val txBytes: Long
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

class AppUsageActivity : AppCompatActivity() {

    private lateinit var adapter: AppUsageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_usage)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = AppUsageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        if (!PermissionUtils.hasUsageAccess(this)) {
            PermissionUtils.requestUsageAccess(this)
            return
        }

        loadTodayUsage()
    }

    override fun onResume() {
        super.onResume()
        if (PermissionUtils.hasUsageAccess(this)) {
            loadTodayUsage()
        }
    }

    private fun loadTodayUsage() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { queryPerAppUsage() }
            adapter.submitList(items)
        }
    }

    private fun queryPerAppUsage(): List<AppUsageItem> {
        val statsManager = getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val pm = packageManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = cal.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageByUid = HashMap<Int, LongArray>()

        fun accumulate(networkType: Int) {
            try {
                val bucket = NetworkStats.Bucket()
                val stats = statsManager.querySummary(networkType, null, startTime, endTime)
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val arr = usageByUid.getOrPut(bucket.uid) { longArrayOf(0L, 0L) }
                    arr[0] += bucket.rxBytes
                    arr[1] += bucket.txBytes
                }
                stats.close()
            } catch (e: Exception) {
                // No data for this transport, or permission not yet effective — skip quietly
            }
        }

        accumulate(ConnectivityManager.TYPE_MOBILE)
        accumulate(ConnectivityManager.TYPE_WIFI)

        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appsByUid = installedApps.associateBy { it.uid }

        return usageByUid.entries
            .filter { it.value[0] + it.value[1] > 0 }
            .map { (uid, bytes) ->
                val appInfo: ApplicationInfo? = appsByUid[uid]
                val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "UID $uid"
                val icon = appInfo?.let { pm.getApplicationIcon(it) }
                AppUsageItem(uid, label, icon, bytes[0], bytes[1])
            }
            .sortedByDescending { it.totalBytes }
    }
}
