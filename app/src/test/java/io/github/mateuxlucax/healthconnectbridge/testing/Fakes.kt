package io.github.mateuxlucax.healthconnectbridge.testing

import android.content.SharedPreferences
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Collections
import kotlin.reflect.KClass

/**
 * SharedPreferences kept in a map; edits apply immediately, listeners are called on apply or commit.
 *
 * Unlike the real implementation, which holds listeners weakly, this one keeps them strongly, so tests can't
 * catch a listener being garbage collected while still needed. `changes()` (PreferenceChanges.kt) relies on
 * its callbackFlow keeping the listener referenced until awaitClose; keep it that way.
 */
class InMemoryPrefs : SharedPreferences {
    val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    /** Number of registered listeners, e.g. to check that a flow stopped listening. */
    val listenerCount get() = listeners.size

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?) = values[key] as Set<String>? ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += l
    }
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= l
    }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changed = mutableListOf<String?>()

        private fun set(key: String, value: Any?): SharedPreferences.Editor {
            values[key] = value
            changed += key
            return this
        }

        /** Like Android 11+: a cleared file is reported with a null key. */
        private fun notifyListeners() {
            val keys = changed.toList()
            changed.clear()
            for (key in keys) listeners.toList().forEach { it.onSharedPreferenceChanged(this@InMemoryPrefs, key) }
        }
        override fun putString(key: String, value: String?) = set(key, value)
        override fun putStringSet(key: String, value: Set<String>?) = set(key, value)
        override fun putInt(key: String, value: Int) = set(key, value)
        override fun putLong(key: String, value: Long) = set(key, value)
        override fun putFloat(key: String, value: Float) = set(key, value)
        override fun putBoolean(key: String, value: Boolean) = set(key, value)
        override fun remove(key: String): SharedPreferences.Editor {
            values.remove(key)
            changed += key
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            values.clear()
            changed += null
            return this
        }
        override fun commit(): Boolean {
            notifyListeners()
            return true
        }
        override fun apply() = notifyListeners()
    }
}

/**
 * Hand-written HealthConnectClient: records every call in [calls] and answers from the configured
 * handlers. Methods the app does not use throw.
 */
class FakeHealthConnectClient(
    var granted: Set<String>,
    var skinTemperatureAvailable: Boolean = true,
) : HealthConnectClient {
    val calls = mutableListOf<String>()
    val tokenRequests = mutableListOf<ChangesTokenRequest>()
    val readRequests = mutableListOf<ReadRecordsRequest<*>>()

    /** Tokens handed out by getChangesToken, in order. */
    val issuedTokens = ArrayDeque<String>()
    var readHandler: (ReadRecordsRequest<*>) -> ReadRecordsResponse<*> = { ReadRecordsResponse(emptyList<Record>(), null) }
    var changesHandler: (String) -> ChangesResponse = { error("unexpected getChanges($it)") }

    /**
     * Records Health Connect can't build, by type: readRecords throws IllegalArgumentException, like the client
     * library does for e.g. a record with startTime == endTime, whenever the requested window contains one.
     * (getChanges can throw the same way from [changesHandler].)
     */
    val unreadable = mutableListOf<Pair<KClass<out Record>, Instant>>()

    override val permissionController = object : PermissionController {
        override suspend fun getGrantedPermissions(): Set<String> = granted
        override suspend fun revokeAllPermissions() = unused()
    }

    override val features = object : HealthConnectFeatures {
        override fun getFeatureStatus(feature: Int): Int =
            if (feature == HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE && skinTemperatureAvailable) {
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            } else {
                HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE
            }
    }

    override suspend fun getChangesToken(request: ChangesTokenRequest): String {
        calls += "getChangesToken"
        tokenRequests += request
        return issuedTokens.removeFirst()
    }

    override suspend fun getChanges(changesToken: String): ChangesResponse {
        calls += "getChanges:$changesToken"
        return changesHandler(changesToken)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> {
        calls += "readRecords:${request.recordType.simpleName}:${request.pageToken}"
        readRequests += request
        val start = request.timeRangeFilter.startTime
        val end = request.timeRangeFilter.endTime
        val bad = unreadable.any { (type, at) ->
            type == request.recordType && (start == null || !at.isBefore(start)) && (end == null || at.isBefore(end))
        }
        if (bad) throw IllegalArgumentException("startTime must be before endTime.")
        return readHandler(request) as ReadRecordsResponse<T>
    }

    override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse = unused()
    override suspend fun updateRecords(records: List<Record>) = unused()
    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        recordIdsList: List<String>,
        clientRecordIdsList: List<String>,
    ) = unused()
    override suspend fun deleteRecords(recordType: KClass<out Record>, timeRangeFilter: TimeRangeFilter) = unused()
    override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): ReadRecordResponse<T> =
        unused()
    override suspend fun aggregate(request: AggregateRequest): AggregationResult = unused()
    override suspend fun aggregateGroupByDuration(
        request: AggregateGroupByDurationRequest,
    ): List<AggregationResultGroupedByDuration> = unused()
    override suspend fun aggregateGroupByPeriod(
        request: AggregateGroupByPeriodRequest,
    ): List<AggregationResultGroupedByPeriod> = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("not used by the app")
}

/** One request received by [FakeServer]. */
class ReceivedRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
    val json get() = JSONObject(body)
}

/** Local exporter stand-in. [status] decides each response code (by request index, 0-based). */
class FakeServer(var status: (Int) -> Int = { 204 }) : AutoCloseable {
    val requests: MutableList<ReceivedRequest> = Collections.synchronizedList(mutableListOf())
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            val headers = exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            val index = synchronized(requests) {
                requests += ReceivedRequest(exchange.requestMethod, exchange.requestURI.path, headers, body)
                requests.size - 1
            }
            val code = status(index)
            if (code == 204) {
                exchange.sendResponseHeaders(204, -1)
            } else {
                val reply = "status $code".toByteArray()
                exchange.sendResponseHeaders(code, reply.size.toLong())
                exchange.responseBody.write(reply)
            }
            exchange.close()
        }
        start()
    }

    val url = "http://127.0.0.1:${server.address.port}"

    /** All samples received, in order, across requests. */
    fun samples(): List<JSONObject> = requests.flatMap { r ->
        val array = r.json.getJSONArray("samples")
        (0 until array.length()).map { array.getJSONObject(it) }
    }

    override fun close() = server.stop(0)
}
