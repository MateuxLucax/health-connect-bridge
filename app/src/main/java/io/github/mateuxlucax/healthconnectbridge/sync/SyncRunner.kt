package io.github.mateuxlucax.healthconnectbridge.sync

import androidx.work.ListenableWorker
import io.github.mateuxlucax.healthconnectbridge.data.local.SettingsStore
import io.github.mateuxlucax.healthconnectbridge.model.BackfillResult
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Runs one sync for the worker: one run at a time, outcome recorded in [SettingsStore], mapped to a WorkManager result. */
object SyncRunner {
    private val mutex = Mutex()

    /** A backfill of [backfillDays] when positive, the incremental sync otherwise; runs never overlap. */
    suspend fun run(
        settings: SettingsStore,
        sync: HealthSync,
        backfillDays: Int,
        localNetworkBlocked: suspend () -> Boolean = { false },
    ): ListenableWorker.Result = mutex.withLock { runSync(settings, sync, backfillDays, localNetworkBlocked) }

    /** [run] without the lock. */
    internal suspend fun runSync(
        settings: SettingsStore,
        sync: HealthSync,
        backfillDays: Int,
        localNetworkBlocked: suspend () -> Boolean = { false },
    ): ListenableWorker.Result =
        if (backfillDays > 0) {
            runAndReport(settings, backfill = true, localNetworkBlocked = localNetworkBlocked) {
                sync.backfill(backfillDays)
            }
        } else {
            runAndReport(settings, localNetworkBlocked = localNetworkBlocked) { sync.incremental() }
        }

    /**
     * Runs [sync], records the outcome in [settings] and maps it to a WorkManager result. Only incremental
     * runs set the last success and error shown on Home; a backfill is reported as [SettingsStore.lastBackfill].
     * [localNetworkBlocked] turns a connect failure into [SyncError.Kind.LOCAL_NETWORK_PERMISSION].
     */
    internal suspend fun runAndReport(
        settings: SettingsStore,
        backfill: Boolean = false,
        localNetworkBlocked: suspend () -> Boolean = { false },
        sync: suspend () -> Unit,
    ): ListenableWorker.Result {
        val error = try {
            sync()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncErrors.of(e, localNetworkBlocked)
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
}
