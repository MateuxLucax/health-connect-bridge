package io.github.mateuxlucax.healthconnectbridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The device status every screen shows: the last loaded [status] (null until the first load) and whether the
 * last load [failed]. The last status stays shown while it reloads; a failed load is shown, not hidden as
 * "no status".
 */
data class DeviceStatusUiState(val status: DeviceStatus? = null, val failed: Boolean = false)

/**
 * Loads the [DeviceStatus] for [App]. It depends on the activity lifecycle (permissions granted in other
 * screens, returning from settings), so App calls [refresh] on resume and after permission requests.
 */
class DeviceStatusViewModel(
    /** DeviceStatusRepository.load. */
    private val load: suspend () -> DeviceStatus,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceStatusUiState())
    val uiState: StateFlow<DeviceStatusUiState> = _uiState.asStateFlow()

    /** The running load; a newer refresh replaces it. */
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = try {
                DeviceStatusUiState(load(), failed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value.copy(failed = true)
            }
        }
    }
}
