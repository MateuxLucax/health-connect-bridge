package io.github.mateuxlucax.healthconnectbridge

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ServerSocket

class ConnectionTestTest {
    private val server = FakeServer()

    /** Accepts connections (in the backlog) but never answers, until closed. */
    private val hanging = ServerSocket(0)
    private val hangingUrl = "http://127.0.0.1:${hanging.localPort}"

    @After
    fun tearDown() {
        server.close()
        hanging.close()
    }

    @Test
    fun supersededTestDoesNotOverwriteTheNewerResult() = runBlocking {
        val test = ConnectionTest(this)
        test.run(hangingUrl, "t")
        delay(300)
        test.run(server.url, "t")
        withTimeout(10_000) { while (test.result?.ok == null) delay(20) }
        assertEquals(true, test.result?.ok)

        hanging.close() // the first request now fails
        delay(1_000)
        assertEquals(true, test.result?.ok)
    }

    @Test
    fun clearedTestStaysCleared() = runBlocking {
        val test = ConnectionTest(this)
        test.run(hangingUrl, "t")
        delay(300)
        test.clear()
        hanging.close()
        delay(1_000)
        assertNull(test.result)
    }

    @Test
    fun invalidUrlFailsWithoutARequest() = runBlocking {
        val test = ConnectionTest(this)
        test.run("ftp://example.com", "t")
        withTimeout(10_000) { while (test.result?.ok == null) delay(20) }
        assertEquals(SyncError(SyncError.Kind.INVALID_URL), test.result?.error)
    }
}
