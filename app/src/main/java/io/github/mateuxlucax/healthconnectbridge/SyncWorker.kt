package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.mateuxlucax.healthconnectbridge.data.exporter.ExporterClient
import io.github.mateuxlucax.healthconnectbridge.data.local.LastValues
import io.github.mateuxlucax.healthconnectbridge.data.local.Settings
import io.github.mateuxlucax.healthconnectbridge.data.network.LocalNetwork
import io.github.mateuxlucax.healthconnectbridge.sync.HealthSync
import io.github.mateuxlucax.healthconnectbridge.sync.SyncRunner
import io.github.mateuxlucax.healthconnectbridge.sync.SyncScheduler

/**
 * Runs an incremental sync, or a backfill when the work request asks for one (see [SyncScheduler]).
 *
 * Do not move or rename this class: WorkManager stores its fully qualified name with every enqueued job
 * (the periodic job is kept indefinitely) and creates it by reflection, so already scheduled syncs on
 * users' phones would fail. All logic lives in the sync package.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        val sync = HealthSync(applicationContext, settings, LastValues(applicationContext), ExporterClient())
        return SyncRunner.run(settings, sync, SyncScheduler.backfillDays(inputData)) {
            LocalNetwork.isBlocked(applicationContext, settings.serverUrl)
        }
    }
}
