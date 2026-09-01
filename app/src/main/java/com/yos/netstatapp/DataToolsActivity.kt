package com.yos.netstatapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class DataToolsActivity : AppCompatActivity() {

    // TODO: replace placeholders with your real GitHub URL, email, and feature description
    private val githubUrl = "https://github.com/YOUR_USERNAME/netstatapp"
    private val contactEmail = "your.email@example.com"
    private val description = """
        A lightweight, fully open-source network monitor — built entirely from source,
        no closed dependencies, no bloat.

        Live upload/download speed tracking, per-app and per-network data usage history,
        persistent background monitoring, and full local database export/import — all
        running privately on your device.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_tools)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setTitleTextColor(android.graphics.Color.WHITE)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.tvVersion).text =
            "Version ${packageManager.getPackageInfo(packageName, 0).versionName}"

        findViewById<TextView>(R.id.tvDescription).text = description

        val tvGithub = findViewById<TextView>(R.id.tvGithub)
        tvGithub.text = "GitHub: $githubUrl"
        tvGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)))
        }

        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        tvEmail.text = "Email: $contactEmail"
        tvEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$contactEmail"))
            startActivity(intent)
        }
    }
}
