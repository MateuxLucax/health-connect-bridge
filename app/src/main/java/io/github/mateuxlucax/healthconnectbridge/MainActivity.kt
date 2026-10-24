package io.github.mateuxlucax.healthconnectbridge

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.CompletableDeferred

/** Actions that need the activity. */
class AppActions(
    val requestHealthPermissions: () -> Unit,
    val requestBatteryExemption: () -> Unit,
    /** Asks for the local network permission (or opens the app's settings once it was denied for good). */
    val requestLocalNetwork: suspend () -> Boolean,
    val openAppSettings: () -> Unit,
)

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings

    /** Bumped when the app returns to the foreground or permissions change: screens re-read everything. */
    private var refreshKey by mutableIntStateOf(0)

    /** Bumped when a sync writes state that the screens show. */
    private var syncKey by mutableIntStateOf(0)

    private val permissionRequest =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { refreshKey++ }

    /** Waiting for the answer to the local network permission request. */
    private var localNetworkResult: CompletableDeferred<Boolean>? = null

    /** Denied with "don't ask again": the system no longer shows the prompt, only the app's settings can grant it. */
    private var localNetworkDeniedForGood = false

    private val localNetworkRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) localNetworkDeniedForGood = !shouldShowRequestPermissionRationale(LocalNetwork.PERMISSION)
        refreshKey++
        localNetworkResult?.complete(granted)
        localNetworkResult = null
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in Settings.SYNC_STATE_KEYS) syncKey++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        if (settings.onboardingDone) SyncWorker.schedulePeriodic(this)
        val actions = AppActions(
            ::requestHealthPermissions,
            ::requestBatteryExemption,
            ::requestLocalNetwork,
            ::openAppSettings,
        )
        setContent {
            AppTheme { App(settings, refreshKey, syncKey, actions) }
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

    private suspend fun requestLocalNetwork(): Boolean {
        if (LocalNetwork.isGranted(this)) return true
        if (localNetworkDeniedForGood) {
            openAppSettings()
            return false
        }
        localNetworkResult?.complete(false)
        val result = CompletableDeferred<Boolean>().also { localNetworkResult = it }
        localNetworkRequest.launch(LocalNetwork.PERMISSION)
        return result.await()
    }

    private fun openAppSettings() {
        startActivity(Intent(ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}
