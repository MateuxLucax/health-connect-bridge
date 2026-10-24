package io.github.mateuxlucax.healthconnectbridge.ui.settings

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.healthconnectbridge.BuildConfig
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.data.network.ServerUrl
import io.github.mateuxlucax.healthconnectbridge.model.DeviceStatus
import io.github.mateuxlucax.healthconnectbridge.ui.AppActions
import io.github.mateuxlucax.healthconnectbridge.ui.components.BatteryState
import io.github.mateuxlucax.healthconnectbridge.ui.components.BodyText
import io.github.mateuxlucax.healthconnectbridge.ui.components.HistoryImport
import io.github.mateuxlucax.healthconnectbridge.ui.components.LocalNetworkNotice
import io.github.mateuxlucax.healthconnectbridge.ui.components.LocalNetworkState
import io.github.mateuxlucax.healthconnectbridge.ui.components.PermissionState
import io.github.mateuxlucax.healthconnectbridge.ui.components.SectionCard
import io.github.mateuxlucax.healthconnectbridge.ui.components.ServerFields
import io.github.mateuxlucax.healthconnectbridge.ui.components.TestResultLine
import io.github.mateuxlucax.healthconnectbridge.ui.components.launchConnectionTest
import io.github.mateuxlucax.healthconnectbridge.ui.components.rememberLocalNetworkAccess
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    status: DeviceStatus?,
    statusFailed: Boolean,
    actions: AppActions,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val form = viewModel.form
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf(form.savedServerUrl) }
    var token by rememberSaveable { mutableStateOf(form.savedToken) }
    val scope = rememberCoroutineScope()
    // Like the form, the test result belongs to this visit of the tab: App clears it when leaving the tab.
    val localNetworkAccess = rememberLocalNetworkAccess(form::isLocalNetworkBlocked, actions)
    val filled = ServerUrl.isValid(url) && token.isNotBlank()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(stringResource(R.string.section_server)) {
            ServerFields(url, { url = it; form.clearTest() }, token, { token = it; form.clearTest() })
            LocalNetworkNotice(url, status, form::isLocalNetworkBlocked, actions)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { scope.launchConnectionTest(form, { url }, { token }, localNetworkAccess) },
                    enabled = filled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.test_connection))
                }
                Button(
                    onClick = {
                        form.save(url, token)
                        Toast.makeText(context, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                        // Saved either way; a denial stays visible in the notice above and on Home.
                        scope.launch { localNetworkAccess(url.trim()) }
                    },
                    enabled = filled,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.save)) }
            }
            TestResultLine(state.testResult, actions.openAppSettings)
        }

        SectionCard(stringResource(R.string.section_history)) {
            BodyText(stringResource(R.string.history_settings_text))
            HistoryImport(state.lastBackfill) { form.importHistory(it) }
        }

        SectionCard(stringResource(R.string.section_permissions)) {
            PermissionState(status, statusFailed, actions.requestHealthPermissions)
            BatteryState(status, actions.requestBatteryExemption)
            LocalNetworkState(status, actions)
        }

        Text(
            stringResource(R.string.version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }
}
