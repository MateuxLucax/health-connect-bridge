package io.github.mateuxlucax.healthconnectbridge.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits once when collected, then whenever one of [keys] changes (or the file is cleared). Listens only
 * while collected.
 */
internal fun SharedPreferences.changes(keys: Set<String>): Flow<Unit> = callbackFlow {
    // SharedPreferences keeps listeners weakly; this coroutine holds the listener until awaitClose.
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in keys) trySend(Unit)
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(Unit)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
