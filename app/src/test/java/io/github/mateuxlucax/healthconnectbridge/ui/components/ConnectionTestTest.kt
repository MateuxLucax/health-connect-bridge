package io.github.mateuxlucax.healthconnectbridge.ui.components

import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.testing.FakeServer
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
        withTimeout(10_000) { while (test.result.value?.ok == null) delay(20) }
        assertEquals(true, test.result.value?.ok)

        hanging.close() // the first request now fails
        delay(1_000)
        assertEquals(true, test.result.value?.ok)
    }

    @Test
    fun clearedTestStaysCleared() = runBlocking {
        val test = ConnectionTest(this)
        test.run(hangingUrl, "t")
        delay(300)
        test.clear()
        hanging.close()
        delay(1_000)
        assertNull(test.result.value)
    }

    @Test
    fun invalidUrlFailsWithoutARequest() = runBlocking {
        val test = ConnectionTest(this)
        test.run("ftp://example.com", "t")
        withTimeout(10_000) { while (test.result.value?.ok == null) delay(20) }
        assertEquals(SyncError(SyncError.Kind.INVALID_URL), test.result.value?.error)
    }

    @Test
    fun deniedLocalNetworkFailsWithoutARequest() = runBlocking {
        var checkedUrl: String? = null
        val test = ConnectionTest(this)
        test.run(" ${server.url} ", "t") { checkedUrl = it; false }
        withTimeout(10_000) { while (test.result.value?.ok == null) delay(20) }
        assertEquals(SyncError(SyncError.Kind.LOCAL_NETWORK_PERMISSION), test.result.value?.error)
        assertEquals(server.url, checkedUrl)
        assertEquals(0, server.requests.size)
    }

    @Test
    fun grantedLocalNetworkConnects() = runBlocking {
        val test = ConnectionTest(this)
        test.run(server.url, "t") { true }
        withTimeout(10_000) { while (test.result.value?.ok == null) delay(20) }
        assertEquals(true, test.result.value?.ok)
        assertEquals(1, server.requests.size)
    }
}
