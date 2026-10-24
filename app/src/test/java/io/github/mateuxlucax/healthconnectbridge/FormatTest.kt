package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun ms(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun valuesWithUnits() {
        assertEquals("72 bpm", Format.value("health_heart_rate_bpm", 72.0))
        assertEquals("8.421 passos", Format.value("health_steps", 8421.0))
        assertEquals("1 passo", Format.value("health_steps", 1.0))
        assertEquals("72,5 kg", Format.value("health_weight_kg", 72.5))
        assertEquals("56,5 kcal", Format.value("health_active_calories_kcal", 56.5))
        assertEquals("97%", Format.value("health_oxygen_saturation_percent", 97.0))
        assertEquals("850 m", Format.value("health_distance_meters", 850.0))
        assertEquals("1,25 km", Format.value("health_distance_meters", 1250.0))
        assertEquals("+0,3 °C", Format.value("health_skin_temperature_delta_celsius", 0.3))
        assertEquals("-0,2 °C", Format.value("health_skin_temperature_delta_celsius", -0.2))
        assertEquals("1h30", Format.value("health_sleep_stage_seconds", 5400.0))
        assertEquals("45 min", Format.value("health_exercise_session_seconds", 2700.0))
        assertEquals("7h00", Format.value("health_sleep_session_seconds", 25200.0))
    }

    @Test
    fun timeOfValue() {
        val now = ms("2026-10-24T15:00:00-03:00")
        assertEquals("hoje às 14:32", Format.time(ms("2026-10-24T14:32:00-03:00"), now, zone))
        assertEquals("ontem às 09:05", Format.time(ms("2026-10-23T09:05:00-03:00"), now, zone))
        assertEquals("02/10 às 23:59", Format.time(ms("2026-10-02T23:59:00-03:00"), now, zone))
        assertEquals("31/12/2025 às 08:00", Format.time(ms("2025-12-31T08:00:00-03:00"), now, zone))
    }

    @Test
    fun lastSync() {
        val now = ms("2026-10-24T15:00:00-03:00")
        assertEquals("24/10/2026 às 14:32", Format.dateTime(ms("2026-10-24T14:32:00-03:00"), zone))
        assertEquals("agora mesmo", Format.relative(now - 30_000, now))
        assertEquals("há 5 min", Format.relative(now - 5 * 60_000, now))
        assertEquals("há 3 h", Format.relative(now - 3 * 3_600_000, now))
        assertEquals("há 1 dia", Format.relative(now - 30 * 3_600_000L, now))
        assertEquals("há 4 dias", Format.relative(now - 4 * 86_400_000L, now))
    }
}
