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
        vm.save(" http://192.168.0.10:8080 ", " secret ")
        assertEquals("http://192.168.0.10:8080", settings.serverUrl)
        assertEquals("secret", settings.token)
        assertEquals("http://192.168.0.10:8080", vm.savedServerUrl)
        assertEquals("secret", vm.savedToken)
    }

    @Test
    fun importHistory_requestsABackfill() {
        vm.importHistory(30)
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
        vm.testConnection(server.url, "t") { true }
        val state = withTimeout(10_000) { vm.uiState.first { it.testResult?.ok != null } }
        assertEquals(true, state.testResult?.ok)
        assertEquals("Bearer t", server.requests.single().headers["authorization"])
    }

    @Test
    fun connectionTest_deniedLocalNetwork_failsWithoutARequest() = runBlocking {
        vm.testConnection(server.url, "t") { false }
        val state = withTimeout(10_000) { vm.uiState.first { it.testResult?.ok != null } }
        assertEquals(SyncError(SyncError.Kind.LOCAL_NETWORK_PERMISSION), state.testResult?.error)
        assertEquals(0, server.requests.size)
    }

    @Test
    fun clearTest_removesTheResult() = runBlocking {
        vm.testConnection("ftp://example.com", "t") { true }
        withTimeout(10_000) { vm.uiState.first { it.testResult?.ok == false } }
        vm.clearTest()
        assertNull(withTimeout(10_000) { vm.uiState.first { it.testResult == null } }.testResult)
    }
}
