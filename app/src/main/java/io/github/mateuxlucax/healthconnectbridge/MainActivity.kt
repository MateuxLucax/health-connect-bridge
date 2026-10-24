package io.github.mateuxlucax.healthconnectbridge

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

/** Actions that need the activity. */
class AppActions(val requestHealthPermissions: () -> Unit, val requestBatteryExemption: () -> Unit)

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings

    /** Bumped when the app returns to the foreground or a setting changes, so screens re-read state. */
    private var refreshKey by mutableIntStateOf(0)

    private val permissionRequest =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { refreshKey++ }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshKey++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        if (settings.onboardingDone) SyncWorker.schedulePeriodic(this)
        val actions = AppActions(::requestHealthPermissions, ::requestBatteryExemption)
        setContent {
            AppTheme { App(settings, refreshKey, actions) }
        }
    }

    override fun onResume() {
        super.onResume()
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        refreshKey++
    }

    override fun onPause() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onPause()
    }

    private fun requestHealthPermissions() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) return
        permissionRequest.launch(HealthSync.requestedPermissions(HealthConnectClient.getOrCreate(this)))
    }

    // Sideloaded app: the direct exemption request is fine (no Play policy applies).
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        startActivity(Intent(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }
}
