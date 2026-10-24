package io.github.mateuxlucax.healthconnectbridge.sync

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.response.ChangesResponse
import androidx.work.ListenableWorker
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.exporter.HttpStatusException
import io.github.mateuxlucax.healthconnectbridge.data.local.LastValues
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.Sample
import io.github.mateuxlucax.healthconnectbridge.model.SetupException
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.testing.FakeHealthConnectClient
import io.github.mateuxlucax.healthconnectbridge.testing.FakeServer
import io.github.mateuxlucax.healthconnectbridge.testing.InMemoryPrefs
import io.github.mateuxlucax.healthconnectbridge.testing.XmlStrings
import io.github.mateuxlucax.healthconnectbridge.ui.format.message
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.SocketTimeoutException

/** Outcome mapping of a sync run to WorkManager results, using the real exporter client against a local server. */
class SyncRunnerTest {
    private val server = FakeServer()
    private val prefs = InMemoryPrefs()
    private val settings = SettingsStore(prefs)
    private val lastValues = LastValues(prefs)
    private val exporter = ExporterClient()

    @After
    fun tearDown() = server.close()

    private fun runWith(block: suspend () -> Unit): ListenableWorker.Result =
        runBlocking { SyncRunner.runAndReport(settings) { block(); 0 } }

    private val uploadOne: suspend () -> Unit = {
        exporter.upload(server.url, "t", listOf(Sample("health_steps", mapOf("source" to "a"), 1, 1L)))
    }

    @Test
    fun success() {
        settings.lastError = SyncError(SyncError.Kind.SERVER, "old error")
        val before = System.currentTimeMillis()
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(settings.lastError)
        assertTrue(settings.lastSuccessMs >= before)
    }

