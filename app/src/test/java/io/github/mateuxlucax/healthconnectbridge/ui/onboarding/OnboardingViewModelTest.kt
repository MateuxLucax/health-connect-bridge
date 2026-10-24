package io.github.mateuxlucax.healthconnectbridge.ui.onboarding

import androidx.health.connect.client.HealthConnectClient
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.testing.FakeServer
import io.github.mateuxlucax.healthconnectbridge.testing.InMemoryPrefs
import io.github.mateuxlucax.healthconnectbridge.testing.MainDispatcherRule
import io.github.mateuxlucax.healthconnectbridge.ui.onboarding.OnboardingViewModel.Companion.BATTERY_STEP
import io.github.mateuxlucax.healthconnectbridge.ui.onboarding.OnboardingViewModel.Companion.PERMISSIONS_STEP
import io.github.mateuxlucax.healthconnectbridge.ui.onboarding.OnboardingViewModel.Companion.SERVER_STEP
import io.github.mateuxlucax.healthconnectbridge.ui.onboarding.OnboardingViewModel.Companion.STEPS
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeServer()
    private val settings = SettingsStore(InMemoryPrefs())
    private val backfills = mutableListOf<Int>()
    private var periodicScheduled = 0
    /** Created in the test, after [main] replaced Dispatchers.Main. */
    private val vm by lazy { OnboardingViewModel(settings, ExporterClient(), { backfills += it }, { periodicScheduled++ }) }

    private fun status(grantedReads: Int) = DeviceStatus(
        healthConnect = HealthConnectClient.SDK_AVAILABLE,
        granted = grantedReads,
        requested = 17,
        grantedReads = grantedReads,
        batteryExempt = false,
    )

    @After
    fun tearDown() = server.close()

    @Test
    fun welcomeBatteryAndHistory_canAlwaysContinue() {
        for (step in listOf(0, BATTERY_STEP, STEPS - 1)) assertTrue("step $step", vm.canContinue(step, "", "", null))
    }

    @Test
    fun serverStep_needsAValidUrlAndAToken() {
        assertFalse(vm.canContinue(SERVER_STEP, "", "", null))
        assertFalse(vm.canContinue(SERVER_STEP, "http://192.168.0.10:8080", " ", null))
        assertFalse(vm.canContinue(SERVER_STEP, "ftp://192.168.0.10", "t", null))
        assertFalse(vm.canContinue(SERVER_STEP, "192.168.0.10:8080", "t", null))
        assertTrue(vm.canContinue(SERVER_STEP, "http://192.168.0.10:8080", "t", null))
    }

    @Test
    fun permissionsStep_needsAtLeastOneGrantedRead() {
        assertFalse(vm.canContinue(PERMISSIONS_STEP, "", "", null)) // status not loaded yet
        assertFalse(vm.canContinue(PERMISSIONS_STEP, "", "", status(grantedReads = 0)))
        assertTrue(vm.canContinue(PERMISSIONS_STEP, "", "", status(grantedReads = 1)))
    }

    @Test
    fun saveServer_trimsAndStores() {
        vm.saveServer(" http://192.168.0.10:8080 ", " t ")
        assertEquals("http://192.168.0.10:8080", settings.serverUrl)
        assertEquals("t", settings.token)
        assertFalse(settings.onboardingDone)
    }

    @Test
    fun finish_marksOnboardingDone_andSchedulesThePeriodicSync() {
        vm.finish()
        assertTrue(settings.onboardingDone)
        assertEquals(1, periodicScheduled)
    }

    @Test
    fun importHistory_requestsABackfill() {
        vm.importHistory(7)
        assertEquals(listOf(7), backfills)
    }

    @Test
    fun connectionTest_runningThenOk() = runBlocking {
        val permissionAnswer = CompletableDeferred<Boolean>()
        vm.testConnection(" ${server.url} ", "t") { permissionAnswer.await() }
        val running = withTimeout(10_000) { vm.uiState.first { it.testResult != null } }
        assertNull(running.testResult?.ok)

        permissionAnswer.complete(true)
        val state = withTimeout(10_000) { vm.uiState.first { it.testResult?.ok != null } }
        assertEquals(true, state.testResult?.ok)
        assertEquals(1, server.requests.size)
    }

    @Test
    fun connectionTest_rejectedToken() = runBlocking {
        server.status = { 401 }
        vm.testConnection(server.url, "wrong") { true }
        val state = withTimeout(10_000) { vm.uiState.first { it.testResult?.ok != null } }
        assertEquals(false, state.testResult?.ok)
        assertEquals(SyncError(SyncError.Kind.UNAUTHORIZED), state.testResult?.error)
    }
}
