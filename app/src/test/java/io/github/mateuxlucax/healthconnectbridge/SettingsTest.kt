package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val settings = Settings(InMemoryPrefs())

    @Test
    fun onboardingNotDoneOnFreshInstall() {
        assertFalse(settings.onboardingDone)
        settings.serverUrl = "http://192.168.0.10:8080"
        settings.token = "t"
        assertFalse(settings.onboardingDone) // configured but never synced: still in setup
    }

    @Test
    fun onboardingDoneWhenFinished() {
        settings.onboardingDone = true
        assertTrue(settings.onboardingDone)
    }

    @Test
    fun upgradeFromAVersionWithoutOnboarding_skipsIt() {
        settings.serverUrl = "http://192.168.0.10:8080"
        settings.token = "t"
        settings.saveChangesToken("token", "StepsRecord")
        assertTrue(settings.onboardingDone)
    }
}
