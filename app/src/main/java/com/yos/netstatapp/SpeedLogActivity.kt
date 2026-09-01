package com.yos.netstatapp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class SpeedLogActivity : AppCompatActivity() {

    private lateinit var tvLiveStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speedlog)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val adapter = NetworkLogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val dao = AppDatabase.getInstance(this).networkLogDao()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dao.getRecent().collect { logs -> adapter.submitList(logs) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NetworkDataBus.setListener { text -> tvLiveStatus.text = text }
    }

    override fun onPause() {
        super.onPause()
        NetworkDataBus.setListener(null)
    }
}
