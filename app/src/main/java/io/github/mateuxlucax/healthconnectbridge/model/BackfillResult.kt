package io.github.mateuxlucax.healthconnectbridge.model

/**
 * Outcome of the last history import: when it finished, its error if it failed, and how many stretches of
 * records Health Connect couldn't read were [skipped] (the import still succeeds).
 */
data class BackfillResult(val finishedMs: Long, val error: SyncError?, val skipped: Int = 0)
