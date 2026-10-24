package io.github.mateuxlucax.healthconnectbridge.ui.format

import androidx.annotation.StringRes
import io.github.mateuxlucax.healthconnectbridge.R
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.model.SyncError.Kind

/** Message shown for this kind of error; it takes the error's detail as its only format argument. */
@get:StringRes
val Kind.message: Int
    get() = when (this) {
        Kind.UNAUTHORIZED -> R.string.error_unauthorized
        Kind.REJECTED -> R.string.error_rejected
        Kind.REDIRECTED -> R.string.error_redirected
        Kind.SERVER -> R.string.error_server
        Kind.NOT_CONFIGURED -> R.string.error_not_configured
        Kind.INVALID_URL -> R.string.error_invalid_url
        Kind.NO_PERMISSIONS -> R.string.error_no_permissions
        Kind.HEALTH_CONNECT_UNAVAILABLE -> R.string.error_health_connect_unavailable
        Kind.PERMISSION_DENIED -> R.string.error_permission_denied
        Kind.LOCAL_NETWORK_PERMISSION -> R.string.error_local_network
        Kind.NETWORK -> R.string.error_network
        Kind.OTHER -> R.string.error_other
    }

/** The error translated with [strings]. */
fun SyncError.message(strings: Strings): String = strings.get(kind.message, detail.orEmpty())
