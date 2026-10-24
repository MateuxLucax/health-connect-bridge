package io.github.mateuxlucax.healthconnectbridge

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(settings: Settings, status: DeviceStatus?, actions: AppActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf(settings.serverUrl) }
    var token by rememberSaveable { mutableStateOf(settings.token) }
    val test = rememberConnectionTest()
    val filled = url.isNotBlank() && token.isNotBlank()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard("Servidor") {
            ServerFields(url, { url = it; test.clear() }, token, { token = it; test.clear() })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { test.run(url, token) }, enabled = filled, modifier = Modifier.weight(1f)) {
                    Text("Testar conexão")
                }
                Button(
                    onClick = {
                        settings.serverUrl = url.trim()
                        settings.token = token.trim()
                        Toast.makeText(context, "Configurações salvas", Toast.LENGTH_SHORT).show()
                    },
                    enabled = filled,
                    modifier = Modifier.weight(1f),
                ) { Text("Salvar") }
            }
            TestResultLine(test.result)
        }

        SectionCard("Importar histórico") {
            BodyText(
                "Lê de novo os últimos dias do Health Connect e envia ao servidor. O Prometheus só aceita " +
                    "dados dentro da janela de dados fora de ordem (30 dias na configuração padrão do exporter)."
            )
            HistoryImport { SyncWorker.backfill(context, it) }
        }

        SectionCard("Permissões e bateria") {
            PermissionState(status, actions.requestHealthPermissions)
            BatteryState(status, actions.requestBatteryExemption)
        }

        Text(
            "Versão ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}
