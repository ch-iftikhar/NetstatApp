package com.yos.netstatapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AppUsageAdapter : ListAdapter<AppUsageItem, AppUsageAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppUsageItem>() {
            override fun areItemsTheSame(old: AppUsageItem, new: AppUsageItem) = old.uid == new.uid
            override fun areContentsTheSame(old: AppUsageItem, new: AppUsageItem) =
                old.rxBytes == new.rxBytes && old.txBytes == new.txBytes && old.label == new.label
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivAppIcon)
        val name: TextView = view.findViewById(R.id.tvAppName)
        val usage: TextView = view.findViewById(R.id.tvAppUsage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.icon.setImageDrawable(item.icon)
        holder.name.text = item.label
        holder.usage.text =
            "\u2193${Formatters.formatBytes(item.rxBytes)}   \u2191${Formatters.formatBytes(item.txBytes)}"
    }
}
