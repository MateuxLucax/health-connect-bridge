package io.github.mateuxlucax.healthconnectbridge.model

/** Outcome of the last history import: when it finished, and its error if it failed. */
data class BackfillResult(val finishedMs: Long, val error: SyncError?)
