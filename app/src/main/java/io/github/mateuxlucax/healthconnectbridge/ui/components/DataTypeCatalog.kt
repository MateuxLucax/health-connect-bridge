package io.github.mateuxlucax.healthconnectbridge.ui.components

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
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.METRICS_BY_RECORD_TYPE
import kotlin.reflect.KClass

enum class Category(@StringRes val title: Int) {
    HEART(R.string.category_heart),
    ACTIVITY(R.string.category_activity),
    SLEEP(R.string.category_sleep),
    BODY(R.string.category_body),
}

/**
 * A Health Connect data type the app reads, as shown to the user. The synced types and their metrics are
 * defined once, in data/healthconnect/RecordMapping.kt; this adds the category, texts and icon.
 */
class DataType(
    val record: KClass<out Record>,
    val category: Category,
    @StringRes val name: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
) {
    /** Metrics produced from this record type. */
    val metrics: List<String> get() = METRICS_BY_RECORD_TYPE[record].orEmpty()
}

/** One entry per synced record type (CatalogConsistencyTest checks it), in display order within each [Category]. */
val DATA_TYPES = listOf(
    DataType(HeartRateRecord::class, Category.HEART, R.string.type_heart_rate, R.string.type_heart_rate_desc, R.drawable.ic_favorite),
    DataType(RestingHeartRateRecord::class, Category.HEART, R.string.type_resting_heart_rate, R.string.type_resting_heart_rate_desc, R.drawable.ic_ecg_heart),
    DataType(HeartRateVariabilityRmssdRecord::class, Category.HEART, R.string.type_hrv, R.string.type_hrv_desc, R.drawable.ic_monitor_heart),
    DataType(OxygenSaturationRecord::class, Category.HEART, R.string.type_oxygen, R.string.type_oxygen_desc, R.drawable.ic_spo2),
    DataType(RespiratoryRateRecord::class, Category.HEART, R.string.type_respiratory_rate, R.string.type_respiratory_rate_desc, R.drawable.ic_pulmonology),
    DataType(StepsRecord::class, Category.ACTIVITY, R.string.type_steps, R.string.type_steps_desc, R.drawable.ic_steps),
    DataType(DistanceRecord::class, Category.ACTIVITY, R.string.type_distance, R.string.type_distance_desc, R.drawable.ic_distance),
    DataType(ActiveCaloriesBurnedRecord::class, Category.ACTIVITY, R.string.type_active_calories, R.string.type_active_calories_desc, R.drawable.ic_local_fire_department),
    DataType(TotalCaloriesBurnedRecord::class, Category.ACTIVITY, R.string.type_total_calories, R.string.type_total_calories_desc, R.drawable.ic_bolt),
    DataType(ExerciseSessionRecord::class, Category.ACTIVITY, R.string.type_exercise, R.string.type_exercise_desc, R.drawable.ic_fitness_center),
    DataType(Vo2MaxRecord::class, Category.ACTIVITY, R.string.type_vo2_max, R.string.type_vo2_max_desc, R.drawable.ic_sprint),
    DataType(SleepSessionRecord::class, Category.SLEEP, R.string.type_sleep, R.string.type_sleep_desc, R.drawable.ic_bedtime),
    DataType(WeightRecord::class, Category.BODY, R.string.type_weight, R.string.type_weight_desc, R.drawable.ic_monitor_weight),
    DataType(BodyFatRecord::class, Category.BODY, R.string.type_body_fat, R.string.type_body_fat_desc, R.drawable.ic_body_fat),
    DataType(SkinTemperatureRecord::class, Category.BODY, R.string.type_skin_temperature, R.string.type_skin_temperature_desc, R.drawable.ic_thermostat),
)
