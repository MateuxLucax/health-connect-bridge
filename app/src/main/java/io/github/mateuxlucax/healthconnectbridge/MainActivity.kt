package io.github.mateuxlucax.healthconnectbridge

import android.annotation.SuppressLint
import android.content.Intent
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
import io.github.mateuxlucax.healthconnectbridge.data.DeviceStatusRepository
import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.HealthConnectAccess
import io.github.mateuxlucax.healthconnectbridge.data.local.LastValues
import io.github.mateuxlucax.healthconnectbridge.data.local.Settings
import io.github.mateuxlucax.healthconnectbridge.data.network.LocalNetwork
import io.github.mateuxlucax.healthconnectbridge.sync.SyncScheduler
import io.github.mateuxlucax.healthconnectbridge.ui.App
import io.github.mateuxlucax.healthconnectbridge.ui.AppActions
import io.github.mateuxlucax.healthconnectbridge.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred

/** The app's launcher activity. Its name is public (launcher shortcuts): do not move or rename it. */
class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings

    /** Bumped when the app returns to the foreground or permissions change: screens re-read everything. */
    private var refreshKey by mutableIntStateOf(0)

    /** Bumped when a sync writes state that the screens show. */
    private var syncKey by mutableIntStateOf(0)

    /** Watches the sync state while the activity is resumed. */
    private var syncStateObserver: AutoCloseable? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        val lastValues = LastValues(this)
        val scheduler = SyncScheduler(this, SyncWorker::class.java)
        val deviceStatus = DeviceStatusRepository(this, settings)
        if (settings.onboardingDone) scheduler.schedulePeriodic()
        val actions = AppActions(
            ::requestHealthPermissions,
            ::requestBatteryExemption,
            ::requestLocalNetwork,
            ::openAppSettings,
        )
        setContent {
            AppTheme { App(settings, lastValues, scheduler, deviceStatus, refreshKey, syncKey, actions) }
        }
    }

    override fun onResume() {
        super.onResume()
        syncStateObserver = settings.observeSyncState { syncKey++ }
        refreshKey++
    }

    override fun onPause() {
        syncStateObserver?.close()
        syncStateObserver = null
        super.onPause()
    }

    private fun requestHealthPermissions() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) return
        permissionRequest.launch(HealthConnectAccess.requestedPermissions(HealthConnectClient.getOrCreate(this)))
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
