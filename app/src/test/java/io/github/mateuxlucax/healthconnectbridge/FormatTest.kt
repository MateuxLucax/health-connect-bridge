package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val en = Format(XmlStrings.of(XmlStrings.ENGLISH))
    private val pt = Format(XmlStrings.of(XmlStrings.PORTUGUESE))
    private fun ms(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    // Newer JDK and Android CLDR data put a narrow no-break space before AM/PM.
    private fun assertText(expected: String, actual: String) = assertEquals(expected, actual.replace(' ', ' '))

    @Test
    fun valuesWithUnits_portuguese() {
        assertEquals("72 bpm", pt.value("health_heart_rate_bpm", 72.0))
        assertEquals("8.421 passos", pt.value("health_steps", 8421.0))
        assertEquals("1 passo", pt.value("health_steps", 1.0))
        assertEquals("72,5 kg", pt.value("health_weight_kg", 72.5))
        assertEquals("56,5 kcal", pt.value("health_active_calories_kcal", 56.5))
        assertEquals("97%", pt.value("health_oxygen_saturation_percent", 97.0))
        assertEquals("16 resp/min", pt.value("health_respiratory_rate_breaths_per_minute", 16.0))
        assertEquals("850 m", pt.value("health_distance_meters", 850.0))
        assertEquals("1,25 km", pt.value("health_distance_meters", 1250.0))
        assertEquals("+0,3 °C", pt.value("health_skin_temperature_delta_celsius", 0.3))
        assertEquals("-0,2 °C", pt.value("health_skin_temperature_delta_celsius", -0.2))
        assertEquals("1h30", pt.value("health_sleep_stage_seconds", 5400.0))
        assertEquals("45 min", pt.value("health_exercise_session_seconds", 2700.0))
        assertEquals("7h00", pt.value("health_sleep_session_seconds", 25200.0))
    }

    @Test
    fun valuesWithUnits_english() {
        assertEquals("72 bpm", en.value("health_heart_rate_bpm", 72.0))
        assertEquals("8,421 steps", en.value("health_steps", 8421.0))
        assertEquals("1 step", en.value("health_steps", 1.0))
        assertEquals("72.5 kg", en.value("health_weight_kg", 72.5))
        assertEquals("16 breaths/min", en.value("health_respiratory_rate_breaths_per_minute", 16.0))
        assertEquals("1.25 km", en.value("health_distance_meters", 1250.0))
        assertEquals("+0.3 °C", en.value("health_skin_temperature_delta_celsius", 0.3))
        assertEquals("1h 30m", en.value("health_sleep_stage_seconds", 5400.0))
        assertEquals("45 min", en.value("health_exercise_session_seconds", 2700.0))
        assertEquals("7h 00m", en.value("health_sleep_session_seconds", 25200.0))
    }

    @Test
    fun timeOfValue_portuguese() {
        val now = ms("2026-10-24T15:00:00-03:00")
        assertEquals("hoje às 14:32", pt.time(ms("2026-10-24T14:32:00-03:00"), now, zone))
        assertEquals("ontem às 09:05", pt.time(ms("2026-10-23T09:05:00-03:00"), now, zone))
        assertEquals("02/10 às 23:59", pt.time(ms("2026-10-02T23:59:00-03:00"), now, zone))
        assertEquals("31/12/2025 às 08:00", pt.time(ms("2025-12-31T08:00:00-03:00"), now, zone))
    }

    @Test
    fun timeOfValue_english() {
        val now = ms("2026-10-24T15:00:00-03:00")
        assertText("Today at 8:49 AM", en.time(ms("2026-10-24T08:49:00-03:00"), now, zone))
        assertText("Yesterday at 9:05 PM", en.time(ms("2026-10-23T21:05:00-03:00"), now, zone))
        assertText("Oct 2 at 11:59 PM", en.time(ms("2026-10-02T23:59:00-03:00"), now, zone))
        assertText("Dec 31, 2025 at 8:00 AM", en.time(ms("2025-12-31T08:00:00-03:00"), now, zone))
    }

    @Test
    fun lastSync_portuguese() {
        val now = ms("2026-10-24T15:00:00-03:00")
        assertEquals("24/10/2026 às 14:32", pt.dateTime(ms("2026-10-24T14:32:00-03:00"), zone))
        assertEquals("Agora mesmo", pt.relative(now - 30_000, now))
        assertEquals("Há 5 min", pt.relative(now - 5 * 60_000, now))
        assertEquals("Há 3 h", pt.relative(now - 3 * 3_600_000, now))
        assertEquals("Há 1 dia", pt.relative(now - 30 * 3_600_000L, now))
        assertEquals("Há 4 dias", pt.relative(now - 4 * 86_400_000L, now))
    }

    @Test
    fun lastSync_english() {
        val now = ms("2026-10-24T15:00:00-03:00")
        assertText("Oct 24, 2026 at 2:32 PM", en.dateTime(ms("2026-10-24T14:32:00-03:00"), zone))
        assertEquals("Just now", en.relative(now - 30_000, now))
        assertEquals("5 min ago", en.relative(now - 5 * 60_000, now))
        assertEquals("3 h ago", en.relative(now - 3 * 3_600_000, now))
        assertEquals("1 day ago", en.relative(now - 30 * 3_600_000L, now))
        assertEquals("4 days ago", en.relative(now - 4 * 86_400_000L, now))
    }

    @Test
    fun names() {
        val sleep = DATA_TYPES.first { "health_sleep_stage_seconds" in it.metrics }
        val exercise = DATA_TYPES.first { "health_exercise_session_seconds" in it.metrics }
        val steps = DATA_TYPES.first { "health_steps" in it.metrics }
        fun value(metric: String, label: String?) = LastValue(metric, label, 1.0, 0L)

        assertEquals("Deep sleep", en.name(sleep, value("health_sleep_stage_seconds", "deep")))
        assertEquals("Sono profundo", pt.name(sleep, value("health_sleep_stage_seconds", "deep")))
        assertEquals("Total sleep", en.name(sleep, value("health_sleep_session_seconds", null)))
        assertEquals("Sono total", pt.name(sleep, value("health_sleep_session_seconds", null)))
        assertEquals("Exercise: running", en.name(exercise, value("health_exercise_session_seconds", "running")))
        assertEquals("Exercício: corrida", pt.name(exercise, value("health_exercise_session_seconds", "running")))
        assertEquals("Exercise: rock climbing", en.name(exercise, value("health_exercise_session_seconds", "rock_climbing")))
        assertEquals("Steps", en.name(steps, value("health_steps", null)))
        assertEquals("Passos", pt.name(steps, value("health_steps", null)))
    }
}
