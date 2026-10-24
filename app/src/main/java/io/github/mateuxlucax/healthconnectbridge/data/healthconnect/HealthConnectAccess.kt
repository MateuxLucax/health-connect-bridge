package io.github.mateuxlucax.healthconnectbridge.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import io.github.mateuxlucax.healthconnectbridge.model.SetupException
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import kotlin.reflect.KClass

/** Health Connect client, the record types this device supports and the permissions the app asks for. */
object HealthConnectAccess {

    /** The Health Connect client, or a [SetupException] when Health Connect isn't available. */
    fun client(context: Context): HealthConnectClient {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            throw SetupException(SyncError.Kind.HEALTH_CONNECT_UNAVAILABLE)
        }
        return HealthConnectClient.getOrCreate(context)
    }

    /** Record types this device supports (skin temperature needs a recent Health Connect). */
    fun supportedTypes(client: HealthConnectClient): List<KClass<out Record>> {
        val skinTemperature = client.features.getFeatureStatus(
            HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE
        ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        return RECORD_TYPES.filter { it != SkinTemperatureRecord::class || skinTemperature }
    }

    /** Every permission the app asks for: one read per supported type, plus background and history. */
    fun requestedPermissions(client: HealthConnectClient): Set<String> = buildSet {
        supportedTypes(client).forEach { add(HealthPermission.getReadPermission(it)) }
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        }
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        ) {
            add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        }
    }
}
