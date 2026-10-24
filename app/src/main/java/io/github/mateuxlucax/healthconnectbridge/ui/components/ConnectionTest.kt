package io.github.mateuxlucax.healthconnectbridge.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.SetupException
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.sync.SyncErrors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Outcome of "Test connection": [ok] is null while the request runs; [error] is set when it failed. */
class TestResult(val ok: Boolean?, val error: SyncError? = null)

/**
 * Runs the connection test and holds its last result. [ensureLocalNetwork] makes sure the app may reach the
 * URL (asking for the local network permission if needed) and returns false when it may not.
 */
class ConnectionTest(
    private val scope: CoroutineScope,
    private val exporter: ExporterClient = ExporterClient(),
    private val ensureLocalNetwork: suspend (String) -> Boolean = { true },
) {
    var result by mutableStateOf<TestResult?>(null)
        private set

    /** The running test; cancelled when superseded, so an older answer can't overwrite a newer state. */
    private var job: Job? = null

    fun run(url: String, token: String) {
        job?.cancel()
        result = TestResult(null)
        job = scope.launch {
            val outcome = test(url.trim(), token.trim())
            // A blocking request can still end (e.g. with an IOException) after cancellation; drop its outcome.
            ensureActive()
            result = outcome
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        result = null
    }

    private suspend fun test(url: String, token: String): TestResult = try {
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

@Composable
fun rememberConnectionTest(ensureLocalNetwork: suspend (String) -> Boolean): ConnectionTest {
    val scope = rememberCoroutineScope()
    return remember { ConnectionTest(scope, ensureLocalNetwork = ensureLocalNetwork) }
}
