package io.github.mateuxlucax.healthconnectbridge

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.TemperatureDelta
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MetricsTest {
    private val source = "com.example.watch"
    private val meta = TestMetadata.from(source)
    private val t0 = Instant.parse("2026-03-01T10:00:00Z")
    private val t0Ms = 1772359200000L // 2026-03-01T10:00:00Z

    private fun assertSample(
        s: Sample,
        metric: String,
        value: Double,
        timestampMs: Long,
        extraLabels: Map<String, String> = emptyMap(),
    ) {
        assertEquals(metric, s.metric)
        assertEquals(value, s.value.toDouble(), 1e-3)
        assertEquals(timestampMs, s.timestampMs)
        assertEquals(mapOf("source" to source) + extraLabels, s.labels)
    }

    private fun single(samples: List<Sample>): Sample {
        assertEquals(1, samples.size)
        return samples[0]
    }

    @Test
    fun fixtureEpochIsRight() {
        assertEquals(t0Ms, t0.toEpochMilli())
    }

    @Test
    fun heartRate_eachSampleBecomesOneSample() {
        val record = HeartRateRecord(
            t0, null, t0.plusSeconds(180), null,
            listOf(
                HeartRateRecord.Sample(t0, 60),
                HeartRateRecord.Sample(t0.plusSeconds(60), 72),
                HeartRateRecord.Sample(t0.plusSeconds(120), 95),
            ),
            meta,
        )
        val samples = record.toSamples()
        assertEquals(3, samples.size)
        assertSample(samples[0], "health_heart_rate_bpm", 60.0, t0Ms)
        assertSample(samples[1], "health_heart_rate_bpm", 72.0, t0Ms + 60_000)
        assertSample(samples[2], "health_heart_rate_bpm", 95.0, t0Ms + 120_000)
    }

    @Test
    fun restingHeartRate() {
        assertSample(
            single(RestingHeartRateRecord(t0, null, 52, meta).toSamples()),
            "health_resting_heart_rate_bpm", 52.0, t0Ms,
        )
    }

    @Test
    fun heartRateVariability() {
        assertSample(
            single(HeartRateVariabilityRmssdRecord(t0, null, 42.5, meta).toSamples()),
            "health_heart_rate_variability_rmssd_ms", 42.5, t0Ms,
        )
    }

    @Test
    fun oxygenSaturation_percentageValue() {
        assertSample(
            single(OxygenSaturationRecord(t0, null, Percentage(97.5), meta).toSamples()),
            "health_oxygen_saturation_percent", 97.5, t0Ms,
        )
    }

    @Test
    fun respiratoryRate() {
        assertSample(
            single(RespiratoryRateRecord(t0, null, 14.5, meta).toSamples()),
            "health_respiratory_rate_breaths_per_minute", 14.5, t0Ms,
        )
    }

    @Test
    fun skinTemperature_eachDeltaBecomesOneSample() {
        val record = SkinTemperatureRecord(
            startTime = t0,
            startZoneOffset = null,
            endTime = t0.plusSeconds(3600),
            endZoneOffset = null,
            metadata = meta,
            deltas = listOf(
                SkinTemperatureRecord.Delta(t0, TemperatureDelta.celsius(-0.4)),
                SkinTemperatureRecord.Delta(t0.plusSeconds(1800), TemperatureDelta.celsius(0.7)),
            ),
        )
        val samples = record.toSamples()
        assertEquals(2, samples.size)
        assertSample(samples[0], "health_skin_temperature_delta_celsius", -0.4, t0Ms)
        assertSample(samples[1], "health_skin_temperature_delta_celsius", 0.7, t0Ms + 1_800_000)
    }

    @Test
    fun steps_atStartTime() {
        assertSample(
            single(StepsRecord(t0, null, t0.plusSeconds(900), null, 1234, meta).toSamples()),
            "health_steps", 1234.0, t0Ms,
        )
    }

    @Test
    fun distance_inMeters() {
        val record = DistanceRecord(t0, null, t0.plusSeconds(900), null, Length.kilometers(2.5), meta)
        assertSample(single(record.toSamples()), "health_distance_meters", 2500.0, t0Ms)
    }

    @Test
    fun activeCalories_inKilocalories() {
        val record = ActiveCaloriesBurnedRecord(t0, null, t0.plusSeconds(900), null, Energy.calories(150_000.0), meta)
        assertSample(single(record.toSamples()), "health_active_calories_kcal", 150.0, t0Ms)
    }

    @Test
    fun totalCalories_inKilocalories() {
        val record = TotalCaloriesBurnedRecord(t0, null, t0.plusSeconds(900), null, Energy.kilojoules(4184.0), meta)
        assertSample(single(record.toSamples()), "health_total_calories_kcal", 1000.0, t0Ms)
    }

    @Test
    fun sleepSession_stagesAndSession() {
        val stageTypes = listOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE to "awake",
            SleepSessionRecord.STAGE_TYPE_SLEEPING to "sleeping",
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to "out_of_bed",
            SleepSessionRecord.STAGE_TYPE_LIGHT to "light",
            SleepSessionRecord.STAGE_TYPE_DEEP to "deep",
            SleepSessionRecord.STAGE_TYPE_REM to "rem",
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to "awake_in_bed",
            SleepSessionRecord.STAGE_TYPE_UNKNOWN to "unknown",
        )
        // Stage i lasts (i + 1) * 10 minutes; stages follow each other without gaps.
        var cursor = t0
        val stages = stageTypes.mapIndexed { i, (type, _) ->
            val end = cursor.plusSeconds((i + 1) * 600L)
            SleepSessionRecord.Stage(cursor, end, type).also { cursor = end }
        }
        val sessionEnd = cursor
        val record = SleepSessionRecord(
            startTime = t0, startZoneOffset = null, endTime = sessionEnd, endZoneOffset = null,
            metadata = meta, stages = stages,
        )

        val samples = record.toSamples()
        val stageSamples = samples.filter { it.metric == "health_sleep_stage_seconds" }
        assertEquals(stageTypes.size, stageSamples.size)
        var startMs = t0Ms
        stageTypes.forEachIndexed { i, (_, name) ->
            val seconds = (i + 1) * 600.0
            assertSample(stageSamples[i], "health_sleep_stage_seconds", seconds, startMs, mapOf("stage" to name))
            startMs += (seconds * 1000).toLong()
        }

        val session = single(samples.filter { it.metric == "health_sleep_session_seconds" })
        val totalSeconds = (1..8).sum() * 600.0
        assertSample(session, "health_sleep_session_seconds", totalSeconds, sessionEnd.toEpochMilli())
        assertEquals(stageTypes.size + 1, samples.size)
    }

    @Test
    fun exerciseSession_readableType() {
        val record = ExerciseSessionRecord(
            startTime = t0, startZoneOffset = null, endTime = t0.plusSeconds(1830), endZoneOffset = null,
            metadata = meta, exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )
        assertSample(
            single(record.toSamples()), "health_exercise_session_seconds", 1830.0, t0Ms,
            mapOf("exercise_type" to "running"),
        )
    }

    @Test
    fun exerciseSession_unknownTypeFallsBackToNumber() {
        val record = ExerciseSessionRecord(
            startTime = t0, startZoneOffset = null, endTime = t0.plusSeconds(60), endZoneOffset = null,
            metadata = meta, exerciseType = 9999,
        )
        assertEquals("9999", single(record.toSamples()).labels["exercise_type"])
    }

    @Test
    fun weight_inKilograms() {
        assertSample(
            single(WeightRecord(t0, null, Mass.pounds(150.0), meta).toSamples()),
            "health_weight_kg", 68.0388555, t0Ms,
        )
    }

    @Test
    fun bodyFat_percentageValue() {
        assertSample(
            single(BodyFatRecord(t0, null, Percentage(18.2), meta).toSamples()),
            "health_body_fat_percent", 18.2, t0Ms,
        )
    }

    @Test
    fun vo2Max() {
        assertSample(
            single(Vo2MaxRecord(t0, null, meta, 48.3).toSamples()),
            "health_vo2_max_ml_per_kg_min", 48.3, t0Ms,
        )
    }
}
