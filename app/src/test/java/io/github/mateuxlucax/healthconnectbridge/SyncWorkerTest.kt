package io.github.mateuxlucax.healthconnectbridge

import androidx.work.ListenableWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

/** Outcome mapping of a sync run to WorkManager results, using the real uploader against a local server. */
class SyncWorkerTest {
    private val server = FakeServer()
    private val settings = Settings(InMemoryPrefs())

    @After
    fun tearDown() = server.close()

    private fun runWith(block: suspend () -> Unit): ListenableWorker.Result =
        runBlocking { SyncWorker.runAndReport(settings, block) }

    private val uploadOne: suspend () -> Unit = {
        Uploader.upload(server.url, "t", listOf(Sample("health_steps", mapOf("source" to "a"), 1, 1L)))
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
        assertEquals("Token rejected by server (401).", settings.lastError!!.message(XmlStrings.of(XmlStrings.ENGLISH)))
        assertEquals("Token rejeitado pelo servidor (401).", settings.lastError!!.message(XmlStrings.of(XmlStrings.PORTUGUESE)))
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
            Uploader.upload("http://127.0.0.1:$closedPort", "t", listOf(Sample("m", mapOf("source" to "a"), 1, 1L)))
        }
        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(SyncError.Kind.NETWORK, settings.lastError?.kind)
    }

    @Test
    fun errorSurvivesStorage() {
        settings.lastError = SyncError(SyncError.Kind.SERVER, "HTTP 502")
        assertEquals(SyncError(SyncError.Kind.SERVER, "HTTP 502"), settings.lastError)
        settings.prefs.edit().putString("last_error", "Texto livre de uma versão antiga").apply()
        assertNull(settings.lastError)
    }
}
