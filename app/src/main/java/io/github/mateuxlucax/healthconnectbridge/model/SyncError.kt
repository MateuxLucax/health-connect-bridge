package io.github.mateuxlucax.healthconnectbridge.model

/**
 * Why a sync (or connection test) failed: a stable [kind] plus technical [detail]. The UI turns the kind into
 * translated text (ui/format/SyncErrorText.kt); exceptions are mapped to kinds in sync/SyncErrors.kt.
 */
data class SyncError(val kind: Kind, val detail: String? = null) {

    /** [retry] says whether trying again later can help. */
    enum class Kind(val retry: Boolean) {
        UNAUTHORIZED(false),
        REJECTED(false),
        REDIRECTED(false),
        SERVER(true),
        NOT_CONFIGURED(false),
        INVALID_URL(false),
        NO_PERMISSIONS(false),
        HEALTH_CONNECT_UNAVAILABLE(false),
        PERMISSION_DENIED(false),
        /** Android 17+ blocks the local server until the user grants the local network permission. */
        LOCAL_NETWORK_PERMISSION(false),
        NETWORK(true),
        OTHER(true),
    }
}

/** Problem the user must fix in the app (settings, permissions); retrying won't help. */
class SetupException(val kind: SyncError.Kind) : Exception(kind.name)
