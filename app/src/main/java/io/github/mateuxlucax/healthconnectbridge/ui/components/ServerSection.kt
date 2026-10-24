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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.data.network.LocalNetwork
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.ui.AppActions
import io.github.mateuxlucax.healthconnectbridge.ui.format.message
import io.github.mateuxlucax.healthconnectbridge.ui.format.rememberStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * For [ConnectionTest] and saving: true when the server at the URL is reachable, i.e. it isn't local, local
 * network access is granted, or the user grants it now.
 */
@Composable
fun rememberLocalNetworkAccess(actions: AppActions): suspend (String) -> Boolean {
    val context = LocalContext.current.applicationContext
    return remember<suspend (String) -> Boolean>(actions) {
        { url -> !LocalNetwork.isBlocked(context, url) || actions.requestLocalNetwork() }
    }
}

/** On Android 17+, explains that the local server at [url] needs the local network permission, until granted. */
@Composable
fun LocalNetworkNotice(url: String, status: DeviceStatus?, actions: AppActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Re-read whenever the status reloads, e.g. after the permission request or returning from settings.
    val granted = remember(status) { LocalNetwork.isGranted(context) }
    // Resolved after a pause in typing; the last answer stays shown meanwhile.
    val local by produceState(false, url, granted) {
        if (granted || !ServerUrl.isValid(url)) {
            value = false
            return@produceState
        }
        delay(500)
        value = withContext(Dispatchers.IO) { LocalNetwork.isLocal(url) }
    }
    if (granted || !local) return
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
