package io.github.mateuxlucax.healthconnectbridge.ui.components

import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The server form and history import, shared by the onboarding and Settings ViewModels (each owns one, in its
 * viewModelScope): the saved values, saving, the local network check and the [ConnectionTest].
 *
 * The test result lives as long as the ViewModel, so it survives rotation. The screens clear it only on
 * explicit triggers: a field edit, or leaving the server step or the Settings tab.
 */
class SetupForm(
    scope: CoroutineScope,
    private val settings: SettingsStore,
    exporter: ExporterClient,
    /** Enqueues a history import of that many days (SyncScheduler.backfill). */
    private val requestBackfill: (Int) -> Unit,
    /** Whether Android 17 local network protection would block connecting to a URL (AppContainer). */
    private val localNetworkBlocked: suspend (String) -> Boolean,
) {
    private val connectionTest = ConnectionTest(scope, exporter)

    /** The connection test result, null when there is none. */
    val testResult: StateFlow<TestResult?> get() = connectionTest.result

    /** Saved values, to fill the form when it opens. */
    val savedServerUrl: String get() = settings.serverUrl
    val savedToken: String get() = settings.token

    fun save(url: String, token: String) {
        settings.serverUrl = url.trim()
        settings.token = token.trim()
    }

    fun importHistory(days: Int) = requestBackfill(days)

    /** True when the server at [url] is on the local network and access isn't granted (Android 17+). */
    suspend fun isLocalNetworkBlocked(url: String): Boolean = localNetworkBlocked(url.trim())

    /** Shows the test as running while the screen settles local network access. */
    fun markTestRunning() = connectionTest.markRunning()

    /** [localNetworkAllowed]: whether the app may reach the URL, settled by the screen (see [ConnectionTest]). */
    fun testConnection(url: String, token: String, localNetworkAllowed: Boolean) =
        connectionTest.run(url, token, localNetworkAllowed)

    fun clearTest() = connectionTest.clear()
}
