package io.github.mateuxlucax.healthconnectbridge.ui.settings

import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.BackfillResult
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.testing.FakeServer
import io.github.mateuxlucax.healthconnectbridge.testing.InMemoryPrefs
import io.github.mateuxlucax.healthconnectbridge.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val server = FakeServer()
    private val settings = SettingsStore(InMemoryPrefs())
    private val backfills = mutableListOf<Int>()
    /** Created in the test, after [main] replaced Dispatchers.Main. */
    private val vm by lazy { SettingsViewModel(settings, ExporterClient()) { backfills += it } }

    @After
    fun tearDown() = server.close()

    @Test
    fun save_trimsAndStores() {
        vm.form.save(" http://192.168.0.10:8080 ", " secret ")
        assertEquals("http://192.168.0.10:8080", settings.serverUrl)
        assertEquals("secret", settings.token)
        assertEquals("http://192.168.0.10:8080", vm.form.savedServerUrl)
        assertEquals("secret", vm.form.savedToken)
    }

    @Test
    fun importHistory_requestsABackfill() {
        vm.form.importHistory(30)
        assertEquals(listOf(30), backfills)
    }

    @Test
    fun showsTheLastHistoryImport() = runTest {
        assertNull(vm.uiState.value.lastBackfill)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        settings.lastBackfill = BackfillResult(5, SyncError(SyncError.Kind.NETWORK, "timeout"))
        assertEquals(BackfillResult(5, SyncError(SyncError.Kind.NETWORK, "timeout")), vm.uiState.value.lastBackfill)
    }

    @Test
    fun connectionTest_ok() = runBlocking {
        vm.form.testConnection(server.url, "t", localNetworkAllowed = true)
        val state = withTimeout(10_000) { vm.uiState.first { it.testResult?.ok != null } }
        assertEquals(true, state.testResult?.ok)
        assertEquals("Bearer t", server.requests.single().headers["authorization"])
    }

    @Test
    fun connectionTest_deniedLocalNetwork_failsWithoutARequest() = runBlocking {
        vm.form.testConnection(server.url, "t", localNetworkAllowed = false)
        val state = withTimeout(10_000) { vm.uiState.first { it.testResult?.ok != null } }
        assertEquals(SyncError(SyncError.Kind.LOCAL_NETWORK_PERMISSION), state.testResult?.error)
        assertEquals(0, server.requests.size)
    }

    @Test
    fun clearTest_removesTheResult() = runBlocking {
        vm.form.testConnection("ftp://example.com", "t", localNetworkAllowed = true)
        withTimeout(10_000) { vm.uiState.first { it.testResult?.ok == false } }
        vm.form.clearTest()
        assertNull(withTimeout(10_000) { vm.uiState.first { it.testResult == null } }.testResult)
    }

    @Test
    fun connectionTest_survivesTheScreenGoingAway() = runBlocking {
        val screen = launch { vm.uiState.collect {} }
        vm.form.testConnection(server.url, "t", localNetworkAllowed = true)
        withTimeout(10_000) { vm.uiState.first { it.testResult?.ok == true } }
        screen.cancel() // e.g. a rotation: the composition goes, the ViewModel stays

        // Nothing clears the result but an explicit call (field edit, leaving the tab).
        assertEquals(true, withTimeout(10_000) { vm.uiState.first() }.testResult?.ok)
        assertEquals(1, server.requests.size)
    }
}
