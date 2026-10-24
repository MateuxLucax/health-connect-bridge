package io.github.mateuxlucax.healthconnectbridge

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient

/** Strings in the current configuration's language. */
@Composable
fun rememberStrings(): Strings {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) { ResourceStrings(resources) }
}

@Composable
fun rememberFormat(): Format {
    val strings = rememberStrings()
    return remember(strings) { Format(strings) }
}

/** The launcher icon: its foreground on the navy background. */
@Composable
fun Logo(size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFF1E2430))
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(1.4f),
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Icon, title and optional subtitle and trailing text, at least 56dp tall. */
@Composable
fun IconRow(@DrawableRes icon: Int, title: String, subtitle: String?, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** A state line: check when [ok], error icon when not, spinner when null and [busy], plain text otherwise. */
@Composable
fun StatusLine(ok: Boolean?, text: String, busy: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            ok == true -> Icon(painterResource(R.drawable.ic_check_circle), stringResource(R.string.cd_ok), tint = MaterialTheme.colorScheme.primary)
            ok == false -> Icon(painterResource(R.drawable.ic_error), stringResource(R.string.cd_error), tint = MaterialTheme.colorScheme.error)
            busy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        }
        if (ok != null || busy) Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TestResultLine(result: TestResult?) {
    if (result == null) return
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

/** Days field and "Importar" button; [onImport] receives the number of days. */
@Composable
fun HistoryImport(onImport: (Int) -> Unit) {
    var days by rememberSaveable { mutableStateOf("30") }
    var scheduled by rememberSaveable { mutableStateOf(false) }
    val parsed = days.toIntOrNull()?.takeIf { it > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = days,
                onValueChange = { days = it.filter(Char::isDigit).take(4); scheduled = false },
                label = { Text(stringResource(R.string.days)) },
                singleLine = true,
                isError = parsed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.width(120.dp),
            )
            Spacer(Modifier.width(16.dp))
            Button(onClick = { onImport(parsed!!); scheduled = true }, enabled = parsed != null) {
                Text(stringResource(R.string.import_history))
            }
        }
        if (scheduled) {
            StatusLine(true, stringResource(R.string.import_scheduled))
        }
    }
}

/** Health Connect availability and granted count, with the button to grant what is missing. */
@Composable
fun PermissionState(status: DeviceStatus?, onRequest: () -> Unit) {
    when {
        status == null -> Unit
        !status.healthConnectAvailable -> StatusLine(
            false,
            if (status.healthConnect == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                stringResource(R.string.hc_update_required)
            } else {
                stringResource(R.string.hc_unavailable)
            },
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (status.granted) {
                status.requested -> StatusLine(true, pluralStringResource(R.plurals.permissions_all, status.requested, status.requested))
                0 -> StatusLine(null, stringResource(R.string.permissions_none))
                else -> StatusLine(
                    null,
                    pluralStringResource(R.plurals.permissions_some, status.requested, status.granted, status.requested),
                )
            }
            if (status.granted < status.requested) {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.grant_permissions)) }
            }
        }
    }
}

@Composable
fun BatteryState(status: DeviceStatus?, onRequest: () -> Unit) {
    when {
        status == null -> Unit
        status.batteryExempt -> StatusLine(true, stringResource(R.string.battery_exempt))
        else -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusLine(null, stringResource(R.string.battery_optimized))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.battery_disable)) }
        }
    }
}
