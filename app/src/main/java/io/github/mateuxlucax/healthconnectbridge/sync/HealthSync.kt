package io.github.mateuxlucax.healthconnectbridge.sync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.HealthConnectAccess
import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.toSamples
import io.github.mateuxlucax.healthconnectbridge.data.local.LastValues
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.data.network.LocalNetwork
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.Sample
import io.github.mateuxlucax.healthconnectbridge.model.SetupException
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/** Reads Health Connect and uploads mapped samples to the exporter. */
class HealthSync(
    private val settings: SettingsStore,
    private val lastValues: LastValues,
    private val exporter: ExporterClient = ExporterClient(),
    /** Whether connecting to the server URL would be blocked by Android 17 local network protection. */
    private val localNetworkBlocked: suspend (String) -> Boolean = { false },
    private val clientFactory: () -> HealthConnectClient,
) {
    constructor(context: Context, settings: SettingsStore, lastValues: LastValues, exporter: ExporterClient) : this(
        settings,
        lastValues,
        exporter,
        { LocalNetwork.isBlocked(context, it) },
        { HealthConnectAccess.client(context) },
    )

    /** Changes-API sync. Only advances the checkpoint after each successful upload. */
    suspend fun incremental() {
        val client = client()
        val types = readableTypes(client)
        val typesKey = types.map { it.java.simpleName }.sorted().joinToString(",")

        val saved = settings.changesToken?.takeIf { settings.changesTypes == typesKey }
        // Whether the token was taken during this run; such a token can't legitimately be expired.
        var fresh = saved == null
        var token = saved ?: initialRead(client, types, typesKey, reinitDays())

        while (true) {
            val response = client.getChanges(token)
            if (response.changesTokenExpired) {
                check(!fresh) { "Health Connect reported a new changes token as expired" }
                token = initialRead(client, types, typesKey, EXPIRED_TOKEN_DAYS)
                fresh = true
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
        exporter.upload(settings.serverUrl, settings.token, samples)
        // Display cache only: it must never fail a sync whose upload succeeded.
        runCatching { lastValues.record(samples) }
    }

    private suspend fun client(): HealthConnectClient {
        if (settings.serverUrl.isBlank() || settings.token.isBlank()) {
            throw SetupException(SyncError.Kind.NOT_CONFIGURED)
        }
        if (!ServerUrl.isValid(settings.serverUrl)) throw SetupException(SyncError.Kind.INVALID_URL)
        // A worker can't ask for the permission; fail now instead of after a connect timeout.
        if (localNetworkBlocked(settings.serverUrl)) throw SetupException(SyncError.Kind.LOCAL_NETWORK_PERMISSION)
        return clientFactory()
    }

    private suspend fun readableTypes(client: HealthConnectClient): List<KClass<out Record>> {
        val granted = client.permissionController.getGrantedPermissions()
        val types = HealthConnectAccess.supportedTypes(client).filter { HealthPermission.getReadPermission(it) in granted }
        if (types.isEmpty()) throw SetupException(SyncError.Kind.NO_PERMISSIONS)
        return types
    }

    private companion object {
        const val INITIAL_DAYS = 7L
        const val EXPIRED_TOKEN_DAYS = 30L
    }
}
