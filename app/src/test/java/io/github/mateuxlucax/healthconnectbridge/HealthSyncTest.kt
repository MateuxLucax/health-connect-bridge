package io.github.mateuxlucax.healthconnectbridge

import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.units.Mass
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.reflect.KClass

class HealthSyncTest {
    private val server = FakeServer()
    private val settings = Settings(InMemoryPrefs())
    private val client = FakeHealthConnectClient(granted = permissionsFor(StepsRecord::class, WeightRecord::class))
    private val sync = HealthSync(settings) { client }

    /** settings.changesToken observed by the server at each upload request. */
    private val tokenAtUpload = mutableListOf<String?>()

    @Before
    fun setUp() {
        settings.serverUrl = server.url
        settings.token = "secret"
        val status = server.status
        server.status = { i -> tokenAtUpload += settings.changesToken; status(i) }
    }

    @After
    fun tearDown() = server.close()

    // ---- helpers -------------------------------------------------------------------------------

    private fun permissionsFor(vararg types: KClass<out Record>) =
        types.map { HealthPermission.getReadPermission(it) }.toSet()

    private var nextSteps = 1L
    private fun steps(): StepsRecord {
        val start = Instant.parse("2026-03-01T10:00:00Z").plusSeconds(nextSteps * 60)
        return StepsRecord(start, null, start.plusSeconds(30), null, nextSteps++, TestMetadata.from("com.example.app"))
    }

    private fun weight() =
        WeightRecord(Instant.parse("2026-03-01T07:00:00Z"), null, Mass.kilograms(70.0), TestMetadata.from("com.scale"))

    private fun changes(vararg c: Change, next: String, hasMore: Boolean = false) =
        ChangesResponse(c.toList(), next, hasMore, false)

    private fun expired() = ChangesResponse(emptyList(), "", false, true)

    private fun readReturning(vararg byType: Pair<KClass<out Record>, List<Record>>) {
        val map = byType.toMap()
        client.readHandler = { r -> ReadRecordsResponse(map[r.recordType].orEmpty(), null) }
    }

    private fun run() = runBlocking { sync.incremental() }

    private fun runExpectingFailure(): Throwable {
        try {
            run()
        } catch (e: Exception) {
            return e
        }
        fail("expected the sync to fail")
        throw IllegalStateException()
    }

    private fun assertRangeDays(request: ReadRecordsRequest<*>, days: Long) {
        val start = request.timeRangeFilter.startTime!!
        val end = request.timeRangeFilter.endTime!!
        assertTrue("end should be ~now, was $end", abs(Duration.between(end, Instant.now()).toMinutes()) <= 1)
        val length = Duration.between(start, end)
        assertTrue("range should be ~$days days, was $length", abs(length.minusDays(days).toMinutes()) <= 1)
    }

    private fun readCalls() = client.calls.filter { it.startsWith("readRecords") }
    private fun changesCalls() = client.calls.filter { it.startsWith("getChanges:") }

    /** Brings the fake to "already initialized with token [token]" through a real first run. */
    private fun initialize(token: String = "t1") {
        client.issuedTokens += "t0"
        client.changesHandler = { changes(next = token) }
        run()
        assertEquals(token, settings.changesToken)
        client.calls.clear()
        client.readRequests.clear()
        client.tokenRequests.clear()
        server.requests.clear()
        tokenAtUpload.clear()
    }

    // ---- first run -----------------------------------------------------------------------------

    @Test
    fun firstRun_takesTokenFirst_readsSevenDays_storesTokenAfterUploads() {
        client.issuedTokens += "t1"
        readReturning(StepsRecord::class to listOf(steps()), WeightRecord::class to listOf(weight()))
        var tokenSeenByGetChanges: String? = null
        client.changesHandler = { tokenSeenByGetChanges = settings.changesToken; changes(next = "t2") }

        run()

        assertEquals("getChangesToken", client.calls.first())
        val firstRead = client.calls.indexOfFirst { it.startsWith("readRecords") }
        assertTrue(firstRead > 0)
        assertEquals(setOf(StepsRecord::class, WeightRecord::class), client.tokenRequests.single().recordTypes)
        assertEquals(
            setOf(StepsRecord::class, WeightRecord::class),
            client.readRequests.map { it.recordType }.toSet(),
        )
        client.readRequests.forEach { assertRangeDays(it, 7) }

        // Both records uploaded, and the checkpoint was not stored while uploading.
        assertEquals(
            setOf("health_steps", "health_weight_kg"),
            server.samples().map { it.getString("metric") }.toSet(),
        )
        assertTrue(tokenAtUpload.isNotEmpty())
        tokenAtUpload.forEach { assertNull(it) }

        // Initial token stored before the changes loop, then advanced by it.
        assertEquals("t1", tokenSeenByGetChanges)
        assertEquals(listOf("getChanges:t1"), changesCalls())
        assertEquals("t2", settings.changesToken)
    }

