package io.github.mateuxlucax.healthconnectbridge.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Enqueues the sync work: the periodic job, "sync now" and history imports. [worker] is the app's
 * SyncWorker, passed in because its class name is persisted by WorkManager and stays in the root package.
 */
class SyncScheduler(context: Context, private val worker: Class<out ListenableWorker>) {
    private val context = context.applicationContext

    fun schedulePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequest.Builder(worker, 15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build(),
        )
    }

    /**
     * REPLACE, not KEEP: a previous manual run waiting in retry backoff would otherwise swallow the tap.
     * Replacing a running sync is safe: the checkpoint moves per uploaded page and [SyncRunner] serializes runs.
     */
    fun syncNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(worker).setConstraints(networkConstraint).build(),
        )
    }

    fun backfill(days: Int) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            BACKFILL,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.Builder(worker)
                .setConstraints(networkConstraint)
                .setInputData(Data.Builder().putInt(KEY_BACKFILL_DAYS, days).build())
                .build(),
        )
    }

    companion object {
        // Unique work names and the input key are stored by WorkManager on users' phones: never change them.
        const val PERIODIC = "periodic-sync"
        const val SYNC_NOW = "sync-now"
        const val BACKFILL = "backfill"
        private const val KEY_BACKFILL_DAYS = "backfill_days"

        private val networkConstraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Days to backfill from a work request's input, 0 for an incremental sync. */
        fun backfillDays(input: Data): Int = input.getInt(KEY_BACKFILL_DAYS, 0)
    }
}
