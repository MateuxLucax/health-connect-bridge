package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.mateuxlucax.healthconnectbridge.data.DeviceStatusRepository
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.healthconnect.HealthConnectAccess
import io.github.mateuxlucax.healthconnectbridge.data.local.LastValues
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.data.network.LocalNetwork
import io.github.mateuxlucax.healthconnectbridge.sync.HealthSync
import io.github.mateuxlucax.healthconnectbridge.sync.SyncScheduler
import io.github.mateuxlucax.healthconnectbridge.ui.DeviceStatusViewModel
import io.github.mateuxlucax.healthconnectbridge.ui.home.HomeViewModel
import io.github.mateuxlucax.healthconnectbridge.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.healthconnectbridge.ui.settings.SettingsViewModel

/**
 * Manual dependency injection: builds the app's long-lived objects once (see docs/adr/0013-code-structure.md).
 * Created by [BridgeApplication].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** The "settings" SharedPreferences file, shared by both stores. */
    private val prefs = SettingsStore.open(appContext)

    val settings = SettingsStore(prefs)
    val lastValues = LastValues(prefs)
    val exporterClient = ExporterClient()
    val syncScheduler = SyncScheduler(appContext, SyncWorker::class.java)
    val deviceStatusRepository = DeviceStatusRepository(appContext, settings)

    /**
     * Whether Android 17 local network protection would block connecting to a URL; used by the sync, [SyncWorker]
     * and the screens' ViewModels. Bound to the application context, so ViewModels can hold it.
     */
    val localNetworkBlocked: suspend (String) -> Boolean = { url -> LocalNetwork.isBlocked(appContext, url) }

    /** A sync engine reading the real Health Connect; used by [SyncWorker]. */
    fun healthSync() = HealthSync(settings, lastValues, exporterClient, localNetworkBlocked) {
        HealthConnectAccess.client(appContext)
    }

    /** Creates the screens' ViewModels with their dependencies. */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            OnboardingViewModel(
                settings, exporterClient, syncScheduler::backfill, syncScheduler::schedulePeriodic, localNetworkBlocked,
            )
        }
        initializer { HomeViewModel(settings, lastValues, syncScheduler::syncNow) }
        initializer { SettingsViewModel(settings, exporterClient, syncScheduler::backfill, localNetworkBlocked) }
        initializer { DeviceStatusViewModel(deviceStatusRepository::load) }
    }
}