    @Test
    fun setupErrorFails() {
        val result = runWith { throw SetupException(SyncError.Kind.NO_PERMISSIONS) }
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError(SyncError.Kind.NO_PERMISSIONS), settings.lastError)
    }

    @Test
    fun unauthorizedFails() {
        server.status = { 401 }
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError(SyncError.Kind.UNAUTHORIZED), settings.lastError)
        assertEquals("Server rejected the token (401). Check the token.", settings.lastError!!.message(XmlStrings.of(XmlStrings.ENGLISH)))
        assertEquals("Servidor recusou o token (401). Confira o token.", settings.lastError!!.message(XmlStrings.of(XmlStrings.PORTUGUESE)))
        assertEquals(0L, settings.lastSuccessMs)
    }

    @Test
    fun otherClientErrorFailsAndIsShown() {
        server.status = { 400 }
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError.Kind.REJECTED, settings.lastError?.kind)
        assertTrue(settings.lastError!!.detail!!.startsWith("HTTP 400"))
    }

    @Test
    fun timeoutAndRateLimitRetry() {
        for (code in listOf(408, 429)) {
            server.status = { code }
            val result = runWith(uploadOne)
            assertTrue("HTTP $code", result is ListenableWorker.Result.Retry)
            assertEquals(SyncError.Kind.SERVER, settings.lastError?.kind)
        }
    }

    @Test
    fun redirectFailsWithUrlHint() {
        server.status = { 301 }
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError.Kind.REDIRECTED, settings.lastError?.kind)
        assertEquals(1, server.requests.size) // not followed
    }

    @Test
    fun securityExceptionFails() {
        val result = runWith { throw SecurityException("no access") }
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError(SyncError.Kind.PERMISSION_DENIED, "no access"), settings.lastError)
    }

    @Test
    fun unexpectedExceptionRetries() {
        val result = runWith { throw IllegalStateException("rate limited") }
        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(SyncError(SyncError.Kind.OTHER, "IllegalStateException: rate limited"), settings.lastError)
    }

    @Test
    fun serverErrorRetries() {
        server.status = { 503 }
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(SyncError.Kind.SERVER, settings.lastError?.kind)
    }

    @Test
    fun networkErrorRetries() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val result = runWith {
            exporter.upload("http://127.0.0.1:$closedPort", "t", listOf(Sample("m", mapOf("source" to "a"), 1, 1L)))
        }
        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(SyncError.Kind.NETWORK, settings.lastError?.kind)
    }

    @Test
    fun backfillInput_runsBackfill_andReportsItSeparately() {
        settings.serverUrl = server.url
        settings.token = "t"
        settings.lastError = SyncError(SyncError.Kind.UNAUTHORIZED)
        val client = FakeHealthConnectClient(granted = setOf(HealthPermission.getReadPermission(StepsRecord::class)))

        val result = runBlocking { SyncRunner.runSync(settings, HealthSync(settings, lastValues) { client }, 5) }

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(client.readRequests.isNotEmpty())
        assertFalse(client.calls.any { it == "getChangesToken" || it.startsWith("getChanges:") })
        assertEquals(SyncError(SyncError.Kind.UNAUTHORIZED), settings.lastError) // Home's state is untouched
        assertEquals(0L, settings.lastSuccessMs)
        assertNull(settings.lastBackfill!!.error)
        assertNull(settings.changesToken)
    }

    @Test
    fun noBackfillInput_runsIncremental() {
        settings.serverUrl = server.url
        settings.token = "t"
        val client = FakeHealthConnectClient(granted = setOf(HealthPermission.getReadPermission(StepsRecord::class)))
        client.issuedTokens += "t0"
        client.changesHandler = { ChangesResponse(emptyList(), "t1", false, false) }

        val result = runBlocking { SyncRunner.runSync(settings, HealthSync(settings, lastValues) { client }, 0) }

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals("t1", settings.changesToken)
        assertTrue(settings.lastSuccessMs > 0)
        assertNull(settings.lastBackfill)
    }

    @Test
    fun failedBackfill_isOnlyReportedAsBackfillResult() {
        val result = runBlocking {
            SyncRunner.runAndReport(settings, backfill = true) { throw HttpStatusException(500, "HTTP 500") }
        }
        assertTrue(result is ListenableWorker.Result.Retry)
        assertNull(settings.lastError)
        assertEquals(SyncError.Kind.SERVER, settings.lastBackfill!!.error?.kind)
    }

    @Test
    fun skippedRecords_dontFailABackfill_andAreReported() {
        val result = runBlocking { SyncRunner.runAndReport(settings, backfill = true) { 2 } }

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(settings.lastBackfill!!.error)
        assertEquals(2, settings.lastBackfill!!.skipped)
        assertEquals(2, settings.currentSyncState().lastBackfill!!.skipped)
    }

    @Test
    fun skippedRecords_dontFailAnIncrementalSync_andAreReported() {
        settings.lastError = SyncError(SyncError.Kind.SERVER, "old error")

        val result = runBlocking { SyncRunner.runAndReport(settings) { 3 } }

        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(settings.lastError)
        assertTrue(settings.lastSuccessMs > 0)
        assertEquals(3, settings.currentSyncState().lastSkipped)

        // The next clean run clears the note.
        runBlocking { SyncRunner.runAndReport(settings) { 0 } }
        assertEquals(0, settings.lastSkipped)
    }

    @Test
    fun errorSurvivesStorage() {
        settings.lastError = SyncError(SyncError.Kind.SERVER, "HTTP 502")
        assertEquals(SyncError(SyncError.Kind.SERVER, "HTTP 502"), settings.lastError)
        prefs.edit().putString("last_error", "Texto livre de uma versão antiga").apply()
        assertNull(settings.lastError)
    }

    @Test
    fun blockedLocalNetwork_failsFastWithoutConnecting() {
        settings.serverUrl = server.url
        settings.token = "t"
        val client = FakeHealthConnectClient(granted = setOf(HealthPermission.getReadPermission(StepsRecord::class)))
        var checkedUrl: String? = null
        val sync = HealthSync(settings, lastValues, localNetworkBlocked = { checkedUrl = it; true }) { client }

        val result = runBlocking { SyncRunner.runSync(settings, sync, 0) }

        assertTrue(result is ListenableWorker.Result.Failure) // not retried: only the user can fix it
        assertEquals(SyncError(SyncError.Kind.LOCAL_NETWORK_PERMISSION), settings.lastError)
        assertEquals(server.url, checkedUrl)
        assertTrue(client.calls.isEmpty())
        assertTrue(server.requests.isEmpty())
        assertEquals(
            "Local network permission needed. Open the app to grant it.",
            settings.lastError!!.message(XmlStrings.of(XmlStrings.ENGLISH)),
        )
        assertEquals(
            "Permissão de rede local necessária. Abra o app para concedê-la.",
            settings.lastError!!.message(XmlStrings.of(XmlStrings.PORTUGUESE)),
        )
    }

    @Test
    fun blockedLocalNetwork_failsBackfillToo() {
        settings.serverUrl = server.url
        settings.token = "t"
        val client = FakeHealthConnectClient(granted = setOf(HealthPermission.getReadPermission(StepsRecord::class)))
        val result = runBlocking { SyncRunner.runSync(settings, HealthSync(settings, lastValues, localNetworkBlocked = { true }) { client }, 5) }
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError.Kind.LOCAL_NETWORK_PERMISSION, settings.lastBackfill!!.error?.kind)
    }

    @Test
    fun connectTimeout_isLocalNetworkPermission_whenBlocked() {
        val result = runBlocking {
            SyncRunner.runAndReport(settings, localNetworkBlocked = { true }) { throw SocketTimeoutException("connect timed out") }
        }
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError(SyncError.Kind.LOCAL_NETWORK_PERMISSION), settings.lastError)
    }

    @Test
    fun connectRefused_isLocalNetworkPermission_whenBlocked() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val result = runBlocking {
            SyncRunner.runAndReport(settings, localNetworkBlocked = { true }) {
                exporter.upload("http://127.0.0.1:$closedPort", "t", listOf(Sample("m", mapOf("source" to "a"), 1, 1L)))
                0
            }
        }
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals(SyncError.Kind.LOCAL_NETWORK_PERMISSION, settings.lastError?.kind)
    }

    @Test
    fun connectTimeout_staysNetworkError_whenNotBlocked() {
        val result = runBlocking {
            SyncRunner.runAndReport(settings, localNetworkBlocked = { false }) { throw SocketTimeoutException("connect timed out") }
        }
        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(SyncError.Kind.NETWORK, settings.lastError?.kind)
    }

    @Test
    fun otherErrors_dontCheckTheLocalNetwork() {
        var checked = false
        server.status = { 401 }
        runBlocking { SyncRunner.runAndReport(settings, localNetworkBlocked = { checked = true; true }, sync = { uploadOne(); 0 }) }
        assertEquals(SyncError(SyncError.Kind.UNAUTHORIZED), settings.lastError)
        assertFalse(checked)
    }
}
