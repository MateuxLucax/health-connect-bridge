package io.github.mateuxlucax.healthconnectbridge.model

/** What the syncs last recorded: the last success and error of the incremental sync, and the last history import. */
data class SyncState(val lastSuccessMs: Long, val lastError: SyncError?, val lastBackfill: BackfillResult?)
