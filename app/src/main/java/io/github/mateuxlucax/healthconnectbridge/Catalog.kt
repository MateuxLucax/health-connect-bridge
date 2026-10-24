package io.github.mateuxlucax.healthconnectbridge

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import kotlin.reflect.KClass

enum class Category(@StringRes val title: Int) {
    HEART(R.string.category_heart),
    ACTIVITY(R.string.category_activity),
    SLEEP(R.string.category_sleep),
    BODY(R.string.category_body),
}

/** A Health Connect data type the app reads, as shown to the user. */
class DataType(
    val record: KClass<out Record>,
    val category: Category,
    @StringRes val name: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
    /** Metrics produced from this record type (see Metrics.kt). */
    val metrics: List<String>,
)

val DATA_TYPES = listOf(
    DataType(HeartRateRecord::class, Category.HEART, R.string.type_heart_rate, R.string.type_heart_rate_desc, R.drawable.ic_favorite, listOf("health_heart_rate_bpm")),
    DataType(RestingHeartRateRecord::class, Category.HEART, R.string.type_resting_heart_rate, R.string.type_resting_heart_rate_desc, R.drawable.ic_ecg_heart, listOf("health_resting_heart_rate_bpm")),
    DataType(HeartRateVariabilityRmssdRecord::class, Category.HEART, R.string.type_hrv, R.string.type_hrv_desc, R.drawable.ic_monitor_heart, listOf("health_heart_rate_variability_rmssd_ms")),
    DataType(OxygenSaturationRecord::class, Category.HEART, R.string.type_oxygen, R.string.type_oxygen_desc, R.drawable.ic_spo2, listOf("health_oxygen_saturation_percent")),
    DataType(RespiratoryRateRecord::class, Category.HEART, R.string.type_respiratory_rate, R.string.type_respiratory_rate_desc, R.drawable.ic_pulmonology, listOf("health_respiratory_rate_breaths_per_minute")),
    DataType(StepsRecord::class, Category.ACTIVITY, R.string.type_steps, R.string.type_steps_desc, R.drawable.ic_steps, listOf("health_steps")),
    DataType(DistanceRecord::class, Category.ACTIVITY, R.string.type_distance, R.string.type_distance_desc, R.drawable.ic_distance, listOf("health_distance_meters")),
    DataType(ActiveCaloriesBurnedRecord::class, Category.ACTIVITY, R.string.type_active_calories, R.string.type_active_calories_desc, R.drawable.ic_local_fire_department, listOf("health_active_calories_kcal")),
    DataType(TotalCaloriesBurnedRecord::class, Category.ACTIVITY, R.string.type_total_calories, R.string.type_total_calories_desc, R.drawable.ic_bolt, listOf("health_total_calories_kcal")),
    DataType(ExerciseSessionRecord::class, Category.ACTIVITY, R.string.type_exercise, R.string.type_exercise_desc, R.drawable.ic_fitness_center, listOf("health_exercise_session_seconds")),
    DataType(Vo2MaxRecord::class, Category.ACTIVITY, R.string.type_vo2_max, R.string.type_vo2_max_desc, R.drawable.ic_sprint, listOf("health_vo2_max_ml_per_kg_min")),
    DataType(SleepSessionRecord::class, Category.SLEEP, R.string.type_sleep, R.string.type_sleep_desc, R.drawable.ic_bedtime, listOf("health_sleep_session_seconds", "health_sleep_stage_seconds")),
    DataType(WeightRecord::class, Category.BODY, R.string.type_weight, R.string.type_weight_desc, R.drawable.ic_monitor_weight, listOf("health_weight_kg")),
    DataType(BodyFatRecord::class, Category.BODY, R.string.type_body_fat, R.string.type_body_fat_desc, R.drawable.ic_body_fat, listOf("health_body_fat_percent")),
    DataType(SkinTemperatureRecord::class, Category.BODY, R.string.type_skin_temperature, R.string.type_skin_temperature_desc, R.drawable.ic_thermostat, listOf("health_skin_temperature_delta_celsius")),
)
