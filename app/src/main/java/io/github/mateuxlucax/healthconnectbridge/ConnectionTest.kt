package io.github.mateuxlucax.healthconnectbridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Outcome of "Test connection": [ok] is null while the request runs; [error] is set when it failed. */
class TestResult(val ok: Boolean?, val error: SyncError? = null)

/** Runs the connection test and holds its last result. */
class ConnectionTest(private val scope: CoroutineScope) {
    var result by mutableStateOf<TestResult?>(null)
        private set

    fun run(url: String, token: String) {
        result = TestResult(null)
        scope.launch { result = test(url.trim(), token.trim()) }
    }

    fun clear() {
        result = null
    }

    private suspend fun test(url: String, token: String): TestResult = try {
        Uploader.check(url, token)
        TestResult(true)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        TestResult(false, SyncError.of(e))
    }
}

@Composable
fun rememberConnectionTest(): ConnectionTest {
    val scope = rememberCoroutineScope()
    return remember { ConnectionTest(scope) }
}
