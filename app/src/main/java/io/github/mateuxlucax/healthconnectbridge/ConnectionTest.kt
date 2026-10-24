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
import java.io.IOException
import java.net.MalformedURLException

/** Outcome of "Testar conexão"; [ok] is null while the request runs. */
class TestResult(val ok: Boolean?, val message: String)

/** Runs the connection test and holds its last result. */
class ConnectionTest(private val scope: CoroutineScope) {
    var result by mutableStateOf<TestResult?>(null)
        private set

    fun run(url: String, token: String) {
        result = TestResult(null, "Testando conexão…")
        scope.launch { result = test(url.trim(), token.trim()) }
    }

    fun clear() {
        result = null
    }

    private suspend fun test(url: String, token: String): TestResult = try {
        Uploader.check(url, token)
        TestResult(true, "Conexão OK: o servidor aceitou o token.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpStatusException) {
        TestResult(false, if (e.code == 401) "Token rejeitado pelo servidor (401)." else "O servidor respondeu ${e.message}")
    } catch (e: MalformedURLException) {
        TestResult(false, "URL inválida. Exemplo: http://192.168.0.10:8080")
    } catch (e: IOException) {
        TestResult(false, "Não foi possível conectar: ${e.message}")
    } catch (e: Exception) {
        TestResult(false, "Erro: ${e.message}")
    }
}

@Composable
fun rememberConnectionTest(): ConnectionTest {
    val scope = rememberCoroutineScope()
    return remember { ConnectionTest(scope) }
}
