package io.github.mateuxlucax.healthconnectbridge.model

/**
 * What the syncs last recorded: the last success and error of the incremental sync, the last history import, and
 * how many unreadable stretches of records the last successful incremental sync skipped.
 */
data class SyncState(
    val lastSuccessMs: Long,
    val lastError: SyncError?,
    val lastBackfill: BackfillResult?,
    val lastSkipped: Int = 0,
)
