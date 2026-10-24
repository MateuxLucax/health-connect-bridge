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
    /** Background reads are supported but not granted: the periodic sync can't read Health Connect. */
    val backgroundMissing: Boolean = false,
) {
    val healthConnectAvailable get() = healthConnect == HealthConnectClient.SDK_AVAILABLE

    companion object {
        suspend fun load(context: Context): DeviceStatus {
            val batteryExempt = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
            val sdk = HealthConnectClient.getSdkStatus(context)
            if (sdk != HealthConnectClient.SDK_AVAILABLE) return DeviceStatus(sdk, 0, 0, 0, batteryExempt)

            val client = HealthConnectClient.getOrCreate(context)
            val reads = HealthSync.supportedTypes(client).map { HealthPermission.getReadPermission(it) }
            return from(
                requested = HealthSync.requestedPermissions(client),
                reads = reads,
                granted = client.permissionController.getGrantedPermissions(),
                batteryExempt = batteryExempt,
            )
        }

        /** Status of an available Health Connect, from the [requested] and [granted] permissions. */
        fun from(requested: Set<String>, reads: List<String>, granted: Set<String>, batteryExempt: Boolean): DeviceStatus {
            val background = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
            return DeviceStatus(
                healthConnect = HealthConnectClient.SDK_AVAILABLE,
                granted = requested.count { it in granted },
                requested = requested.size,
                grantedReads = reads.count { it in granted },
                batteryExempt = batteryExempt,
                backgroundMissing = background in requested && background !in granted,
            )
        }
    }
}
