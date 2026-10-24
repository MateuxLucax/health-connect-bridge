package io.github.mateuxlucax.healthconnectbridge

import android.content.Context
import android.content.SharedPreferences

/** User settings and sync checkpoint, persisted in SharedPreferences. */
class Settings(context: Context) {
    val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

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

    fun saveChangesToken(token: String, types: String) {
        prefs.edit().putString(KEY_CHANGES_TOKEN, token).putString(KEY_CHANGES_TYPES, types).apply()
    }

    var lastSuccessMs: Long
        get() = prefs.getLong(KEY_LAST_SUCCESS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUCCESS, value).apply()

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_ERROR, value).apply()

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_CHANGES_TOKEN = "changes_token"
        const val KEY_CHANGES_TYPES = "changes_types"
        const val KEY_LAST_SUCCESS = "last_success_ms"
        const val KEY_LAST_ERROR = "last_error"
    }
}
