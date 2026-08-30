package com.yos.netstatapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class DailyUsageAdapter : RecyclerView.Adapter<DailyUsageAdapter.ViewHolder>() {

    private var items: List<DailyUsage> = emptyList()
    private val displayFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun submitList(newItems: List<DailyUsage>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvMobile: TextView = view.findViewById(R.id.tvMobile)
        val tvWifi: TextView = view.findViewById(R.id.tvWifi)
        val tvTotal: TextView = view.findViewById(R.id.tvTotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_daily_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val todayKey = keyFormat.format(Date())
        val isToday = item.date == todayKey

        holder.tvDate.text = if (isToday) "Today" else {
            try { displayFormat.format(keyFormat.parse(item.date)!!) } catch (e: Exception) { item.date }
        }
        holder.tvMobile.text = MonitorService.formatBytes(item.mobileBytes)
        holder.tvWifi.text = MonitorService.formatBytes(item.wifiBytes)
        holder.tvTotal.text = MonitorService.formatBytes(item.mobileBytes + item.wifiBytes)

        holder.itemView.setBackgroundColor(
            if (position % 2 == 0) Color.parseColor("#1E3A5F") else Color.parseColor("#16283D")
        )

        holder.itemView.foreground = if (isToday) {
            ContextCompat.getDrawable(holder.itemView.context, R.drawable.neon_border_row)
        } else {
            null
        }
    }

    override fun getItemCount(): Int = items.size
}
