package io.github.mateuxlucax.healthconnectbridge.data.exporter

import io.github.mateuxlucax.healthconnectbridge.model.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Non-2xx response from the exporter. */
class HttpStatusException(val code: Int, message: String) : Exception(message)

/**
 * Client of health-connect-prometheus-exporter: POSTs samples to `{serverUrl}/api/v1/samples`, at most
 * [MAX_SAMPLES_PER_REQUEST] per request. The JSON wire format (docs/data.md) is built only in [post].
 */
class ExporterClient {
    suspend fun upload(serverUrl: String, token: String, samples: List<Sample>) = withContext(Dispatchers.IO) {
        samples.chunked(MAX_SAMPLES_PER_REQUEST).forEach { post(serverUrl, token, it) }
    }

    /** Sends an empty batch to check the URL and token; throws like [upload]. */
    suspend fun check(serverUrl: String, token: String) = withContext(Dispatchers.IO) {
        post(serverUrl, token, emptyList())
    }

    private fun post(serverUrl: String, token: String, samples: List<Sample>) {
        val body = JSONObject().put("samples", JSONArray().apply {
            samples.forEach { s ->
                put(
                    JSONObject()
                        .put("metric", s.metric)
                        .put("labels", JSONObject(s.labels))
                        .put("value", s.value)
                        .put("timestamp_ms", s.timestampMs)
                )
            }
        }).toString().toByteArray()

        val connection = URL(serverUrl.trimEnd('/') + "/api/v1/samples").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            // A redirect is reported as an error (wrong URL, or http that should be https), and the token is
            // never sent on to another location.
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(200)
                throw HttpStatusException(code, "HTTP $code $detail".trim())
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_SAMPLES_PER_REQUEST = 5_000
    }
}
