package io.github.mateuxlucax.healthconnectbridge.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.model.LastValue
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.ui.AppActions
import io.github.mateuxlucax.healthconnectbridge.ui.components.BackgroundWarning
import io.github.mateuxlucax.healthconnectbridge.ui.components.Category
import io.github.mateuxlucax.healthconnectbridge.ui.components.DATA_TYPES
import io.github.mateuxlucax.healthconnectbridge.ui.components.IconRow
import io.github.mateuxlucax.healthconnectbridge.ui.components.SkippedNote
import io.github.mateuxlucax.healthconnectbridge.ui.components.StatusLine
import io.github.mateuxlucax.healthconnectbridge.ui.format.Format
import io.github.mateuxlucax.healthconnectbridge.ui.format.message
import io.github.mateuxlucax.healthconnectbridge.ui.format.rememberFormat
import io.github.mateuxlucax.healthconnectbridge.ui.format.rememberStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    status: DeviceStatus?,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onSyncNow = {
        viewModel.syncNow()
        Toast.makeText(context, R.string.sync_scheduled, Toast.LENGTH_SHORT).show()
    }
    // Relative times ("5 min ago") are refreshed on resume and every 30 seconds while shown.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(30_000)
            }
        }
    }
    val zone = ZoneId.systemDefault()
    val format = rememberFormat()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SyncCard(
            state.lastSuccessMs, state.lastError, state.skipped, status?.backgroundMissing == true, now, zone, format, actions, onSyncNow,
        )
        ExportedValues(state.values, now, zone, format)
    }
}

@Composable
private fun SyncCard(
    lastSuccess: Long,
    lastError: SyncError?,
    skipped: Int,
    backgroundMissing: Boolean,
    now: Long,
    zone: ZoneId,
    format: Format,
    actions: AppActions,
    onSyncNow: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(
                    stringResource(R.string.last_sync),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lastSuccess == 0L) {
                    Text(stringResource(R.string.never), style = MaterialTheme.typography.headlineSmall)
                } else {
                    Text(
                        format.relative(lastSuccess, now),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        format.dateTime(lastSuccess, zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                lastError != null -> ErrorBox(lastError.message(rememberStrings()))
                lastSuccess > 0 -> StatusLine(true, stringResource(R.string.status_ok))
                else -> StatusLine(null, stringResource(R.string.status_auto))
            }
            if (lastError == null && skipped > 0) SkippedNote(skipped)
            if (backgroundMissing) BackgroundWarning()
            if (lastError?.kind == SyncError.Kind.LOCAL_NETWORK_PERMISSION) {
                OutlinedButton(
                    onClick = { scope.launch { if (actions.requestLocalNetwork()) onSyncNow() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.local_network_allow)) }
            }
            Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.sync_now))
            }
        }
    }
}

@Composable
private fun ErrorBox(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = stringResource(R.string.cd_error))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.error_title), style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ExportedValues(values: List<LastValue>, now: Long, zone: ZoneId, format: Format) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.exported_data), style = MaterialTheme.typography.titleLarge)
        if (values.isEmpty()) {
            EmptyState()
            return@Column
        }
        Category.entries.forEach { category ->
            val rows = DATA_TYPES.filter { it.category == category }.flatMap { type ->
                type.metrics.flatMap { metric ->
                    values.filter { it.metric == metric }.map { format.name(type, it) to (type to it) }.sortedBy { it.first }
                }
            }
            if (rows.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(category.title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(Modifier.fillMaxWidth()) {
                    rows.forEachIndexed { i, (name, typeAndValue) ->
                        val (type, value) = typeAndValue
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                        IconRow(
                            type.icon,
                            name,
                            format.time(value.timestampMs, now, zone),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            trailing = format.value(value.metric, value.value),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
