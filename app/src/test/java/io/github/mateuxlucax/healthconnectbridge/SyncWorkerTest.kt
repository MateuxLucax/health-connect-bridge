package io.github.mateuxlucax.healthconnectbridge

import androidx.work.ListenableWorker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        settings.lastError = "old error"
        val before = System.currentTimeMillis()
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Success)
        assertNull(settings.lastError)
        assertTrue(settings.lastSuccessMs >= before)
    }

    @Test
    fun setupErrorFails() {
        val result = runWith { throw SetupException("no permissions") }
        assertTrue(result is ListenableWorker.Result.Failure)
        assertNotNull(settings.lastError)
    }

    @Test
    fun unauthorizedFails() {
        server.status = { 401 }
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Failure)
        assertNotNull(settings.lastError)
        assertEquals(0L, settings.lastSuccessMs)
    }

    @Test
    fun otherClientErrorFailsAndIsShown() {
        server.status = { 400 }
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Failure)
        assertNotNull(settings.lastError)
    }

    @Test
    fun serverErrorRetries() {
        server.status = { 503 }
        val result = runWith(uploadOne)
        assertTrue(result is ListenableWorker.Result.Retry)
        assertNotNull(settings.lastError)
    }

    @Test
    fun networkErrorRetries() {
        val closedPort = ServerSocket(0).use { it.localPort }
        val result = runWith {
            Uploader.upload("http://127.0.0.1:$closedPort", "t", listOf(Sample("m", mapOf("source" to "a"), 1, 1L)))
        }
        assertTrue(result is ListenableWorker.Result.Retry)
        assertNotNull(settings.lastError)
    }
}
