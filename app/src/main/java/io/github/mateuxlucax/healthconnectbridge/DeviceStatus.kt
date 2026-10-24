package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import android.os.PowerManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission

/** Health Connect availability, granted permissions and battery optimization state. */
class DeviceStatus(
    val healthConnect: Int,
    val granted: Int,
    val requested: Int,
    val grantedReads: Int,
    val batteryExempt: Boolean,
) {
    val healthConnectAvailable get() = healthConnect == HealthConnectClient.SDK_AVAILABLE

    companion object {
        suspend fun load(context: Context): DeviceStatus {
            val batteryExempt = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
            val sdk = HealthConnectClient.getSdkStatus(context)
            if (sdk != HealthConnectClient.SDK_AVAILABLE) return DeviceStatus(sdk, 0, 0, 0, batteryExempt)

            val client = HealthConnectClient.getOrCreate(context)
            val requested = HealthSync.requestedPermissions(client)
            val reads = HealthSync.supportedTypes(client).map { HealthPermission.getReadPermission(it) }
            val granted = client.permissionController.getGrantedPermissions()
            return DeviceStatus(
                healthConnect = sdk,
                granted = requested.count { it in granted },
                requested = requested.size,
                grantedReads = reads.count { it in granted },
                batteryExempt = batteryExempt,
            )
        }
    }
}
