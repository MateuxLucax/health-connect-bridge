package io.github.mateuxlucax.healthconnectbridge.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.healthconnectbridge.data.local.LastValues
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.LastValue
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.ui.WhileUiSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What Home shows: the last successful sync (0 = never), the last error and the last value per metric. */
data class HomeUiState(
    val lastSuccessMs: Long = 0L,
    val lastError: SyncError? = null,
    val values: List<LastValue> = emptyList(),
)

/** State of the Home tab, updated whenever a sync records its outcome or uploads values. */
class HomeViewModel(
    settings: SettingsStore,
    lastValues: LastValues,
    /** Enqueues a one-time sync (SyncScheduler.syncNow). */
    private val requestSync: () -> Unit,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(settings.syncState, lastValues.values) { sync, values ->
            HomeUiState(sync.lastSuccessMs, sync.lastError, values)
        }.stateIn(
            viewModelScope,
            WhileUiSubscribed,
            // The stored state right away, so the first frame doesn't show "never".
            settings.currentSyncState().let { HomeUiState(it.lastSuccessMs, it.lastError, lastValues.all()) },
        )

    fun syncNow() = requestSync()
}
