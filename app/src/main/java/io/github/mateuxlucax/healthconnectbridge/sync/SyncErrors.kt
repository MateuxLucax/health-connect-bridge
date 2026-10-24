package io.github.mateuxlucax.healthconnectbridge.sync

import io.github.mateuxlucax.healthconnectbridge.data.exporter.HttpStatusException
import io.github.mateuxlucax.healthconnectbridge.model.SetupException
import io.github.mateuxlucax.healthconnectbridge.model.SyncError
import io.github.mateuxlucax.healthconnectbridge.model.SyncError.Kind
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException

/** Maps the exceptions of a sync or a connection test to a [SyncError]. */
object SyncErrors {
    fun of(e: Exception): SyncError = when (e) {
        is HttpStatusException -> when {
            e.code == 401 -> SyncError(Kind.UNAUTHORIZED)
            e.code in 300..399 -> SyncError(Kind.REDIRECTED, e.message)
            // Request timeout and rate limiting are temporary.
            e.code == 408 || e.code == 429 -> SyncError(Kind.SERVER, e.message)
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

    /**
     * Like [of], but a connect timeout or refusal is reported as [Kind.LOCAL_NETWORK_PERMISSION] when
     * [localNetworkBlocked] says so: that is how Android 17 fails connections it blocks.
     */
    suspend fun of(e: Exception, localNetworkBlocked: suspend () -> Boolean): SyncError {
        val error = of(e)
        val connectFailure = e is SocketTimeoutException || e is ConnectException
        return if (connectFailure && localNetworkBlocked()) SyncError(Kind.LOCAL_NETWORK_PERMISSION) else error
    }
}
