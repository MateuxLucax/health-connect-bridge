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

    /**
     * Changes-API sync. Only advances the checkpoint after each successful upload. Returns how many unreadable
     * stretches of records were skipped (see [readRange]).
     */
    suspend fun incremental(): Int {
        val client = client()
        val types = readableTypes(client)
        val typesKey = types.map { it.java.simpleName }.sorted().joinToString(",")
        val skips = Skips()

        val saved = settings.changesToken?.takeIf { settings.changesTypes == typesKey }
        // Whether the token was taken during this run; such a token can't legitimately be expired.
        var fresh = saved == null
        var token = saved ?: initialRead(client, types, typesKey, reinitDays(), skips)

        while (true) {
            val response = try {
                client.getChanges(token)
            } catch (e: IllegalArgumentException) {
                // A changed record Health Connect itself can't build (e.g. startTime == endTime, written by
                // another app). A changes page can't be read around it, so re-read the range with a new token.
                if (fresh) {
                    // Also unreadable with a token from this run: stop here instead of looping; next run retries.
                    skips.count++
                    break
                }
                token = initialRead(client, types, typesKey, reinitDays(), skips)
                fresh = true
                continue
            }
            if (response.changesTokenExpired) {
                check(!fresh) { "Health Connect reported a new changes token as expired" }
                token = initialRead(client, types, typesKey, EXPIRED_TOKEN_DAYS, skips)
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
        return skips.count
    }

    /** Re-reads the last [days] days without touching the checkpoint. Returns the skips, like [incremental]. */
    suspend fun backfill(days: Int): Int {
        val client = client()
        val end = Instant.now()
        val skips = Skips()
        readRange(client, readableTypes(client), end.minus(Duration.ofDays(days.toLong())), end, skips)
        return skips.count
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
        skips: Skips,
    ): String {
        val token = client.getChangesToken(ChangesTokenRequest(types.toSet()))
        val end = Instant.now()
        readRange(client, types, end.minus(Duration.ofDays(days)), end, skips)
        settings.saveChangesToken(token, typesKey)
        return token
    }

    /** Number of unreadable stretches skipped during one run. */
    private class Skips {
        var count = 0
    }

    /**
     * Reads and uploads every record of [types] in [start, end), page by page.
     *
     * Health Connect throws IllegalArgumentException for the whole page when one record in it fails the
     * client library's own checks (e.g. "startTime must be before endTime." for a record some app stored with
     * startTime == endTime). Then the window is split in halves and each is read on its own, so only the
     * smallest window that still fails ([MIN_WINDOW], or any window once [MAX_FAILED_READS] is reached) is
     * skipped. Adjacent skipped windows count as one skip.
     */
    private suspend fun readRange(
        client: HealthConnectClient,
        types: List<KClass<out Record>>,
        start: Instant,
        end: Instant,
        skips: Skips,
    ) {
        for (type in types) {
            var failedReads = 0
            var lastSkippedEnd: Instant? = null

            suspend fun read(from: Instant, to: Instant) {
                if (readPages(client, type, from, to)) return
                failedReads++
                if (Duration.between(from, to) <= MIN_WINDOW || failedReads >= MAX_FAILED_READS) {
                    if (lastSkippedEnd != from) skips.count++
                    lastSkippedEnd = to
                    return
                }
                val middle = from.plus(Duration.between(from, to).dividedBy(2))
                read(from, middle)
                read(middle, to)
            }

            read(start, end)
        }
    }

    /** Reads and uploads all pages of [type] in [from, to); false if Health Connect couldn't build a page. */
    private suspend fun readPages(client: HealthConnectClient, type: KClass<out Record>, from: Instant, to: Instant): Boolean {
        var pageToken: String? = null
        do {
            val response = try {
                @Suppress("UNCHECKED_CAST")
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = type as KClass<Record>,
                        timeRangeFilter = TimeRangeFilter.between(from, to),
                        pageToken = pageToken,
                    )
                )
            } catch (e: IllegalArgumentException) {
                return false
            }
            upload(response.records.flatMap { it.toSamples() })
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        return true
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

        /** Smallest window [readRange] still splits; a failing window this short is skipped. */
        val MIN_WINDOW: Duration = Duration.ofMinutes(1)

        /** Failed reads per type and range after which failing windows are skipped without splitting them. */
        const val MAX_FAILED_READS = 100
    }
}
