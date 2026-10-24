package io.github.mateuxlucax.healthconnectbridge.data.exporter

import io.github.mateuxlucax.healthconnectbridge.model.Sample
import io.github.mateuxlucax.healthconnectbridge.testing.FakeServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket

class ExporterClientTest {
    private val server = FakeServer()
    private val client = ExporterClient()

    @After
    fun tearDown() = server.close()

    // Compiled against android.jar's org.json, which has keys() but not keySet().
    private fun JSONObject.keyNames(): Set<String> = keys().asSequence().toSet()

    private fun sample(i: Int) =
        Sample("health_steps", mapOf("source" to "com.example.app"), i, 1_700_000_000_000L + i)

    private fun upload(url: String = server.url, samples: List<Sample> = listOf(sample(1))) =
        runBlocking { client.upload(url, "secret-token", samples) }

    private inline fun <reified E : Throwable> assertThrows(block: () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw AssertionError("expected ${E::class.simpleName}, got $e", e)
        }
        fail("expected ${E::class.simpleName}, nothing thrown")
        throw IllegalStateException()
    }

    @Test
    fun postsToSamplesPath_withoutTrailingSlash() {
        upload(server.url)
        assertEquals("POST", server.requests.single().method)
        assertEquals("/api/v1/samples", server.requests.single().path)
    }

    @Test
    fun postsToSamplesPath_withTrailingSlash() {
        upload(server.url + "/")
        assertEquals("/api/v1/samples", server.requests.single().path)
    }

    @Test
    fun sendsBearerTokenAndJsonContentType() {
        upload()
        val headers = server.requests.single().headers
        assertEquals("Bearer secret-token", headers["authorization"])
        assertTrue(headers["content-type"]!!.startsWith("application/json"))
    }

    @Test
    fun bodyMatchesSpec() {
        val samples = listOf(
            Sample("health_heart_rate_bpm", mapOf("source" to "com.example.watch"), 72L, 1_700_000_000_123L),
            Sample(
                "health_sleep_stage_seconds",
                mapOf("source" to "com.example.sleep", "stage" to "deep"),
                1800.5,
                1_700_000_100_000L,
            ),
        )
        upload(samples = samples)

        val json = server.requests.single().json
        assertEquals(setOf("samples"), json.keyNames())
        val array = json.getJSONArray("samples")
        assertEquals(2, array.length())

        val first = array.getJSONObject(0)
        assertEquals(setOf("metric", "labels", "value", "timestamp_ms"), first.keyNames())
        assertEquals("health_heart_rate_bpm", first.getString("metric"))
        assertEquals(setOf("source"), first.getJSONObject("labels").keyNames())
        assertEquals("com.example.watch", first.getJSONObject("labels").getString("source"))
        assertEquals(72.0, first.getDouble("value"), 0.0)
        assertTrue(first.get("value") is Number)
        assertEquals(1_700_000_000_123L, first.getLong("timestamp_ms"))
        assertTrue(first.get("timestamp_ms") is Number)

        val second = array.getJSONObject(1)
        assertEquals("health_sleep_stage_seconds", second.getString("metric"))
        val labels = second.getJSONObject("labels")
        assertEquals(setOf("source", "stage"), labels.keyNames())
        assertEquals("com.example.sleep", labels.getString("source"))
        assertEquals("deep", labels.getString("stage"))
        assertEquals(1800.5, second.getDouble("value"), 0.0)
        assertEquals(1_700_000_100_000L, second.getLong("timestamp_ms"))
    }

    @Test
    fun splitsIntoBatchesOfAtMost5000() {
        val samples = (0 until 12_001).map { sample(it) }
        upload(samples = samples)

        assertEquals(listOf(5000, 5000, 2001), server.requests.map { it.json.getJSONArray("samples").length() })
        val received = server.samples().map { it.getLong("timestamp_ms") }
        assertEquals(samples.map { it.timestampMs }, received)
    }

    @Test
    fun noContentIsSuccess() {
        server.status = { 204 }
        upload()
        assertEquals(1, server.requests.size)
    }

    @Test
    fun okIsSuccess() {
        server.status = { 200 }
        upload()
    }

    @Test
    fun unauthorizedIsHttpStatus401() {
        server.status = { 401 }
        assertEquals(401, assertThrows<HttpStatusException> { upload() }.code)
    }

    @Test
    fun badRequestIsHttpStatus400() {
        server.status = { 400 }
        assertEquals(400, assertThrows<HttpStatusException> { upload() }.code)
    }

    @Test
    fun serverErrorIsHttpStatus500() {
        server.status = { 500 }
        assertEquals(500, assertThrows<HttpStatusException> { upload() }.code)
    }

    @Test
    fun connectionRefusedIsIOException() {
        val closedPort = ServerSocket(0).use { it.localPort }
        assertThrows<IOException> { upload(url = "http://127.0.0.1:$closedPort") }
    }

    @Test
    fun checkPostsAnEmptyBatchWithTheToken() {
        runBlocking { client.check(server.url, "secret-token") }
        val request = server.requests.single()
        assertEquals("/api/v1/samples", request.path)
        assertEquals("Bearer secret-token", request.headers["authorization"])
        assertEquals(0, request.json.getJSONArray("samples").length())
    }

    @Test
    fun checkReportsRejectedToken() {
        server.status = { 401 }
        assertEquals(401, assertThrows<HttpStatusException> { runBlocking { client.check(server.url, "bad") } }.code)
    }
}
