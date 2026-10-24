package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import android.content.SharedPreferences

/** Outcome of the last history import: when it finished, and its error if it failed. */
data class BackfillResult(val finishedMs: Long, val error: SyncError?)

/** User settings and sync checkpoint, persisted in SharedPreferences. */
class Settings(val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("settings", Context.MODE_PRIVATE))

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /** Health Connect changes token, valid only for the record types in [changesTypes]. */
    val changesToken: String?
        get() = prefs.getString(KEY_CHANGES_TOKEN, null)

    val changesTypes: String?
        get() = prefs.getString(KEY_CHANGES_TYPES, null)

    /** When the changes token was last saved, i.e. how far back the checkpoint covers; 0 if never. */
    val changesSavedMs: Long
        get() = prefs.getLong(KEY_CHANGES_SAVED, 0L)

    fun saveChangesToken(token: String, types: String, nowMs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putString(KEY_CHANGES_TOKEN, token)
            .putString(KEY_CHANGES_TYPES, types)
            .putLong(KEY_CHANGES_SAVED, nowMs)
            .apply()
    }

    var lastSuccessMs: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUCCESS, value).apply()

    /** Error of the last incremental sync, null after a successful one. */
    var lastError: SyncError?
        get() = readError(KEY_LAST_ERROR, KEY_LAST_ERROR_DETAIL)
        set(value) = prefs.edit().putError(KEY_LAST_ERROR, KEY_LAST_ERROR_DETAIL, value).apply()

    var lastBackfill: BackfillResult?
        get() {
            val finishedMs = prefs.getLong(KEY_BACKFILL_FINISHED, 0L).takeIf { it > 0 } ?: return null
            return BackfillResult(finishedMs, readError(KEY_BACKFILL_ERROR, KEY_BACKFILL_ERROR_DETAIL))
        }
        set(value) = prefs.edit()
            .putLong(KEY_BACKFILL_FINISHED, value?.finishedMs ?: 0L)
            .putError(KEY_BACKFILL_ERROR, KEY_BACKFILL_ERROR_DETAIL, value?.error)
            .apply()

    private fun readError(kindKey: String, detailKey: String): SyncError? {
        val kind = prefs.getString(kindKey, null) ?: return null
        // Unknown values (e.g. the free text stored by older versions) are dropped.
        val known = SyncError.Kind.entries.find { it.name == kind } ?: return null
        return SyncError(known, prefs.getString(detailKey, null))
    }

    private fun SharedPreferences.Editor.putError(kindKey: String, detailKey: String, error: SyncError?) =
        putString(kindKey, error?.kind?.name).putString(detailKey, error?.detail)

    /** Also true for installs that were already syncing before onboarding existed (v0.1.0). */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_DONE, false) ||
            (serverUrl.isNotBlank() && token.isNotBlank() && changesToken != null)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_CHANGES_TOKEN = "changes_token"
        const val KEY_CHANGES_TYPES = "changes_types"
        const val KEY_CHANGES_SAVED = "changes_saved_ms"
        const val KEY_LAST_SUCCESS = "last_success_ms"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_LAST_ERROR_DETAIL = "last_error_detail"
        const val KEY_BACKFILL_FINISHED = "backfill_finished_ms"
        const val KEY_BACKFILL_ERROR = "backfill_error"
        const val KEY_BACKFILL_ERROR_DETAIL = "backfill_error_detail"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
