package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Health Connect availability, granted permissions and battery optimization state. */
class DeviceStatus(
    val healthConnect: Int,
    val granted: Int,
    val requested: Int,
    val grantedReads: Int,
    val batteryExempt: Boolean,
    /** Background reads are supported but not granted: the periodic sync can't read Health Connect. */
    val backgroundMissing: Boolean = false,
    /** Local network access on Android 17+ when the saved server is local; null when it doesn't apply. */
    val localNetworkGranted: Boolean? = null,
) {
    val healthConnectAvailable get() = healthConnect == HealthConnectClient.SDK_AVAILABLE

    companion object {
        suspend fun load(context: Context): DeviceStatus {
            val batteryExempt = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName)
            val localNetworkGranted = localNetworkGranted(context)
            val sdk = HealthConnectClient.getSdkStatus(context)
            if (sdk != HealthConnectClient.SDK_AVAILABLE) {
                return DeviceStatus(sdk, 0, 0, 0, batteryExempt, localNetworkGranted = localNetworkGranted)
            }

            val client = HealthConnectClient.getOrCreate(context)
            val reads = HealthSync.supportedTypes(client).map { HealthPermission.getReadPermission(it) }
            return from(
                requested = HealthSync.requestedPermissions(client),
                reads = reads,
                granted = client.permissionController.getGrantedPermissions(),
                batteryExempt = batteryExempt,
                localNetworkGranted = localNetworkGranted,
            )
        }

        private suspend fun localNetworkGranted(context: Context): Boolean? {
            if (Build.VERSION.SDK_INT < LocalNetwork.FIRST_SDK) return null
            val url = Settings(context).serverUrl
            val local = withContext(Dispatchers.IO) { LocalNetwork.isLocal(url) }
            return if (local) LocalNetwork.isGranted(context) else null
        }

        /** Status of an available Health Connect, from the [requested] and [granted] permissions. */
        fun from(
            requested: Set<String>,
            reads: List<String>,
            granted: Set<String>,
            batteryExempt: Boolean,
            localNetworkGranted: Boolean? = null,
        ): DeviceStatus {
            val background = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
            return DeviceStatus(
                healthConnect = HealthConnectClient.SDK_AVAILABLE,
                granted = requested.count { it in granted },
                requested = requested.size,
                grantedReads = reads.count { it in granted },
                batteryExempt = batteryExempt,
                backgroundMissing = background in requested && background !in granted,
                localNetworkGranted = localNetworkGranted,
            )
        }
    }
}
