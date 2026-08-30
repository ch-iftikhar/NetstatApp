package com.yos.netstatapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class NetworkLogAdapter : RecyclerView.Adapter<NetworkLogAdapter.ViewHolder>() {

    private var items: List<NetworkLog> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun submitList(newItems: List<NetworkLog>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvRx: TextView = view.findViewById(R.id.tvRx)
        val tvTx: TextView = view.findViewById(R.id.tvTx)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_network_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = items[position]
        holder.tvTime.text = timeFormat.format(Date(log.timestamp))
        holder.tvRx.text = MonitorService.formatRate(log.rxRate)
        holder.tvTx.text = MonitorService.formatRate(log.txRate)

        holder.itemView.setBackgroundColor(
            if (position % 2 == 0) Color.parseColor("#1E3A5F") else Color.parseColor("#16283D")
        )
    }

    override fun getItemCount(): Int = items.size
}
