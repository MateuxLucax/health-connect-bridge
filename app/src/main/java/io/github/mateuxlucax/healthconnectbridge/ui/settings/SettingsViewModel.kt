package io.github.mateuxlucax.healthconnectbridge.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.BackfillResult
import io.github.mateuxlucax.healthconnectbridge.ui.components.ConnectionTest
import io.github.mateuxlucax.healthconnectbridge.ui.components.TestResult
import io.github.mateuxlucax.healthconnectbridge.ui.WhileUiSubscribed
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
    private val settings: SettingsStore,
    exporter: ExporterClient,
    /** Enqueues a history import of that many days (SyncScheduler.backfill). */
    private val requestBackfill: (Int) -> Unit,
) : ViewModel() {
    private val connectionTest = ConnectionTest(viewModelScope, exporter)

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.syncState, connectionTest.result) { sync, test -> SettingsUiState(sync.lastBackfill, test) }
            .stateIn(
                viewModelScope,
                WhileUiSubscribed,
                SettingsUiState(settings.lastBackfill, connectionTest.result.value),
            )

    /** Saved values, to fill the form when it opens. */
    val savedServerUrl: String get() = settings.serverUrl
    val savedToken: String get() = settings.token

    fun save(url: String, token: String) {
        settings.serverUrl = url.trim()
        settings.token = token.trim()
    }

    fun importHistory(days: Int) = requestBackfill(days)

    fun testConnection(url: String, token: String, ensureLocalNetwork: suspend (String) -> Boolean) =
        connectionTest.run(url, token, ensureLocalNetwork)

    fun clearTest() = connectionTest.clear()
}
