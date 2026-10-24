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
        runAndReport(settings) {
            val sync = HealthSync(applicationContext, settings)
            val backfillDays = inputData.getInt(KEY_BACKFILL_DAYS, 0)
            if (backfillDays > 0) sync.backfill(backfillDays) else sync.incremental()
        }
    }

    companion object {
        private const val KEY_BACKFILL_DAYS = "backfill_days"
        private val mutex = Mutex()

        /** Runs [sync], records the outcome in [settings] and maps it to a WorkManager result. */
        internal suspend fun runAndReport(settings: Settings, sync: suspend () -> Unit): ListenableWorker.Result =
            try {
                sync()
                settings.lastSuccessMs = System.currentTimeMillis()
                settings.lastError = null
                ListenableWorker.Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = SyncError.of(e)
                settings.lastError = error
                if (error.kind.retry) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
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

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-now",
                ExistingWorkPolicy.KEEP,
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
