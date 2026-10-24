package io.github.mateuxlucax.healthconnectbridge.ui.onboarding

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.data.local.Settings
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.sync.SyncScheduler
import io.github.mateuxlucax.healthconnectbridge.ui.AppActions
import io.github.mateuxlucax.healthconnectbridge.ui.components.BatteryState
import io.github.mateuxlucax.healthconnectbridge.ui.components.BodyText
import io.github.mateuxlucax.healthconnectbridge.ui.components.Category
import io.github.mateuxlucax.healthconnectbridge.ui.components.DATA_TYPES
import io.github.mateuxlucax.healthconnectbridge.ui.components.HistoryImport
import io.github.mateuxlucax.healthconnectbridge.ui.components.IconRow
import io.github.mateuxlucax.healthconnectbridge.ui.components.LocalNetworkNotice
import io.github.mateuxlucax.healthconnectbridge.ui.components.Logo
import io.github.mateuxlucax.healthconnectbridge.ui.components.PermissionState
import io.github.mateuxlucax.healthconnectbridge.ui.components.ServerFields
import io.github.mateuxlucax.healthconnectbridge.ui.components.TestResultLine
import io.github.mateuxlucax.healthconnectbridge.ui.components.rememberConnectionTest
import io.github.mateuxlucax.healthconnectbridge.ui.components.rememberLocalNetworkAccess
import kotlinx.coroutines.launch

private const val STEPS = 5

/** First-launch setup: welcome, server, permissions, battery, history. */
@Composable
fun OnboardingScreen(
    settings: Settings,
    scheduler: SyncScheduler,
    status: DeviceStatus?,
    statusFailed: Boolean,
    actions: AppActions,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var url by rememberSaveable { mutableStateOf(settings.serverUrl) }
    var token by rememberSaveable { mutableStateOf(settings.token) }
    BackHandler(enabled = step > 0) { step-- }
    val scope = rememberCoroutineScope()
    val localNetworkAccess = rememberLocalNetworkAccess(actions)

    val canContinue = when (step) {
        1 -> ServerUrl.isValid(url) && token.isNotBlank()
        2 -> (status?.grantedReads ?: 0) > 0
        else -> true
    }
    val nextLabel = when (step) {
        3 -> if (status?.batteryExempt == true) R.string.next else R.string.skip
        STEPS - 1 -> R.string.finish
        else -> R.string.next
    }

    Scaffold(
        bottomBar = {
            BottomButtons(
                showBack = step > 0,
                nextLabel = stringResource(nextLabel),
                nextEnabled = canContinue,
                onBack = { step-- },
                onNext = {
                    if (step == 1) {
                        settings.serverUrl = url.trim()
                        settings.token = token.trim()
                        // Asks for local network access now, while it's clear why; a denial is shown later.
                        scope.launch {
                            localNetworkAccess(url.trim())
                            step = 2
                        }
                    } else if (step == STEPS - 1) {
                        onFinish()
                    } else {
                        step++
                    }
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
                    1 -> ServerStep(url, { url = it }, token, { token = it }, status, actions, localNetworkAccess)
                    2 -> PermissionsStep(status, statusFailed, actions.requestHealthPermissions)
                    3 -> BatteryStep(status, actions.requestBatteryExemption)
                    else -> HistoryStep { scheduler.backfill(it) }
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
            stringResource(R.string.step_of, step + 1, STEPS),
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
            if (showBack) TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
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
    StepTitle(stringResource(R.string.welcome_title))
    BodyText(stringResource(R.string.welcome_text))
    DataFlow(Modifier.padding(vertical = 8.dp))
    BodyText(stringResource(R.string.welcome_server_text))
    BodyText(stringResource(R.string.welcome_privacy_text))
}

@Composable
private fun ServerStep(
    url: String,
    onUrlChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
    status: DeviceStatus?,
    actions: AppActions,
    localNetworkAccess: suspend (String) -> Boolean,
) {
    val test = rememberConnectionTest(localNetworkAccess)
    StepTitle(stringResource(R.string.your_server))
    BodyText(stringResource(R.string.server_text))
    ServerFields(url, { onUrlChange(it); test.clear() }, token, { onTokenChange(it); test.clear() })
    LocalNetworkNotice(url, status, actions)
    OutlinedButton(
        onClick = { test.run(url, token) },
        enabled = ServerUrl.isValid(url) && token.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.test_connection)) }
    TestResultLine(test.result, actions.openAppSettings)
}

@Composable
private fun PermissionsStep(status: DeviceStatus?, statusFailed: Boolean, onRequest: () -> Unit) {
    StepTitle(stringResource(R.string.permissions_title))
    BodyText(stringResource(R.string.permissions_text))
    PermissionState(status, statusFailed, onRequest)
    Text(stringResource(R.string.what_app_reads), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    Category.entries.forEach { category ->
        Column {
            Text(
                stringResource(category.title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            DATA_TYPES.filter { it.category == category }.forEach {
                IconRow(it.icon, stringResource(it.name), stringResource(it.description), Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun BatteryStep(status: DeviceStatus?, onRequest: () -> Unit) {
    StepTitle(stringResource(R.string.battery_title))
    BodyText(stringResource(R.string.battery_text))
    BodyText(stringResource(R.string.battery_text_2))
    BatteryState(status, onRequest)
    BodyText(stringResource(R.string.battery_skip_text))
}

@Composable
private fun HistoryStep(onImport: (Int) -> Unit) {
    StepTitle(stringResource(R.string.section_history))
    BodyText(stringResource(R.string.history_text))
    BodyText(stringResource(R.string.history_text_2))
    HistoryImport(last = null, onImport)
    BodyText(stringResource(R.string.history_text_3))
}
