package io.github.mateuxlucax.healthconnectbridge.ui.home

import io.github.mateuxlucax.healthconnectbridge.data.local.LastValues
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.Sample
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.testing.InMemoryPrefs
import io.github.mateuxlucax.healthconnectbridge.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val prefs = InMemoryPrefs()
    private val settings = SettingsStore(prefs)
    private val lastValues = LastValues(prefs)
    private var syncRequests = 0

    private fun viewModel() = HomeViewModel(settings, lastValues) { syncRequests++ }

    private fun steps(value: Int, t: Long) = Sample("health_steps", mapOf("source" to "a"), value, t)

    @Test
    fun startsWithTheStoredState() {
        settings.lastSuccessMs = 1_000
        settings.lastError = SyncError(SyncError.Kind.UNAUTHORIZED)
        lastValues.record(listOf(steps(1234, 500)))

        val state = viewModel().uiState.value

        assertEquals(1_000L, state.lastSuccessMs)
        assertEquals(SyncError(SyncError.Kind.UNAUTHORIZED), state.lastError)
        assertEquals(listOf("health_steps" to 1234.0), state.values.map { it.metric to it.value })
    }

    @Test
    fun freshInstall_showsNeverAndNoValues() {
        val state = viewModel().uiState.value
        assertEquals(HomeUiState(), state)
    }

    @Test
    fun showsWhatALaterSyncRecords() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        // A failed run, then a successful one that uploaded values.
        settings.lastError = SyncError(SyncError.Kind.SERVER, "HTTP 502")
        assertEquals(SyncError(SyncError.Kind.SERVER, "HTTP 502"), vm.uiState.value.lastError)
        assertEquals(0L, vm.uiState.value.lastSuccessMs)

        lastValues.record(listOf(steps(10, 2_000)))
        settings.lastSuccessMs = 3_000
        settings.lastError = null

        val state = vm.uiState.value
        assertEquals(3_000L, state.lastSuccessMs)
        assertNull(state.lastError)
        assertEquals(listOf(10.0), state.values.map { it.value })
    }

    @Test
    fun syncNow_requestsASync() {
        viewModel().syncNow()
        assertEquals(1, syncRequests)
    }
}
