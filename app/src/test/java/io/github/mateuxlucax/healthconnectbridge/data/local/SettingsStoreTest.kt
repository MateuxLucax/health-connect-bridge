package io.github.mateuxlucax.healthconnectbridge.data.local

import io.github.mateuxlucax.healthconnectbridge.model.BackfillResult
import io.github.mateuxlucax.healthconnectbridge.model.SyncState
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.testing.InMemoryPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    private val prefs = InMemoryPrefs()
    private val settings = SettingsStore(prefs)

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
        val s = SettingsStore(prefs)
        s.lastSuccessMs = 1
        s.lastError = SyncError(SyncError.Kind.NETWORK, "x")
        s.lastBackfill = BackfillResult(1, null)
        assertTrue(SettingsStore.SYNC_STATE_KEYS.containsAll(prefs.values.keys))

        s.serverUrl = "http://x"
        s.token = "t"
        s.onboardingDone = true
        assertFalse(SettingsStore.SYNC_STATE_KEYS.any { it in setOf("server_url", "token", "onboarding_done") })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun syncState_emitsTheCurrentStateThenEachChange_andStopsListening() = runTest {
        settings.lastSuccessMs = 1
        val states = mutableListOf<SyncState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { settings.syncState.toList(states) }

        settings.lastError = SyncError(SyncError.Kind.NETWORK, "x")
        settings.serverUrl = "http://x" // not sync state: no emission
        settings.lastSuccessMs = 2
        settings.lastError = null
        settings.lastBackfill = BackfillResult(3, null)
        job.cancel()
        advanceUntilIdle()
        settings.lastSuccessMs = 4

        assertEquals(
            listOf(
                SyncState(1, null, null),
                SyncState(1, SyncError(SyncError.Kind.NETWORK, "x"), null),
                SyncState(2, SyncError(SyncError.Kind.NETWORK, "x"), null),
                SyncState(2, null, null),
                SyncState(2, null, BackfillResult(3, null)),
            ),
            states,
        )
        assertEquals(0, prefs.listenerCount)
    }
}
