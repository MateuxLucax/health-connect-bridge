package io.github.mateuxlucax.healthconnectbridge.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.HealthConnectAccess
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.data.network.LocalNetwork
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads the [DeviceStatus]: Health Connect, battery optimization and local network access. */
class DeviceStatusRepository(context: Context, private val settings: SettingsStore) {
    private val context = context.applicationContext

    suspend fun load(): DeviceStatus {
        val batteryExempt = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
        val localNetworkGranted = localNetworkGranted()
        val sdk = HealthConnectClient.getSdkStatus(context)
        if (sdk != HealthConnectClient.SDK_AVAILABLE) {
            return DeviceStatus(sdk, 0, 0, 0, batteryExempt, localNetworkGranted = localNetworkGranted)
        }

        val client = HealthConnectClient.getOrCreate(context)
        val reads = HealthConnectAccess.supportedTypes(client).map { HealthPermission.getReadPermission(it) }
        return DeviceStatus.from(
            requested = HealthConnectAccess.requestedPermissions(client),
            reads = reads,
            granted = client.permissionController.getGrantedPermissions(),
            batteryExempt = batteryExempt,
            localNetworkGranted = localNetworkGranted,
        )
    }

    private suspend fun localNetworkGranted(): Boolean? {
        if (Build.VERSION.SDK_INT < LocalNetwork.FIRST_SDK) return null
        val url = settings.serverUrl
        val local = withContext(Dispatchers.IO) { LocalNetwork.isLocal(url) }
        return if (local) LocalNetwork.isGranted(context) else null
    }
}
