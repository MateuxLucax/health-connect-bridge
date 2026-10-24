package io.github.mateuxlucax.healthconnectbridge

import androidx.annotation.DrawableRes
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

enum class Category(val title: String) { HEART("Coração"), ACTIVITY("Atividade"), SLEEP("Sono"), BODY("Corpo") }

/** A Health Connect data type the app reads, as shown to the user. */
class DataType(
    val record: KClass<out Record>,
    val category: Category,
    val name: String,
    val description: String,
    @DrawableRes val icon: Int,
    /** Metrics produced from this record type (see Metrics.kt). */
    val metrics: List<String>,
)

val DATA_TYPES = listOf(
    DataType(HeartRateRecord::class, Category.HEART, "Frequência cardíaca", "Batimentos por minuto medidos ao longo do dia", R.drawable.ic_favorite, listOf("health_heart_rate_bpm")),
    DataType(RestingHeartRateRecord::class, Category.HEART, "Frequência em repouso", "Batimentos por minuto em repouso", R.drawable.ic_ecg_heart, listOf("health_resting_heart_rate_bpm")),
    DataType(HeartRateVariabilityRmssdRecord::class, Category.HEART, "Variabilidade da frequência", "Variação entre batimentos (RMSSD)", R.drawable.ic_monitor_heart, listOf("health_heart_rate_variability_rmssd_ms")),
    DataType(OxygenSaturationRecord::class, Category.HEART, "Oxigênio no sangue", "Saturação de oxigênio (SpO₂)", R.drawable.ic_spo2, listOf("health_oxygen_saturation_percent")),
    DataType(RespiratoryRateRecord::class, Category.HEART, "Frequência respiratória", "Respirações por minuto", R.drawable.ic_pulmonology, listOf("health_respiratory_rate_breaths_per_minute")),
    DataType(StepsRecord::class, Category.ACTIVITY, "Passos", "Passos contados pelo relógio ou celular", R.drawable.ic_steps, listOf("health_steps")),
    DataType(DistanceRecord::class, Category.ACTIVITY, "Distância", "Distância percorrida", R.drawable.ic_distance, listOf("health_distance_meters")),
    DataType(ActiveCaloriesBurnedRecord::class, Category.ACTIVITY, "Calorias ativas", "Calorias gastas em atividade", R.drawable.ic_local_fire_department, listOf("health_active_calories_kcal")),
    DataType(TotalCaloriesBurnedRecord::class, Category.ACTIVITY, "Calorias totais", "Calorias gastas no total, incluindo em repouso", R.drawable.ic_bolt, listOf("health_total_calories_kcal")),
    DataType(ExerciseSessionRecord::class, Category.ACTIVITY, "Exercícios", "Tipo e duração de cada treino", R.drawable.ic_fitness_center, listOf("health_exercise_session_seconds")),
    DataType(Vo2MaxRecord::class, Category.ACTIVITY, "VO₂ máximo", "Estimativa de capacidade aeróbica", R.drawable.ic_sprint, listOf("health_vo2_max_ml_per_kg_min")),
    DataType(SleepSessionRecord::class, Category.SLEEP, "Sono", "Duração do sono e de cada estágio", R.drawable.ic_bedtime, listOf("health_sleep_session_seconds", "health_sleep_stage_seconds")),
    DataType(WeightRecord::class, Category.BODY, "Peso", "Peso corporal", R.drawable.ic_monitor_weight, listOf("health_weight_kg")),
    DataType(BodyFatRecord::class, Category.BODY, "Gordura corporal", "Percentual de gordura", R.drawable.ic_body_fat, listOf("health_body_fat_percent")),
    DataType(SkinTemperatureRecord::class, Category.BODY, "Temperatura da pele", "Variação em relação à sua média", R.drawable.ic_thermostat, listOf("health_skin_temperature_delta_celsius")),
)

/** Human name of an exported value, e.g. "Sono profundo" or "Exercício: corrida". */
fun displayName(type: DataType, value: LastValue): String = when (value.metric) {
    "health_sleep_session_seconds" -> "Sono total"
    "health_sleep_stage_seconds" -> SLEEP_STAGES[value.label] ?: "Sono: ${value.label}"
    "health_exercise_session_seconds" ->
        "Exercício: " + (EXERCISES[value.label] ?: value.label.orEmpty().replace('_', ' '))
    else -> type.name
}

private val SLEEP_STAGES = mapOf(
    "awake" to "Acordado",
    "sleeping" to "Dormindo",
    "out_of_bed" to "Fora da cama",
    "light" to "Sono leve",
    "deep" to "Sono profundo",
    "rem" to "Sono REM",
    "awake_in_bed" to "Acordado na cama",
    "unknown" to "Sono (estágio desconhecido)",
)

private val EXERCISES = mapOf(
    "running" to "corrida",
    "walking" to "caminhada",
    "biking" to "ciclismo",
    "swimming_pool" to "natação",
    "strength_training" to "musculação",
    "yoga" to "ioga",
    "hiking" to "trilha",
    "other_workout" to "outro",
)
