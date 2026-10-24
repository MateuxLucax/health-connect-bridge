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
 * Runs the connection test in [scope] and holds its last result. A plain state holder used by [SetupForm].
 * It gets only plain data: whether the app may reach the server is settled by the screen, which can ask for
 * the permission, so this coroutine never holds the activity.
 */
class ConnectionTest(
    private val scope: CoroutineScope,
    private val exporter: ExporterClient = ExporterClient(),
) {
    private val _result = MutableStateFlow<TestResult?>(null)
    val result: StateFlow<TestResult?> = _result.asStateFlow()

    /** The running test; cancelled when superseded, so an older answer can't overwrite a newer state. */
    private var job: Job? = null

    /** Shows the test as running while the screen settles local network access; [run] or [clear] follows. */
    fun markRunning() {
        job?.cancel()
        job = null
        _result.value = TestResult(null)
    }

    /**
     * Tests [url] and [token]. [localNetworkAllowed] is false when the server is on the local network and the
     * app may not reach it (Android 17+); the test then fails without a request instead of timing out.
     */
    fun run(url: String, token: String, localNetworkAllowed: Boolean = true) {
        job?.cancel()
        _result.value = TestResult(null)
        job = scope.launch {
            val outcome = test(url.trim(), token.trim(), localNetworkAllowed)
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

    private suspend fun test(url: String, token: String, localNetworkAllowed: Boolean): TestResult = try {
        if (!ServerUrl.isValid(url)) throw SetupException(SyncError.Kind.INVALID_URL)
        if (!localNetworkAllowed) throw SetupException(SyncError.Kind.LOCAL_NETWORK_PERMISSION)
        exporter.check(url, token)
        TestResult(true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TestResult(false, SyncErrors.of(e))
    }
}
