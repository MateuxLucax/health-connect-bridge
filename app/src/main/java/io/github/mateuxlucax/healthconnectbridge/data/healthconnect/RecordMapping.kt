package io.github.mateuxlucax.healthconnectbridge.data.healthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import io.github.mateuxlucax.healthconnectbridge.model.Sample
import java.lang.reflect.Modifier
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Every Health Connect record type the app syncs, with the metrics [toSamples] produces from it. The single
 * list of synced types: [RECORD_TYPES], the permissions and the UI catalog (ui/components/DataTypeCatalog.kt)
 * all come from it.
 */
val METRICS_BY_RECORD_TYPE: Map<KClass<out Record>, List<String>> = linkedMapOf<KClass<out Record>, List<String>>(
    HeartRateRecord::class to listOf("health_heart_rate_bpm"),
    RestingHeartRateRecord::class to listOf("health_resting_heart_rate_bpm"),
    HeartRateVariabilityRmssdRecord::class to listOf("health_heart_rate_variability_rmssd_ms"),
    OxygenSaturationRecord::class to listOf("health_oxygen_saturation_percent"),
    RespiratoryRateRecord::class to listOf("health_respiratory_rate_breaths_per_minute"),
    SkinTemperatureRecord::class to listOf("health_skin_temperature_delta_celsius"),
    StepsRecord::class to listOf("health_steps"),
    DistanceRecord::class to listOf("health_distance_meters"),
    ActiveCaloriesBurnedRecord::class to listOf("health_active_calories_kcal"),
    TotalCaloriesBurnedRecord::class to listOf("health_total_calories_kcal"),
    SleepSessionRecord::class to listOf("health_sleep_session_seconds", "health_sleep_stage_seconds"),
    ExerciseSessionRecord::class to listOf("health_exercise_session_seconds"),
    WeightRecord::class to listOf("health_weight_kg"),
    BodyFatRecord::class to listOf("health_body_fat_percent"),
    Vo2MaxRecord::class to listOf("health_vo2_max_ml_per_kg_min"),
)

/** Every Health Connect record type the app syncs. */
val RECORD_TYPES: List<KClass<out Record>> = METRICS_BY_RECORD_TYPE.keys.toList()

/** Maps a Health Connect record to Prometheus samples (no aggregation). */
fun Record.toSamples(): List<Sample> {
    val source = mapOf("source" to metadata.dataOrigin.packageName)
    fun sample(metric: String, value: Number, time: Instant, extra: Map<String, String> = emptyMap()) =
        Sample(metric, source + extra, value, time.toEpochMilli())

    return when (this) {
        is HeartRateRecord -> samples.map { sample("health_heart_rate_bpm", it.beatsPerMinute, it.time) }
        is RestingHeartRateRecord -> listOf(sample("health_resting_heart_rate_bpm", beatsPerMinute, time))
        is HeartRateVariabilityRmssdRecord ->
            listOf(sample("health_heart_rate_variability_rmssd_ms", heartRateVariabilityMillis, time))
        is OxygenSaturationRecord -> listOf(sample("health_oxygen_saturation_percent", percentage.value, time))
        is RespiratoryRateRecord -> listOf(sample("health_respiratory_rate_breaths_per_minute", rate, time))
        is SkinTemperatureRecord ->
            deltas.map { sample("health_skin_temperature_delta_celsius", it.delta.inCelsius, it.time) }
        is StepsRecord -> listOf(sample("health_steps", count, startTime))
        is DistanceRecord -> listOf(sample("health_distance_meters", distance.inMeters, startTime))
        is ActiveCaloriesBurnedRecord -> listOf(sample("health_active_calories_kcal", energy.inKilocalories, startTime))
        is TotalCaloriesBurnedRecord -> listOf(sample("health_total_calories_kcal", energy.inKilocalories, startTime))
        is SleepSessionRecord ->
            stages.map {
                sample(
                    "health_sleep_stage_seconds", seconds(it.startTime, it.endTime), it.startTime,
                    mapOf("stage" to (SLEEP_STAGE_NAMES[it.stage] ?: "unknown")),
                )
            } + sample("health_sleep_session_seconds", seconds(startTime, endTime), endTime)
        is ExerciseSessionRecord ->
            listOf(
                sample(
                    "health_exercise_session_seconds", seconds(startTime, endTime), startTime,
                    mapOf("exercise_type" to (exerciseTypeNames[exerciseType] ?: exerciseType.toString())),
                )
            )
        is WeightRecord -> listOf(sample("health_weight_kg", weight.inKilograms, time))
        is BodyFatRecord -> listOf(sample("health_body_fat_percent", percentage.value, time))
        is Vo2MaxRecord -> listOf(sample("health_vo2_max_ml_per_kg_min", vo2MillilitersPerMinuteKilogram, time))
        else -> emptyList()
    }
}

private fun seconds(start: Instant, end: Instant): Double = Duration.between(start, end).toMillis() / 1000.0

private val SLEEP_STAGE_NAMES = mapOf(
    SleepSessionRecord.STAGE_TYPE_UNKNOWN to "unknown",
    SleepSessionRecord.STAGE_TYPE_AWAKE to "awake",
    SleepSessionRecord.STAGE_TYPE_SLEEPING to "sleeping",
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED to "out_of_bed",
    SleepSessionRecord.STAGE_TYPE_LIGHT to "light",
    SleepSessionRecord.STAGE_TYPE_DEEP to "deep",
    SleepSessionRecord.STAGE_TYPE_REM to "rem",
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED to "awake_in_bed",
)

/**
 * Readable names from the library's public EXERCISE_TYPE_* constants, e.g. 56 -> "running".
 * (The library's own int-to-string map is restricted API and maps some types ambiguously.)
 * Works because release builds are not minified.
 */
private val exerciseTypeNames: Map<Int, String> by lazy {
    ExerciseSessionRecord::class.java.fields
        .filter {
            Modifier.isStatic(it.modifiers) &&
                it.type == Int::class.javaPrimitiveType &&
                it.name.startsWith("EXERCISE_TYPE_")
        }
        .associate { it.getInt(null) to it.name.removePrefix("EXERCISE_TYPE_").lowercase() }
}
