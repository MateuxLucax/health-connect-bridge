package io.github.mateuxlucax.healthconnectbridge.model

import androidx.health.connect.client.permission.HealthPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceStatusTest {
    private val steps = "android.permission.health.READ_STEPS"
    private val background = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    @Test
    fun backgroundMissing_whenSupportedButNotGranted() {
        val status = DeviceStatus.from(setOf(steps, background), listOf(steps), setOf(steps), batteryExempt = false)
        assertTrue(status.backgroundMissing)
        assertEquals(1, status.grantedReads)
        assertEquals(1, status.granted)
        assertEquals(2, status.requested)
    }

    @Test
    fun backgroundGranted() {
        val status = DeviceStatus.from(setOf(steps, background), listOf(steps), setOf(steps, background), batteryExempt = true)
        assertFalse(status.backgroundMissing)
        assertEquals(2, status.granted)
    }

    @Test
    fun backgroundNotSupported_isNotMissing() {
        val status = DeviceStatus.from(setOf(steps), listOf(steps), setOf(steps), batteryExempt = false)
        assertFalse(status.backgroundMissing)
    }
}
