package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
        val container = (applicationContext as BridgeApplication).container
        val settings = container.settings
        return SyncRunner.run(settings, container.healthSync(), SyncScheduler.backfillDays(inputData)) {
            container.localNetworkBlocked(settings.serverUrl)
        }
    }
}
