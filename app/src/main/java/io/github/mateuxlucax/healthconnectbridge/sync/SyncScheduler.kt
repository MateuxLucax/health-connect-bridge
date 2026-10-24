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
 * [intervalMinutes] reads the chosen automatic sync interval.
 */
class SyncScheduler(
    context: Context,
    private val worker: Class<out ListenableWorker>,
    private val intervalMinutes: () -> Int,
) {
    private val context = context.applicationContext

    /** On app start and at the end of onboarding: keeps an already scheduled job and its timer. */
    fun schedulePeriodic() = enqueuePeriodic(intervalChanged = false)

    /** After the user picks another interval: the existing job gets the new period. */
    fun reschedulePeriodic() = enqueuePeriodic(intervalChanged = true)

    private fun enqueuePeriodic(intervalChanged: Boolean) {
        val plan = periodicPlan(intervalMinutes(), intervalChanged)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            plan.policy,
            PeriodicWorkRequest.Builder(worker, plan.minutes, TimeUnit.MINUTES)
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

    /** How the periodic job is enqueued: its period and what happens to an existing job. */
    data class PeriodicPlan(val minutes: Long, val policy: ExistingPeriodicWorkPolicy)

    companion object {
        // Unique work names and the input key are stored by WorkManager on users' phones: never change them.
        const val PERIODIC = "periodic-sync"
        const val SYNC_NOW = "sync-now"
        const val BACKFILL = "backfill"
        private const val KEY_BACKFILL_DAYS = "backfill_days"

        private val networkConstraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** WorkManager's shortest period for periodic work. */
        private const val MIN_PERIOD_MINUTES = 15L

        /**
         * KEEP on start, so a launch doesn't restart the timer. UPDATE when the interval changed: the unique work
         * stays (same id, last run kept) and the next run follows the new period.
         */
        fun periodicPlan(intervalMinutes: Int, intervalChanged: Boolean) = PeriodicPlan(
            intervalMinutes.toLong().coerceAtLeast(MIN_PERIOD_MINUTES),
            if (intervalChanged) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
        )

        /** Days to backfill from a work request's input, 0 for an incremental sync. */
        fun backfillDays(input: Data): Int = input.getInt(KEY_BACKFILL_DAYS, 0)
    }
}
