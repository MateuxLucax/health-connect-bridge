package io.github.mateuxlucax.healthconnectbridge

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** pt-BR display formatting for values and times. */
object Format {
    private val locale: Locale = Locale.forLanguageTag("pt-BR")
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM", locale)
    private val fullDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)

    fun number(value: Double, maxDecimals: Int = 1): String {
        val pattern = if (maxDecimals == 0) "#,##0" else "#,##0." + "#".repeat(maxDecimals)
        return DecimalFormat(pattern, DecimalFormatSymbols(locale)).format(value)
    }

    /** "45 min", "1h32", "7h00". */
    fun duration(seconds: Double): String {
        val minutes = (seconds / 60).roundToLong()
        return if (minutes < 60) "$minutes min" else String.format(locale, "%dh%02d", minutes / 60, minutes % 60)
    }

    /** Value of [metric] with its unit, e.g. "72 bpm", "8.421 passos". */
    fun value(metric: String, value: Double): String = when (metric) {
        "health_heart_rate_bpm", "health_resting_heart_rate_bpm" -> "${number(value, 0)} bpm"
        "health_heart_rate_variability_rmssd_ms" -> "${number(value, 0)} ms"
        "health_oxygen_saturation_percent", "health_body_fat_percent" -> "${number(value)}%"
        "health_respiratory_rate_breaths_per_minute" -> "${number(value)} resp/min"
        "health_skin_temperature_delta_celsius" -> (if (value > 0) "+" else "") + "${number(value)} °C"
        "health_steps" -> number(value, 0) + if (value == 1.0) " passo" else " passos"
        "health_distance_meters" ->
            if (value < 1000) "${number(value, 0)} m" else "${number(value / 1000, 2)} km"
        "health_active_calories_kcal", "health_total_calories_kcal" -> "${number(value)} kcal"
        "health_sleep_stage_seconds", "health_sleep_session_seconds", "health_exercise_session_seconds" ->
            duration(value)
        "health_weight_kg" -> "${number(value)} kg"
        "health_vo2_max_ml_per_kg_min" -> "${number(value)} ml/kg/min"
        else -> number(value)
    }

    /** "hoje às 14:32", "ontem às 09:05", "22/10 às 14:32", or with the year when it differs. */
    fun time(ms: Long, nowMs: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(ms).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = when {
            at.toLocalDate() == today -> "hoje"
            at.toLocalDate() == today.minusDays(1) -> "ontem"
            at.year == today.year -> dayMonth.format(at)
            else -> fullDate.format(at)
        }
        return "$day às ${time.format(at)}"
    }

    /** "24/10/2026 às 14:32". */
    fun dateTime(ms: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(ms).atZone(zone)
        return "${fullDate.format(at)} às ${time.format(at)}"
    }

    /** "agora mesmo", "há 5 min", "há 3 h", "há 2 dias". */
    fun relative(ms: Long, nowMs: Long): String {
        val minutes = (nowMs - ms) / 60_000
        return when {
            minutes < 1 -> "agora mesmo"
            minutes < 60 -> "há $minutes min"
            minutes < 24 * 60 -> "há ${minutes / 60} h"
            minutes < 48 * 60 -> "há 1 dia"
            else -> "há ${minutes / (24 * 60)} dias"
        }
    }
}
