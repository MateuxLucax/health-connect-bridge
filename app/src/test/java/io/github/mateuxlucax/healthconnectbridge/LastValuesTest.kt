package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastValuesTest {
    private val prefs = InMemoryPrefs()
    private val store = LastValues(prefs)

    private fun sample(metric: String, value: Number, t: Long, vararg labels: Pair<String, String>) =
        Sample(metric, mapOf("source" to "app") + labels, value, t)

    private fun byKey() = store.all().associateBy { it.metric to it.label }

    @Test
    fun emptyByDefault() = assertTrue(store.all().isEmpty())

    @Test
    fun keepsNewestPerMetric_evenWhenOlderArrivesLater() {
        store.record(listOf(sample("health_heart_rate_bpm", 61, 1000), sample("health_heart_rate_bpm", 72, 3000)))
        store.record(listOf(sample("health_heart_rate_bpm", 88, 2000))) // e.g. a backfill
        val value = byKey().getValue("health_heart_rate_bpm" to null)
        assertEquals(72.0, value.value, 0.0)
        assertEquals(3000L, value.timestampMs)
    }

    @Test
    fun separatesSleepStagesAndExerciseTypes() {
        store.record(
            listOf(
                sample("health_sleep_stage_seconds", 3600, 1, "stage" to "light"),
                sample("health_sleep_stage_seconds", 5400, 2, "stage" to "deep"),
                sample("health_exercise_session_seconds", 2700.0, 3, "exercise_type" to "running"),
                sample("health_steps", 1234L, 4),
            )
        )
        val values = byKey()
        assertEquals(4, values.size)
        assertEquals(5400.0, values.getValue("health_sleep_stage_seconds" to "deep").value, 0.0)
        assertEquals(3600.0, values.getValue("health_sleep_stage_seconds" to "light").value, 0.0)
        assertEquals(2700.0, values.getValue("health_exercise_session_seconds" to "running").value, 0.0)
        assertEquals(1234.0, values.getValue("health_steps" to null).value, 0.0)
    }

    @Test
    fun persistsInPreferences() {
        store.record(listOf(sample("health_weight_kg", 72.5, 10)))
        val reloaded = LastValues(prefs).all().single()
        assertEquals("health_weight_kg", reloaded.metric)
        assertEquals(72.5, reloaded.value, 0.0)
        assertEquals(10L, reloaded.timestampMs)
    }

    @Test
    fun corruptStorageIsIgnoredAndReplaced() {
        prefs.edit().putString("last_values", "{not json").apply()
        assertTrue(store.all().isEmpty())
        store.record(listOf(sample("health_weight_kg", 72.5, 10)))
        assertEquals(72.5, store.all().single().value, 0.0)
    }

    @Test
    fun malformedEntriesAreSkipped() {
        prefs.edit().putString("last_values", """{"health_steps":5,"health_weight_kg":{"v":72.5,"t":10},"x":{"v":1}}""").apply()
        assertEquals(listOf("health_weight_kg"), store.all().map { it.metric })
    }
}
