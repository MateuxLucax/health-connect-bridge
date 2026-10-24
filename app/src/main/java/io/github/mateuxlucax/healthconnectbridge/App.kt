package io.github.mateuxlucax.healthconnectbridge

import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Onboarding on first launch, then the two tabs. */
@Composable
fun App(settings: Settings, refreshKey: Int, actions: AppActions) {
    val context = LocalContext.current
    val status by produceState<DeviceStatus?>(null, refreshKey) {
        value = runCatching { DeviceStatus.load(context) }.getOrNull()
    }
    var onboardingDone by remember { mutableStateOf(settings.onboardingDone) }

    if (!onboardingDone) {
        OnboardingScreen(settings, status, actions) {
            settings.onboardingDone = true
            SyncWorker.schedulePeriodic(context)
            onboardingDone = true
        }
    } else {
        MainScreen(settings, status, refreshKey, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(settings: Settings, status: DeviceStatus?, refreshKey: Int, actions: AppActions) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Logo(32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(painterResource(R.drawable.ic_home), contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) },
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
        if (tab == 0) {
            HomeScreen(settings, refreshKey, onSyncNow = {
                SyncWorker.syncNow(context)
                Toast.makeText(context, R.string.sync_started, Toast.LENGTH_SHORT).show()
            }, modifier)
        } else {
            SettingsScreen(settings, status, actions, modifier)
        }
    }
}
