package io.github.mateuxlucax.healthconnectbridge

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private lateinit var status: TextView
    private lateinit var batteryButton: Button

    private val requestPermissions =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            refreshStatus()
        }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)
        status = findViewById(R.id.status)
        batteryButton = findViewById(R.id.battery)

        val serverUrl = findViewById<EditText>(R.id.server_url).apply { setText(settings.serverUrl) }
        val token = findViewById<EditText>(R.id.token).apply { setText(settings.token) }
        val backfillDays = findViewById<EditText>(R.id.backfill_days)

        findViewById<Button>(R.id.save).setOnClickListener {
            settings.serverUrl = serverUrl.text.toString().trim()
            settings.token = token.text.toString().trim()
            toast(R.string.saved)
        }
        findViewById<Button>(R.id.grant).setOnClickListener {
            if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
                toast(R.string.hc_unavailable)
                return@setOnClickListener
            }
            requestPermissions.launch(HealthSync.requestedPermissions(HealthConnectClient.getOrCreate(this)))
        }
        findViewById<Button>(R.id.sync_now).setOnClickListener {
            SyncWorker.syncNow(this)
            toast(R.string.sync_enqueued)
        }
        findViewById<Button>(R.id.backfill).setOnClickListener {
            val days = backfillDays.text.toString().toIntOrNull()
            if (days == null || days <= 0) {
                toast(R.string.invalid_days)
            } else {
                SyncWorker.backfill(this, days)
                toast(R.string.sync_enqueued)
            }
        }
        batteryButton.setOnClickListener { requestBatteryExemption() }

        SyncWorker.schedulePeriodic(this)
    }

    override fun onResume() {
        super.onResume()
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        refreshStatus()
    }

    override fun onPause() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onPause()
    }

    // Sideloaded app: the direct exemption request is fine (no Play policy applies).
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        startActivity(Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun refreshStatus() {
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        batteryButton.visibility = if (exempt) View.GONE else View.VISIBLE

        lifecycleScope.launch {
            val lines = mutableListOf<String>()
            when (HealthConnectClient.getSdkStatus(this@MainActivity)) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    lines += getString(R.string.status_hc_available)
                    val client = HealthConnectClient.getOrCreate(this@MainActivity)
                    val requested = HealthSync.requestedPermissions(client)
                    val granted = client.permissionController.getGrantedPermissions()
                    lines += getString(R.string.status_permissions, requested.count { it in granted }, requested.size)
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    lines += getString(R.string.status_hc_update_required)
                else -> lines += getString(R.string.status_hc_unavailable)
            }
            val lastSuccess = settings.lastSuccessMs
            lines += getString(
                R.string.status_last_success,
                if (lastSuccess == 0L) getString(R.string.never)
                else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(lastSuccess)),
            )
            lines += getString(R.string.status_last_error, settings.lastError ?: getString(R.string.none))
            status.text = lines.joinToString("\n")
        }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
