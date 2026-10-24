package io.github.mateuxlucax.healthconnectbridge.ui.components

import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.SetupException
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.sync.SyncErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Outcome of "Test connection": [ok] is null while the request runs; [error] is set when it failed. */
class TestResult(val ok: Boolean?, val error: SyncError? = null)

/**
 * Runs the connection test in [scope] and holds its last result. A plain state holder shared by the
 * onboarding and settings ViewModels.
 */
class ConnectionTest(
    private val scope: CoroutineScope,
    private val exporter: ExporterClient = ExporterClient(),
) {
    private val _result = MutableStateFlow<TestResult?>(null)
    val result: StateFlow<TestResult?> = _result.asStateFlow()

    /** The running test; cancelled when superseded, so an older answer can't overwrite a newer state. */
    private var job: Job? = null

    /**
     * Tests [url] and [token]. [ensureLocalNetwork] makes sure the app may reach the URL (asking for the local
     * network permission if needed) and returns false when it may not.
     */
    fun run(url: String, token: String, ensureLocalNetwork: suspend (String) -> Boolean = { true }) {
        job?.cancel()
        _result.value = TestResult(null)
        job = scope.launch {
            val outcome = test(url.trim(), token.trim(), ensureLocalNetwork)
            // A blocking request can still end (e.g. with an IOException) after cancellation; drop its outcome.
            ensureActive()
            _result.value = outcome
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        _result.value = null
    }

    private suspend fun test(url: String, token: String, ensureLocalNetwork: suspend (String) -> Boolean): TestResult = try {
        if (!ServerUrl.isValid(url)) throw SetupException(SyncError.Kind.INVALID_URL)
        // Asking first avoids a connect timeout on Android 17 when the permission is missing.
        if (!ensureLocalNetwork(url)) throw SetupException(SyncError.Kind.LOCAL_NETWORK_PERMISSION)
        exporter.check(url, token)
        TestResult(true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TestResult(false, SyncErrors.of(e))
    }
}
