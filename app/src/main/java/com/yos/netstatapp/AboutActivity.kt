package com.yos.netstatapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AboutActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) exportDatabase(uri)
    }

    private val exportTxtLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) exportTxt(uri)
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDatabase(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        tvStatus = findViewById(R.id.tvStatus)
        findViewById<TextView>(R.id.tvVersion).text =
            "Version ${packageManager.getPackageInfo(packageName, 0).versionName}"

        findViewById<Button>(R.id.btnExport).setOnClickListener {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportLauncher.launch("netstat_backup_$stamp.db")
        }

        findViewById<Button>(R.id.btnExportTxt).setOnClickListener {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            exportTxtLauncher.launch("netstat_usage_$stamp.txt")
        }

        findViewById<Button>(R.id.btnImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }
    }

    private fun exportDatabase(destUri: Uri) {
        tvStatus.text = "Exporting..."
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppDatabase.checkpoint(applicationContext)
                    val dbFile = getDatabasePath(AppDatabase.DB_NAME)
                    contentResolver.openOutputStream(destUri)?.use { out ->
                        dbFile.inputStream().use { input -> input.copyTo(out) }
                    }
                }
                tvStatus.text = "Export complete."
            } catch (e: Exception) {
                tvStatus.text = "Export failed: ${e.message}"
            }
        }
    }

    private fun exportTxt(destUri: Uri) {
        tvStatus.text = "Exporting TXT..."
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getInstance(applicationContext).dailyUsageDao()
                    val rows = dao.getAll()
                    val displayFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
                    val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    val sb = StringBuilder()
                    sb.append("NetstatApp — Daily Usage Export\n")
                    sb.append("Generated: ${SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                    sb.append("=".repeat(60)).append("\n\n")
                    sb.append(String.format("%-14s %-12s %-12s %-12s\n", "Date", "Mobile", "WiFi", "Total"))
                    sb.append("-".repeat(60)).append("\n")

                    var totalMobile = 0L
                    var totalWifi = 0L

                    for (row in rows) {
                        val dateLabel = try {
                            displayFormat.format(keyFormat.parse(row.date)!!)
                        } catch (e: Exception) { row.date }
                        sb.append(String.format(
                            "%-14s %-12s %-12s %-12s\n",
                            dateLabel,
                            Formatters.formatBytes(row.mobileBytes),
                            Formatters.formatBytes(row.wifiBytes),
                            Formatters.formatBytes(row.mobileBytes + row.wifiBytes)
                        ))
                        totalMobile += row.mobileBytes
                        totalWifi += row.wifiBytes
                    }

                    sb.append("-".repeat(60)).append("\n")
                    sb.append(String.format(
                        "%-14s %-12s %-12s %-12s\n",
                        "TOTAL",
                        Formatters.formatBytes(totalMobile),
                        Formatters.formatBytes(totalWifi),
                        Formatters.formatBytes(totalMobile + totalWifi)
                    ))

                    contentResolver.openOutputStream(destUri)?.use { out ->
                        out.write(sb.toString().toByteArray())
                    }
                }
                tvStatus.text = "TXT export complete."
            } catch (e: Exception) {
                tvStatus.text = "TXT export failed: ${e.message}"
            }
        }
    }

    private fun importDatabase(srcUri: Uri) {
        tvStatus.text = "Stopping monitor service..."
        lifecycleScope.launch {
            try {
                stopService(Intent(this@AboutActivity, MonitorService::class.java))
                delay(300)

                withContext(Dispatchers.IO) {
                    AppDatabase.closeAndReset()
                    val dbFile = getDatabasePath(AppDatabase.DB_NAME)
                    dbFile.parentFile?.mkdirs()
                    contentResolver.openInputStream(srcUri)?.use { input ->
                        dbFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-shm").delete()
                    AppDatabase.getInstance(applicationContext)
                }

                tvStatus.text = "Import complete. Restarting monitor..."

                val serviceIntent = Intent(this@AboutActivity, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }

                tvStatus.text = "Import complete."
            } catch (e: Exception) {
                tvStatus.text = "Import failed: ${e.message}"
            }
        }
    }
}
