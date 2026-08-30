package com.yos.netstatapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DailyUsageActivity : AppCompatActivity() {

    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_daily_usage)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val tvWeekTotal = findViewById<TextView>(R.id.tvWeekTotal)
        val tvMonthTotal = findViewById<TextView>(R.id.tvMonthTotal)
        val adapter = DailyUsageAdapter()
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
}
