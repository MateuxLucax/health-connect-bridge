package io.github.mateuxlucax.healthconnectbridge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.model.BackfillResult
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.ui.format.message
import io.github.mateuxlucax.healthconnectbridge.ui.format.rememberFormat
import io.github.mateuxlucax.healthconnectbridge.ui.format.rememberStrings

/** Days field and "Import" button, and the outcome of the [last] import; [onImport] receives the number of days. */
@Composable
fun HistoryImport(last: BackfillResult?, onImport: (Int) -> Unit) {
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
        } else if (last != null) {
            val strings = rememberStrings()
            val error = last.error
            if (error == null) {
                val time = rememberFormat().dateTime(last.finishedMs, java.time.ZoneId.systemDefault())
                StatusLine(true, stringResource(R.string.backfill_done, time))
            } else {
                StatusLine(false, stringResource(R.string.backfill_failed, error.message(strings)))
            }
        }
    }
}

/** Health Connect availability and granted count, with the button to grant what is missing. */
@Composable
fun PermissionState(status: DeviceStatus?, failed: Boolean, onRequest: () -> Unit) {
    if (failed) StatusLine(false, stringResource(R.string.status_load_failed))
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
            if (status.backgroundMissing) BackgroundWarning()
            if (status.granted < status.requested) {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.grant_permissions)) }
            }
        }
    }
}

/** Shown when background reads are supported but not granted. */
@Composable
fun BackgroundWarning() {
    StatusLine(false, stringResource(R.string.background_missing))
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
