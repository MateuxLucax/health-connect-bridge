package io.github.mateuxlucax.healthconnectbridge.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.model.LastValue
import io.github.mateuxlucax.healthconnectbridge.ui.components.DataType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToLong

/** Display formatting for values, names and times, in the language and locale of [strings]. */
class Format(private val strings: Strings) {
    private val locale = strings.locale
    private val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private val dayMonth = DateTimeFormatter.ofPattern(strings.get(R.string.pattern_day_month), locale)
    private val fullDate = DateTimeFormatter.ofPattern(strings.get(R.string.pattern_date), locale)

    fun number(value: Double, maxDecimals: Int = 1): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = maxDecimals
        }.format(rounded(value, maxDecimals))

    /** [value] rounded to [decimals] places as shown, so signs and units are chosen on the shown value ("0", not "-0"). */
    private fun rounded(value: Double, decimals: Int): Double =
        BigDecimal(value).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()

    /** "45 min", "1h 32m" / "1h32". */
    fun duration(seconds: Double): String {
        val minutes = (seconds / 60).roundToLong()
        return if (minutes < 60) {
            strings.get(R.string.duration_minutes, minutes)
        } else {
            strings.get(R.string.duration_hours, minutes / 60, minutes % 60)
        }
    }

    /** Value of [metric] with its unit, e.g. "72 bpm", "8,421 steps" / "8.421 passos". Unit symbols aren't translated. */
    fun value(metric: String, value: Double): String = when (metric) {
        "health_heart_rate_bpm", "health_resting_heart_rate_bpm" -> "${number(value, 0)} bpm"
        "health_heart_rate_variability_rmssd_ms" -> "${number(value, 0)} ms"
        "health_oxygen_saturation_percent", "health_body_fat_percent" -> "${number(value)}%"
        "health_respiratory_rate_breaths_per_minute" -> strings.get(R.string.unit_breaths_per_minute, number(value))
        "health_skin_temperature_delta_celsius" -> rounded(value, 1).let { (if (it > 0) "+" else "") + "${number(it)} °C" }
        "health_steps" -> strings.plural(R.plurals.unit_steps, value.toInt(), number(value, 0))
        "health_distance_meters" ->
            if (rounded(value, 0) < 1000) "${number(value, 0)} m" else "${number(value / 1000, 2)} km"
        "health_active_calories_kcal", "health_total_calories_kcal" -> "${number(value)} kcal"
        "health_sleep_stage_seconds", "health_sleep_session_seconds", "health_exercise_session_seconds" ->
            duration(value)
        "health_weight_kg" -> "${number(value)} kg"
        "health_vo2_max_ml_per_kg_min" -> "${number(value)} ml/kg/min"
        else -> number(value)
    }

    /** Name of an exported value, e.g. "Deep sleep" or "Exercise: running". */
    fun name(type: DataType, value: LastValue): String = when (value.metric) {
        "health_sleep_session_seconds" -> strings.get(R.string.sleep_total)
        "health_sleep_stage_seconds" ->
            SLEEP_STAGES[value.label]?.let { strings.get(it) } ?: strings.get(R.string.sleep_other, value.label.orEmpty())
        "health_exercise_session_seconds" -> strings.get(
            R.string.exercise_name,
            EXERCISES[value.label]?.let { strings.get(it) } ?: value.label.orEmpty().replace('_', ' '),
        )
        else -> strings.get(type.name)
    }

    /** "Today at 2:32 PM" / "hoje às 14:32", yesterday, a day this year, or a full date. */
    fun time(ms: Long, nowMs: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(ms).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val clockText = clock.format(at)
        return when {
            at.toLocalDate() == today -> strings.get(R.string.time_today, clockText)
            at.toLocalDate() == today.minusDays(1) -> strings.get(R.string.time_yesterday, clockText)
            at.year == today.year -> strings.get(R.string.time_on_date, dayMonth.format(at), clockText)
            else -> strings.get(R.string.time_on_date, fullDate.format(at), clockText)
        }
    }

    /** "Oct 24, 2026 at 2:32 PM" / "24/10/2026 às 14:32". */
    fun dateTime(ms: Long, zone: ZoneId): String {
        val at = Instant.ofEpochMilli(ms).atZone(zone)
        return strings.get(R.string.time_on_date, fullDate.format(at), clock.format(at))
    }

    /** "Just now", "5 min ago", "3 h ago", "2 days ago" (localized). */
    fun relative(ms: Long, nowMs: Long): String {
        val minutes = (nowMs - ms) / 60_000
        val days = minutes / (24 * 60)
        return when {
            minutes < 1 -> strings.get(R.string.relative_now)
            minutes < 60 -> strings.get(R.string.relative_minutes, minutes)
            minutes < 24 * 60 -> strings.get(R.string.relative_hours, minutes / 60)
            else -> strings.plural(R.plurals.relative_days, days.toInt(), days)
        }
    }

    private companion object {
        val SLEEP_STAGES = mapOf(
            "awake" to R.string.sleep_awake,
            "sleeping" to R.string.sleep_sleeping,
            "out_of_bed" to R.string.sleep_out_of_bed,
            "light" to R.string.sleep_light,
            "deep" to R.string.sleep_deep,
            "rem" to R.string.sleep_rem,
            "awake_in_bed" to R.string.sleep_awake_in_bed,
            "unknown" to R.string.sleep_unknown,
        )

        val EXERCISES = mapOf(
            "running" to R.string.exercise_running,
            "walking" to R.string.exercise_walking,
            "biking" to R.string.exercise_biking,
            "swimming_pool" to R.string.exercise_swimming_pool,
            "strength_training" to R.string.exercise_strength_training,
            "yoga" to R.string.exercise_yoga,
            "hiking" to R.string.exercise_hiking,
            "other_workout" to R.string.exercise_other_workout,
        )
    }
}

@Composable
fun rememberFormat(): Format {
    val strings = rememberStrings()
    return remember(strings) { Format(strings) }
}
