package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/** Reads Health Connect and uploads mapped samples to the exporter. */
class HealthSync(
    private val settings: Settings,
    private val clientFactory: () -> HealthConnectClient,
) {
    constructor(context: Context, settings: Settings) : this(settings, { defaultClient(context) })

    /** Changes-API sync. Only advances the checkpoint after each successful upload. */
    suspend fun incremental() {
        val client = client()
        val types = readableTypes(client)
        val typesKey = types.map { it.java.simpleName }.sorted().joinToString(",")

        var token = settings.changesToken?.takeIf { settings.changesTypes == typesKey }
            ?: initialRead(client, types, typesKey, reinitDays())

        while (true) {
            val response = client.getChanges(token)
            if (response.changesTokenExpired) {
                token = initialRead(client, types, typesKey, EXPIRED_TOKEN_DAYS)
                continue
            }
            // DeletionChange is ignored: Prometheus cannot delete samples.
            val samples = response.changes
                .filterIsInstance<UpsertionChange>()
                .flatMap { it.record.toSamples() }
            upload(samples)
            settings.saveChangesToken(response.nextChangesToken, typesKey)
            token = response.nextChangesToken
            if (!response.hasMore) break
        }
    }

    /** Re-reads the last [days] days without touching the checkpoint. */
    suspend fun backfill(days: Int) {
        val client = client()
        val end = Instant.now()
        readRange(client, readableTypes(client), end.minus(Duration.ofDays(days.toLong())), end)
    }

    /**
     * Days to read when (re)starting the checkpoint: 7 on a fresh install, but when the set of types changed,
     * everything since the last saved checkpoint (it may be days old after an outage), up to 30.
     */
    private fun reinitDays(): Long {
        val savedMs = settings.changesSavedMs.takeIf { it > 0 } ?: return INITIAL_DAYS
        val days = Duration.ofMillis(System.currentTimeMillis() - savedMs).toDays() + 1
        return days.coerceIn(INITIAL_DAYS, EXPIRED_TOKEN_DAYS)
    }

    /** Takes a changes token first so nothing written during the read is missed, then stores it. */
    private suspend fun initialRead(
        client: HealthConnectClient,
        types: List<KClass<out Record>>,
        typesKey: String,
        days: Long,
    ): String {
        val token = client.getChangesToken(ChangesTokenRequest(types.toSet()))
        val end = Instant.now()
        readRange(client, types, end.minus(Duration.ofDays(days)), end)
        settings.saveChangesToken(token, typesKey)
        return token
    }

    private suspend fun readRange(
        client: HealthConnectClient,
        types: List<KClass<out Record>>,
        start: Instant,
        end: Instant,
    ) {
        for (type in types) {
            var pageToken: String? = null
            do {
                @Suppress("UNCHECKED_CAST")
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = type as KClass<Record>,
                        timeRangeFilter = TimeRangeFilter.between(start, end),
                        pageToken = pageToken,
                    )
                )
                upload(response.records.flatMap { it.toSamples() })
                pageToken = response.pageToken
            } while (!pageToken.isNullOrEmpty())
        }
    }

    private suspend fun upload(samples: List<Sample>) {
        if (samples.isEmpty()) return
        Uploader.upload(settings.serverUrl, settings.token, samples)
        LastValues(settings.prefs).record(samples)
    }

    private fun client(): HealthConnectClient {
        if (settings.serverUrl.isBlank() || settings.token.isBlank()) {
            throw SetupException(SyncError.Kind.NOT_CONFIGURED)
        }
        if (!ServerUrl.isValid(settings.serverUrl)) throw SetupException(SyncError.Kind.INVALID_URL)
        return clientFactory()
    }

    private suspend fun readableTypes(client: HealthConnectClient): List<KClass<out Record>> {
        val granted = client.permissionController.getGrantedPermissions()
        val types = supportedTypes(client).filter { HealthPermission.getReadPermission(it) in granted }
        if (types.isEmpty()) throw SetupException(SyncError.Kind.NO_PERMISSIONS)
        return types
    }

    companion object {
        private const val INITIAL_DAYS = 7L
        private const val EXPIRED_TOKEN_DAYS = 30L

        private fun defaultClient(context: Context): HealthConnectClient {
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
}
