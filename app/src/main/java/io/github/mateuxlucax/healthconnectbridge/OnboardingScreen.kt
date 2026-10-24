package io.github.mateuxlucax.healthconnectbridge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val STEPS = 5

/** First-launch setup: welcome, server, permissions, battery, history. */
@Composable
fun OnboardingScreen(settings: Settings, status: DeviceStatus?, actions: AppActions, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var url by rememberSaveable { mutableStateOf(settings.serverUrl) }
    var token by rememberSaveable { mutableStateOf(settings.token) }
    BackHandler(enabled = step > 0) { step-- }

    val canContinue = when (step) {
        1 -> url.isNotBlank() && token.isNotBlank()
        2 -> (status?.grantedReads ?: 0) > 0
        else -> true
    }
    val nextLabel = when (step) {
        3 -> if (status?.batteryExempt == true) "Próximo" else "Pular"
        STEPS - 1 -> "Concluir"
        else -> "Próximo"
    }

    Scaffold(
        bottomBar = {
            BottomButtons(
                showBack = step > 0,
                nextLabel = nextLabel,
                nextEnabled = canContinue,
                onBack = { step-- },
                onNext = {
                    if (step == 1) {
                        settings.serverUrl = url.trim()
                        settings.token = token.trim()
                    }
                    if (step == STEPS - 1) onFinish() else step++
                },
            )
        },
    ) { padding ->
        key(step) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(step)
                when (step) {
                    0 -> WelcomeStep()
                    1 -> ServerStep(url, { url = it }, token, { token = it })
                    2 -> PermissionsStep(status, actions.requestHealthPermissions)
                    3 -> BatteryStep(status, actions.requestBatteryExemption)
                    else -> HistoryStep { SyncWorker.backfill(context, it) }
                }
            }
        }
    }
}

@Composable
private fun Header(step: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Logo(72.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(STEPS) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Passo ${step + 1} de $STEPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BottomButtons(showBack: Boolean, nextLabel: String, nextEnabled: Boolean, onBack: () -> Unit, onNext: () -> Unit) {
    Surface {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) TextButton(onClick = onBack) { Text("Voltar") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNext, enabled = nextEnabled) { Text(nextLabel) }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall)
}

@Composable
private fun WelcomeStep() {
    StepTitle("Bem-vindo")
    BodyText(
        "Este app lê seus dados de saúde do Health Connect (frequência cardíaca, passos, sono, peso e mais) " +
            "e envia para o seu próprio servidor, a cada 15 minutos."
    )
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Health Connect", "↓", "Seu servidor", "↓", "Prometheus e Grafana").forEach {
                Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
    }
    BodyText(
        "O servidor (health-connect-prometheus-exporter) grava os dados no Prometheus, " +
            "e você acompanha tudo em gráficos no Grafana."
    )
    BodyText("Seus dados vão somente para o servidor que você configurar. Nada é enviado a terceiros.")
}

@Composable
private fun ServerStep(url: String, onUrlChange: (String) -> Unit, token: String, onTokenChange: (String) -> Unit) {
    val test = rememberConnectionTest()
    StepTitle("Seu servidor")
    BodyText(
        "Informe o endereço do health-connect-prometheus-exporter e o token configurado nele. " +
            "Na rede de casa, algo como http://192.168.0.10:8080."
    )
    ServerFields(url, { onUrlChange(it); test.clear() }, token, { onTokenChange(it); test.clear() })
    OutlinedButton(
        onClick = { test.run(url, token) },
        enabled = url.isNotBlank() && token.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Testar conexão") }
    TestResultLine(test.result)
}

@Composable
private fun PermissionsStep(status: DeviceStatus?, onRequest: () -> Unit) {
    StepTitle("Permissões")
    BodyText(
        "O app só lê do Health Connect, nunca escreve. Conceda acesso aos dados que quiser exportar " +
            "e permita também o acesso em segundo plano e ao histórico, para a sincronização automática."
    )
    PermissionState(status, onRequest)
    Text("O que o app lê", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    Category.entries.forEach { category ->
        Column {
            Text(
                category.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            DATA_TYPES.filter { it.category == category }.forEach {
                IconRow(it.icon, it.name, it.description, Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun BatteryStep(status: DeviceStatus?, onRequest: () -> Unit) {
    StepTitle("Bateria")
    BodyText(
        "No Galaxy (One UI) e em outros aparelhos, a otimização de bateria coloca apps em suspensão " +
            "e pode cancelar a sincronização em segundo plano."
    )
    BodyText(
        "Desative a otimização só para este app para os dados chegarem ao servidor mesmo com a tela desligada. " +
            "O consumo é pequeno: cada sincronização leva poucos segundos."
    )
    BatteryState(status, onRequest)
    BodyText("Se preferir, pule esta etapa e faça isso depois em Configurações.")
}

@Composable
private fun HistoryStep(onImport: (Int) -> Unit) {
    StepTitle("Importar histórico")
    BodyText(
        "A sincronização automática envia os últimos 7 dias e tudo o que chegar depois. Para enviar dados " +
            "mais antigos, importe o histórico: o app lê de novo os últimos dias do Health Connect e envia ao servidor."
    )
    BodyText(
        "O Prometheus só aceita dados dentro da janela de dados fora de ordem, que é de 30 dias na configuração " +
            "padrão do exporter. Dados mais antigos que isso são recusados."
    )
    HistoryImport(onImport)
    BodyText("Você também pode importar depois, em Configurações. Toque em Concluir para começar a usar o app.")
}
