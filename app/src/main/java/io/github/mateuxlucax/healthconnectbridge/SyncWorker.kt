package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Runs an incremental sync, or a backfill when [KEY_BACKFILL_DAYS] is set. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = mutex.withLock {
        val settings = Settings(applicationContext)
        runSync(settings, HealthSync(applicationContext, settings), inputData.getInt(KEY_BACKFILL_DAYS, 0))
    }

    companion object {
        private const val KEY_BACKFILL_DAYS = "backfill_days"
        private val mutex = Mutex()

        /** A backfill of [backfillDays] when positive, the incremental sync otherwise. */
        internal suspend fun runSync(settings: Settings, sync: HealthSync, backfillDays: Int): ListenableWorker.Result =
            if (backfillDays > 0) {
                runAndReport(settings, backfill = true) { sync.backfill(backfillDays) }
            } else {
                runAndReport(settings) { sync.incremental() }
            }

        /**
         * Runs [sync], records the outcome in [settings] and maps it to a WorkManager result. Only incremental
         * runs set the last success and error shown on Home; a backfill is reported as [Settings.lastBackfill].
         */
        internal suspend fun runAndReport(
            settings: Settings,
            backfill: Boolean = false,
            sync: suspend () -> Unit,
        ): ListenableWorker.Result {
            val error = try {
                sync()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncError.of(e)
            }
            val now = System.currentTimeMillis()
            if (backfill) {
                settings.lastBackfill = BackfillResult(now, error)
            } else {
                if (error == null) settings.lastSuccessMs = now
                settings.lastError = error
            }
            return when {
                error == null -> ListenableWorker.Result.success()
                error.kind.retry -> ListenableWorker.Result.retry()
                else -> ListenableWorker.Result.failure()
            }
        }

        private val networkConstraint =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "periodic-sync",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(networkConstraint)
                    .build(),
            )
        }

        /**
         * REPLACE, not KEEP: a previous manual run waiting in retry backoff would otherwise swallow the tap.
         * Replacing a running sync is safe: the checkpoint moves per uploaded page and [mutex] serializes runs.
         */
        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-now",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(networkConstraint).build(),
            )
        }

        fun backfill(context: Context, days: Int) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "backfill",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(networkConstraint)
                    .setInputData(Data.Builder().putInt(KEY_BACKFILL_DAYS, days).build())
                    .build(),
            )
        }
    }
}
