package com.yos.netstatapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DailyUsageAdapter
    private lateinit var tvWeekTotal: TextView
    private lateinit var tvMonthTotal: TextView
    private lateinit var pinnedDate: TextView
    private lateinit var pinnedMobile: TextView
    private lateinit var pinnedWifi: TextView
    private lateinit var pinnedTotal: TextView
    private var serviceStarted = false

    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawerLayout)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            android.R.string.ok, android.R.string.cancel
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(android.graphics.Color.parseColor("#4FC3F7"))

        findViewById<TextView>(R.id.navSpeedLog).setOnClickListener {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, SpeedLogActivity::class.java))
        }
        findViewById<TextView>(R.id.navAbout).setOnClickListener {
            drawerLayout.closeDrawers()
            startActivity(Intent(this, AboutActivity::class.java))
        }

        pinnedDate = findViewById(R.id.pinnedDate)
        pinnedMobile = findViewById(R.id.pinnedMobile)
        pinnedWifi = findViewById(R.id.pinnedWifi)
        pinnedTotal = findViewById(R.id.pinnedTotal)

        recyclerView = findViewById(R.id.recyclerView)
        tvWeekTotal = findViewById(R.id.tvWeekTotal)
        tvMonthTotal = findViewById(R.id.tvMonthTotal)
        adapter = DailyUsageAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val cal = Calendar.getInstance()
        val monthPrefix = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(cal.time)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val emptyMonth = (1..daysInMonth).map { day ->
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, day)
            DailyUsage(date = keyFormat.format(c.time), mobileBytes = 0, wifiBytes = 0)
        }.sortedByDescending { it.date }

        adapter.submitList(emptyMonth)

        val dao = AppDatabase.getInstance(this).dailyUsageDao()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.getMonthRows(monthPrefix).collect { dbRows ->
                    val dbMap = dbRows.associateBy { it.date }
                    val merged = emptyMonth.map { placeholder -> dbMap[placeholder.date] ?: placeholder }
                    adapter.submitList(merged)

                    val todayKey = keyFormat.format(Date())
                    val todayRow = merged.find { it.date == todayKey } ?: DailyUsage(todayKey, 0, 0)
                    pinnedDate.text = "Today"
                    pinnedMobile.text = MonitorService.formatBytes(todayRow.mobileBytes)
                    pinnedWifi.text = MonitorService.formatBytes(todayRow.wifiBytes)
                    pinnedTotal.text = MonitorService.formatBytes(todayRow.mobileBytes + todayRow.wifiBytes)

                    val weekCutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                    val weekCutoffStr = keyFormat.format(weekCutoff.time)
                    val weekTotal = merged.filter { it.date >= weekCutoffStr }
                        .sumOf { it.mobileBytes + it.wifiBytes }
                    val monthTotal = merged.sumOf { it.mobileBytes + it.wifiBytes }

                    tvWeekTotal.text = "Week Total: ${MonitorService.formatBytes(weekTotal)}"
                    tvMonthTotal.text = "Month Total: ${MonitorService.formatBytes(monthTotal)}"
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        drawerLayout.openDrawer(GravityCompat.START)
        return true
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndStartService()
    }

    private fun checkPermissionAndStartService() {
        if (!PermissionUtils.hasUsageAccess(this)) {
            PermissionUtils.requestUsageAccess(this)
        } else if (!serviceStarted) {
            if (!PermissionUtils.isIgnoringBatteryOptimizations(this)) {
                PermissionUtils.requestIgnoreBatteryOptimizations(this)
            }
            serviceStarted = true
            val serviceIntent = Intent(this, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}
