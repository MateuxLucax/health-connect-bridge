package io.github.mateuxlucax.healthconnectbridge.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.ui.WhileUiSubscribed
import io.github.mateuxlucax.healthconnectbridge.ui.components.SetupForm
import io.github.mateuxlucax.healthconnectbridge.ui.components.TestResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What the onboarding shows besides its form: the connection test result of the server step and the automatic
 * sync interval of the history step.
 */
data class OnboardingUiState(
    val testResult: TestResult? = null,
    val syncIntervalMinutes: Int = SettingsStore.DEFAULT_SYNC_INTERVAL,
)

/**
 * State and actions of the five onboarding steps (welcome, server, permissions, battery, history). The current
 * step and the form fields are UI element state, kept by the screen.
 */
class OnboardingViewModel(
    private val settings: SettingsStore,
    exporter: ExporterClient,
    /** Enqueues a history import of that many days (SyncScheduler.backfill). */
    requestBackfill: (Int) -> Unit,
    /** Schedules the periodic sync (SyncScheduler.schedulePeriodic). */
    private val schedulePeriodicSync: () -> Unit,
    /** Whether Android 17 local network protection would block connecting to a URL. */
    localNetworkBlocked: suspend (String) -> Boolean = { false },
) : ViewModel() {
    /** The server step's form and the history step's import. */
    val form = SetupForm(viewModelScope, settings, exporter, requestBackfill, localNetworkBlocked)

    val uiState: StateFlow<OnboardingUiState> =
        combine(form.testResult, settings.syncInterval) { test, interval -> OnboardingUiState(test, interval) }
            .stateIn(
                viewModelScope,
                WhileUiSubscribed,
                OnboardingUiState(form.testResult.value, settings.syncIntervalMinutes),
            )

    /** Whether "Next" is enabled: the server step needs a valid URL and a token, the permissions step one read. */
    fun canContinue(step: Int, url: String, token: String, status: DeviceStatus?): Boolean = when (step) {
        SERVER_STEP -> ServerUrl.isValid(url) && token.isNotBlank()
        PERMISSIONS_STEP -> (status?.grantedReads ?: 0) > 0
        else -> true
    }

    /** Only saved: nothing is scheduled until [finish]. */
    fun setSyncInterval(minutes: Int) {
        settings.syncIntervalMinutes = minutes
    }

    /** Onboarding is done for good: the periodic sync starts, at the chosen interval. */
    fun finish() {
        settings.onboardingDone = true
        schedulePeriodicSync()
    }

    companion object {
        const val STEPS = 5
        const val SERVER_STEP = 1
        const val PERMISSIONS_STEP = 2
        const val BATTERY_STEP = 3
    }
}