    @Test
    fun firstRun_uploadFailure_storesNoToken() {
        server.status = { 500 }
        client.issuedTokens += "t1"
        readReturning(StepsRecord::class to listOf(steps()))

        val error = runExpectingFailure()

        assertTrue(error is HttpStatusException)
        assertNull(settings.changesToken)
        assertTrue(changesCalls().isEmpty())
    }

    @Test
    fun readRecords_followsPageTokenUntilEmpty() {
        client.issuedTokens += "t1"
        client.changesHandler = { changes(next = "t2") }
        val pages = mapOf<String?, Pair<List<Record>, String?>>(
            null to (listOf(steps(), steps()) to "p1"),
            "p1" to (listOf(steps()) to "p2"),
            "p2" to (listOf(steps()) to null),
        )
        client.readHandler = { r ->
            if (r.recordType == StepsRecord::class) {
                val (records, next) = pages.getValue(r.pageToken)
                ReadRecordsResponse(records, next)
            } else {
                ReadRecordsResponse(emptyList<Record>(), "")
            }
        }

        run()

        assertEquals(
            listOf("readRecords:StepsRecord:null", "readRecords:StepsRecord:p1", "readRecords:StepsRecord:p2"),
            readCalls().filter { it.contains("StepsRecord") },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), server.samples().map { it.getLong("value") })
    }

    // ---- incremental runs ----------------------------------------------------------------------

    @Test
    fun subsequentRun_followsHasMore_andStoresTokenAfterEachPage() {
        initialize("t1")
        client.changesHandler = { token ->
            when (token) {
                "t1" -> changes(UpsertionChange(steps()), next = "t2", hasMore = true)
                "t2" -> changes(UpsertionChange(steps()), next = "t3", hasMore = true)
                "t3" -> changes(UpsertionChange(steps()), next = "t4", hasMore = false)
                else -> error("unexpected token $token")
            }
        }

        run()

        assertEquals(listOf("getChanges:t1", "getChanges:t2", "getChanges:t3"), client.calls)
        assertEquals(listOf<String?>("t1", "t2", "t3"), tokenAtUpload)
        assertEquals(3, server.samples().size)
        assertEquals("t4", settings.changesToken)
    }

    @Test
    fun subsequentRun_uploadFailureMidLoop_keepsLastSuccessfulToken() {
        initialize("t1")
        server.status = { i -> if (i == 1) 503 else 204 }
        client.changesHandler = { token ->
            when (token) {
                "t1" -> changes(UpsertionChange(steps()), next = "t2", hasMore = true)
                "t2" -> changes(UpsertionChange(steps()), next = "t3", hasMore = true)
                "t3" -> changes(UpsertionChange(steps()), next = "t4", hasMore = false)
                else -> error("unexpected token $token")
            }
        }

        val error = runExpectingFailure()

        assertTrue(error is HttpStatusException)
        assertEquals("t2", settings.changesToken)
        assertFalse(client.calls.contains("getChanges:t3"))
    }

    @Test
    fun expiredToken_rereadsThirtyDaysWithNewToken() {
        initialize("t1")
        client.issuedTokens += "n1"
        readReturning(StepsRecord::class to listOf(steps()))
        client.changesHandler = { token ->
            when (token) {
                "t1" -> expired()
                "n1" -> changes(next = "n2")
                else -> error("unexpected token $token")
            }
        }

        run()

        assertEquals(1, client.calls.count { it == "getChangesToken" })
        assertTrue(client.readRequests.isNotEmpty())
        client.readRequests.forEach { assertRangeDays(it, 30) }
        assertEquals(1, server.samples().size)
        assertEquals(listOf("getChanges:t1", "getChanges:n1"), changesCalls())
        assertEquals("n2", settings.changesToken)
    }

    @Test
    fun grantedTypesChanged_reinitializes() {
        client.granted = permissionsFor(StepsRecord::class)
        initialize("t1")
        client.granted = permissionsFor(StepsRecord::class, WeightRecord::class)
        client.issuedTokens += "fresh"
        client.changesHandler = { token ->
            if (token == "fresh") changes(next = "fresh2") else error("old token $token used")
        }

        run()

        assertEquals("getChangesToken", client.calls.first())
        assertEquals(setOf(StepsRecord::class, WeightRecord::class), client.tokenRequests.single().recordTypes)
        client.readRequests.forEach { assertRangeDays(it, 7) }
        assertEquals(listOf("getChanges:fresh"), changesCalls())
        assertEquals("fresh2", settings.changesToken)
    }

    @Test
    fun grantedTypesChanged_afterLongOutage_readsSinceLastCheckpoint() {
        client.granted = permissionsFor(StepsRecord::class)
        initialize("t1")
        // The last checkpoint is 10 days old (e.g. the server was down), then a new type is granted.
        settings.saveChangesToken("t1", settings.changesTypes!!, System.currentTimeMillis() - Duration.ofDays(10).toMillis())
        client.granted = permissionsFor(StepsRecord::class, WeightRecord::class)
        client.issuedTokens += "fresh"
        client.changesHandler = { changes(next = "fresh2") }

        run()

        assertTrue(client.readRequests.isNotEmpty())
        client.readRequests.forEach { assertRangeDays(it, 11) }
        assertEquals("fresh2", settings.changesToken)
    }

    @Test
    fun grantedTypesChanged_afterVeryLongOutage_readsAtMostThirtyDays() {
        client.granted = permissionsFor(StepsRecord::class)
        initialize("t1")
        settings.saveChangesToken("t1", settings.changesTypes!!, System.currentTimeMillis() - Duration.ofDays(90).toMillis())
        client.granted = permissionsFor(StepsRecord::class, WeightRecord::class)
        client.issuedTokens += "fresh"
        client.changesHandler = { changes(next = "fresh2") }

        run()

        client.readRequests.forEach { assertRangeDays(it, 30) }
    }

    @Test
    fun deletions_produceNoSamples() {
        initialize("t1")
        client.changesHandler = { changes(DeletionChange("gone-1"), DeletionChange("gone-2"), next = "t2") }

        run()

        assertEquals(0, server.requests.size)
        assertEquals("t2", settings.changesToken)
    }

    @Test
    fun deletionsMixedWithUpserts_onlyUpsertsUploaded() {
        initialize("t1")
        client.changesHandler = { changes(DeletionChange("gone"), UpsertionChange(steps()), next = "t2") }

        run()

        assertEquals(listOf("health_steps"), server.samples().map { it.getString("metric") })
    }

    // ---- permissions & features ----------------------------------------------------------------

    @Test
    fun noReadPermissions_isSetupError() {
        client.granted = emptySet()

        val error = runExpectingFailure()

        assertTrue(error is SetupException)
        assertTrue(readCalls().isEmpty())
        assertFalse(client.calls.contains("getChangesToken"))
    }

    @Test
    fun invalidSavedUrl_isSetupError() {
        settings.serverUrl = "ftp://example.com"

        val error = runExpectingFailure()

        assertEquals(SyncError.Kind.INVALID_URL, (error as SetupException).kind)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun onlyGrantedTypesAreRead() {
        client.granted = permissionsFor(WeightRecord::class)
        client.issuedTokens += "t1"
        client.changesHandler = { changes(next = "t2") }

        run()

        assertEquals(setOf(WeightRecord::class), client.readRequests.map { it.recordType }.toSet())
        assertEquals(setOf(WeightRecord::class), client.tokenRequests.single().recordTypes)
    }

    @Test
    fun skinTemperature_skippedWhenFeatureUnavailable() {
        client.granted = permissionsFor(StepsRecord::class, SkinTemperatureRecord::class)
        client.skinTemperatureAvailable = false
        client.issuedTokens += "t1"
        client.changesHandler = { changes(next = "t2") }

        run()

        assertEquals(setOf(StepsRecord::class), client.readRequests.map { it.recordType }.toSet())
        assertEquals(setOf(StepsRecord::class), client.tokenRequests.single().recordTypes)
    }

    @Test
    fun skinTemperature_readWhenFeatureAvailable() {
        client.granted = permissionsFor(StepsRecord::class, SkinTemperatureRecord::class)
        client.skinTemperatureAvailable = true
        client.issuedTokens += "t1"
        client.changesHandler = { changes(next = "t2") }

        run()

        assertEquals(
            setOf(StepsRecord::class, SkinTemperatureRecord::class),
            client.readRequests.map { it.recordType }.toSet(),
        )
    }

    // ---- backfill ------------------------------------------------------------------------------

    @Test
    fun backfill_readsRange_andLeavesCheckpointUntouched() {
        initialize("t1")
        readReturning(StepsRecord::class to listOf(steps(), steps()))

        runBlocking { sync.backfill(3) }

        assertEquals("t1", settings.changesToken)
        assertFalse(client.calls.any { it == "getChangesToken" || it.startsWith("getChanges:") })
        assertTrue(client.readRequests.isNotEmpty())
        client.readRequests.forEach { assertRangeDays(it, 3) }
        assertEquals(2, server.samples().size)
    }

    @Test
    fun backfill_onFreshInstall_storesNoCheckpoint() {
        readReturning(StepsRecord::class to listOf(steps()))

        runBlocking { sync.backfill(14) }

        assertNull(settings.changesToken)
        client.readRequests.forEach { assertRangeDays(it, 14) }
    }
}
