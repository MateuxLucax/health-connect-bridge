package io.github.mateuxlucax.healthconnectbridge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.ui.AppActions
import io.github.mateuxlucax.healthconnectbridge.ui.format.message
import io.github.mateuxlucax.healthconnectbridge.ui.format.rememberStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TestResultLine(result: TestResult?, onOpenSettings: () -> Unit) {
    if (result == null) return
    if (result.error?.kind == SyncError.Kind.LOCAL_NETWORK_PERMISSION) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine(false, stringResource(R.string.local_network_denied))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_app_settings))
            }
        }
        return
    }
    val text = when (result.ok) {
        null -> stringResource(R.string.test_running)
        true -> stringResource(R.string.test_ok)
        false -> result.error?.message(rememberStrings()).orEmpty()
    }
    StatusLine(result.ok, text, busy = result.ok == null)
}

@Composable
fun ServerFields(url: String, onUrlChange: (String) -> Unit, token: String, onTokenChange: (String) -> Unit) {
    var showToken by rememberSaveable { mutableStateOf(false) }
    val invalid = url.isNotBlank() && !ServerUrl.isValid(url)
    val problem = when {
        invalid -> stringResource(R.string.error_invalid_url)
        ServerUrl.isCleartextToPublicHost(url) -> stringResource(R.string.url_cleartext_warning)
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.server_url)) },
            placeholder = { Text("http://192.168.0.10:8080") },
            isError = problem != null,
            supportingText = if (problem != null) {
                { Text(problem) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text(stringResource(R.string.token)) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        painterResource(if (showToken) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = stringResource(if (showToken) R.string.hide_token else R.string.show_token),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * For the connection test and saving: true when the server at the URL is reachable, i.e. [isBlocked] says it
 * isn't blocked (not local, or access granted), or the user grants local network access now. Runs in the
 * caller's composition scope, the only place that may hold [actions] (bound to the activity).
 */
@Composable
fun rememberLocalNetworkAccess(isBlocked: suspend (String) -> Boolean, actions: AppActions): suspend (String) -> Boolean =
    remember<suspend (String) -> Boolean>(isBlocked, actions) {
        { url -> !isBlocked(url) || actions.requestLocalNetwork() }
    }

/**
 * Starts "Test connection" of [form] with the current field values ([url], [token] read them). Local network
 * access is settled here first, asking for the permission if needed, and [form] gets only the answer, so no
 * ViewModel coroutine holds the activity. A test whose fields change meanwhile is dropped.
 */
fun CoroutineScope.launchConnectionTest(
    form: SetupForm,
    url: () -> String,
    token: () -> String,
    localNetworkAccess: suspend (String) -> Boolean,
) {
    val testedUrl = url()
    val testedToken = token()
    form.markTestRunning()
    launch {
        var started = false
        try {
            val allowed = localNetworkAccess(testedUrl.trim())
            if (url() == testedUrl && token() == testedToken) {
                form.testConnection(testedUrl, testedToken, allowed)
                started = true
            }
        } finally {
            // Edited, or the screen went away (e.g. rotated) during the prompt: don't leave "running" behind.
            if (!started) form.clearTest()
        }
    }
}

/**
 * On Android 17+, explains that the local server at [url] needs the local network permission, until granted.
 * [isBlocked] is the check of the screen's [SetupForm].
 */
@Composable
fun LocalNetworkNotice(url: String, status: DeviceStatus?, isBlocked: suspend (String) -> Boolean, actions: AppActions) {
    val scope = rememberCoroutineScope()
    // The URL of the last answer; only a changed URL waits for a pause in typing.
    val checkedUrl = remember { mutableStateOf<String?>(null) }
    // Re-checked whenever the status reloads (e.g. after the permission request or returning from settings) and
    // when the URL changes; the last answer stays shown meanwhile.
    val blocked by produceState(false, url, status) {
        if (!ServerUrl.isValid(url)) {
            value = false
            return@produceState
        }
        if (url != checkedUrl.value) delay(500)
        value = isBlocked(url)
        checkedUrl.value = url
    }
    if (!blocked) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusLine(null, stringResource(R.string.local_network_needed))
        OutlinedButton(onClick = { scope.launch { actions.requestLocalNetwork() } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.local_network_allow))
        }
    }
}

/** Local network access, shown on Android 17+ when the saved server is local. */
@Composable
fun LocalNetworkState(status: DeviceStatus?, actions: AppActions) {
    val scope = rememberCoroutineScope()
    when (status?.localNetworkGranted) {
        null -> Unit
        true -> StatusLine(true, stringResource(R.string.local_network_granted))
        false -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusLine(null, stringResource(R.string.local_network_missing))
            Button(onClick = { scope.launch { actions.requestLocalNetwork() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.local_network_allow))
            }
        }
    }
}
