package io.github.mateuxlucax.healthconnectbridge

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.ZoneId

@Composable
fun HomeScreen(settings: Settings, refreshKey: Int, onSyncNow: () -> Unit, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(refreshKey) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val lastSuccess = remember(refreshKey) { settings.lastSuccessMs }
    val lastError = remember(refreshKey) { settings.lastError }
    val values = remember(refreshKey) { LastValues(settings.prefs).all() }
    val zone = ZoneId.systemDefault()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SyncCard(lastSuccess, lastError, now, zone, onSyncNow)
        ExportedValues(values, now, zone)
    }
}

@Composable
private fun SyncCard(lastSuccess: Long, lastError: String?, now: Long, zone: ZoneId, onSyncNow: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(
                    "Última sincronização",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lastSuccess == 0L) {
                    Text("Nunca", style = MaterialTheme.typography.headlineSmall)
                } else {
                    Text(
                        Format.relative(lastSuccess, now).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        Format.dateTime(lastSuccess, zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                lastError != null -> ErrorBox(lastError)
                lastSuccess > 0 -> StatusLine(true, "Tudo certo: os dados chegaram ao servidor.")
                else -> StatusLine(null, "Os dados são enviados automaticamente a cada 15 minutos.")
            }
            Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Sincronizar agora")
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
            Icon(painterResource(R.drawable.ic_error), contentDescription = "Erro")
            Spacer(Modifier.width(12.dp))
            Column {
                Text("A última tentativa falhou", style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ExportedValues(values: List<LastValue>, now: Long, zone: ZoneId) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Dados exportados", style = MaterialTheme.typography.titleLarge)
        if (values.isEmpty()) {
            EmptyState()
            return@Column
        }
        Category.entries.forEach { category ->
            val rows = DATA_TYPES.filter { it.category == category }.flatMap { type ->
                type.metrics.flatMap { metric ->
                    values.filter { it.metric == metric }.map { displayName(type, it) to (type to it) }.sortedBy { it.first }
                }
            }
            if (rows.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(category.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(Modifier.fillMaxWidth()) {
                    rows.forEachIndexed { i, (name, typeAndValue) ->
                        val (type, value) = typeAndValue
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                        IconRow(
                            type.icon,
                            name,
                            Format.time(value.timestampMs, now, zone),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            trailing = Format.value(value.metric, value.value),
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
            Text("Nada exportado ainda", style = MaterialTheme.typography.titleMedium)
            Text(
                "Os últimos valores enviados ao servidor aparecem aqui depois da primeira sincronização.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
