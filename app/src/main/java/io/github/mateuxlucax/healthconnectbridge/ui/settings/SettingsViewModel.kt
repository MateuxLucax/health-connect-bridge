package io.github.mateuxlucax.healthconnectbridge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.BackfillResult
import io.github.mateuxlucax.healthconnectbridge.ui.WhileUiSubscribed
import io.github.mateuxlucax.healthconnectbridge.ui.components.SetupForm
import io.github.mateuxlucax.healthconnectbridge.ui.components.TestResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What Settings shows besides the form: the last history import, the connection test result and the sync interval. */
data class SettingsUiState(
    val lastBackfill: BackfillResult? = null,
    val testResult: TestResult? = null,
    val syncIntervalMinutes: Int = SettingsStore.DEFAULT_SYNC_INTERVAL,
)

/** State and actions of the Settings tab. The form fields are UI element state, kept by the screen. */
class SettingsViewModel(
    private val settings: SettingsStore,
    exporter: ExporterClient,
    /** Enqueues a history import of that many days (SyncScheduler.backfill). */
    requestBackfill: (Int) -> Unit,
    /** Applies a new interval to the scheduled periodic sync (SyncScheduler.reschedulePeriodic). */
    private val reschedulePeriodicSync: () -> Unit,
    /** Whether Android 17 local network protection would block connecting to a URL. */
    localNetworkBlocked: suspend (String) -> Boolean = { false },
) : ViewModel() {
    /** The server section's form and the history import. */
    val form = SetupForm(viewModelScope, settings, exporter, requestBackfill, localNetworkBlocked)

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.syncState, form.testResult, settings.syncInterval) { sync, test, interval ->
            SettingsUiState(sync.lastBackfill, test, interval)
        }.stateIn(
            viewModelScope,
            WhileUiSubscribed,
            SettingsUiState(settings.lastBackfill, form.testResult.value, settings.syncIntervalMinutes),
        )

    /** Saves the interval and reschedules the periodic sync with it; picking the current one does nothing. */
    fun setSyncInterval(minutes: Int) {
        if (minutes == settings.syncIntervalMinutes) return
        settings.syncIntervalMinutes = minutes
        reschedulePeriodicSync()
    }
}
