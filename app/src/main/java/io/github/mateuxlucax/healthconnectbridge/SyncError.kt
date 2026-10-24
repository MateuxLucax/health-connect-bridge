package io.github.mateuxlucax.healthconnectbridge

import androidx.annotation.StringRes
import java.io.IOException
import java.net.MalformedURLException

/** Why a sync (or connection test) failed: a stable [kind] plus technical [detail], translated only when shown. */
data class SyncError(val kind: Kind, val detail: String? = null) {

    /** [message] takes [detail] as its only format argument; [retry] says whether trying again later can help. */
    enum class Kind(@StringRes val message: Int, val retry: Boolean) {
        UNAUTHORIZED(R.string.error_unauthorized, false),
        REJECTED(R.string.error_rejected, false),
        SERVER(R.string.error_server, true),
        NOT_CONFIGURED(R.string.error_not_configured, false),
        INVALID_URL(R.string.error_invalid_url, false),
        NO_PERMISSIONS(R.string.error_no_permissions, false),
        HEALTH_CONNECT_UNAVAILABLE(R.string.error_health_connect_unavailable, false),
        PERMISSION_DENIED(R.string.error_permission_denied, false),
        NETWORK(R.string.error_network, true),
        OTHER(R.string.error_other, true),
    }

    fun message(strings: Strings): String = strings.get(kind.message, detail.orEmpty())

    companion object {
        fun of(e: Exception): SyncError = when (e) {
            is HttpStatusException -> when {
                e.code == 401 -> SyncError(Kind.UNAUTHORIZED)
                e.code < 500 -> SyncError(Kind.REJECTED, e.message)
                else -> SyncError(Kind.SERVER, e.message)
            }
            is SetupException -> SyncError(e.kind)
            is SecurityException -> SyncError(Kind.PERMISSION_DENIED, e.message)
            is MalformedURLException -> SyncError(Kind.INVALID_URL)
            is IOException -> SyncError(Kind.NETWORK, e.message)
            // Health Connect throws IllegalStateException when rate limited, among others.
            else -> SyncError(Kind.OTHER, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}

/** Problem the user must fix in the app (settings, permissions); retrying won't help. */
class SetupException(val kind: SyncError.Kind) : Exception(kind.name)
