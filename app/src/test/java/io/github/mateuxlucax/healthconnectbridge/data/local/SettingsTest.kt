package io.github.mateuxlucax.healthconnectbridge.data.local

import io.github.mateuxlucax.healthconnectbridge.model.BackfillResult
import io.github.mateuxlucax.healthconnectbridge.model.Sample
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.testing.InMemoryPrefs
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

    @Test
    fun syncStateKeys_coverWhatSyncsWrite_notTheServerSettings() {
        val prefs = InMemoryPrefs()
        val s = Settings(prefs)
        s.lastSuccessMs = 1
        s.lastError = SyncError(SyncError.Kind.NETWORK, "x")
        s.lastBackfill = BackfillResult(1, null)
        LastValues(prefs).record(listOf(Sample("health_steps", mapOf("source" to "a"), 1, 1L)))
        assertTrue(Settings.SYNC_STATE_KEYS.containsAll(prefs.values.keys))

        s.serverUrl = "http://x"
        s.token = "t"
        s.onboardingDone = true
        assertFalse(Settings.SYNC_STATE_KEYS.any { it in setOf("server_url", "token", "onboarding_done") })
    }
}
