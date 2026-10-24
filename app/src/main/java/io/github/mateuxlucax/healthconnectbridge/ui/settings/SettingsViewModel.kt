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

/** What Settings shows besides the form: the last history import and the connection test result. */
data class SettingsUiState(
    val lastBackfill: BackfillResult? = null,
    val testResult: TestResult? = null,
)

/** State and actions of the Settings tab. The form fields are UI element state, kept by the screen. */
class SettingsViewModel(
    settings: SettingsStore,
    exporter: ExporterClient,
    /** Enqueues a history import of that many days (SyncScheduler.backfill). */
    requestBackfill: (Int) -> Unit,
    /** Whether Android 17 local network protection would block connecting to a URL. */
    localNetworkBlocked: suspend (String) -> Boolean = { false },
) : ViewModel() {
    /** The server section's form and the history import. */
    val form = SetupForm(viewModelScope, settings, exporter, requestBackfill, localNetworkBlocked)

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.syncState, form.testResult) { sync, test -> SettingsUiState(sync.lastBackfill, test) }
            .stateIn(
                viewModelScope,
                WhileUiSubscribed,
                SettingsUiState(settings.lastBackfill, form.testResult.value),
            )
}
