package io.github.mateuxlucax.healthconnectbridge.model

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission

/**
 * Health Connect availability, granted permissions and battery optimization state.
 * Loaded by data/DeviceStatusRepository.
 */
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
