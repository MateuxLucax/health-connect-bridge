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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the onboarding shows besides its form: the connection test result of the server step. */
data class OnboardingUiState(val testResult: TestResult? = null)

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

    val uiState: StateFlow<OnboardingUiState> = form.testResult
        .map { OnboardingUiState(it) }
        .stateIn(viewModelScope, WhileUiSubscribed, OnboardingUiState(form.testResult.value))

    /** Whether "Next" is enabled: the server step needs a valid URL and a token, the permissions step one read. */
    fun canContinue(step: Int, url: String, token: String, status: DeviceStatus?): Boolean = when (step) {
        SERVER_STEP -> ServerUrl.isValid(url) && token.isNotBlank()
        PERMISSIONS_STEP -> (status?.grantedReads ?: 0) > 0
        else -> true
    }

    /** Onboarding is done for good: the periodic sync starts. */
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
