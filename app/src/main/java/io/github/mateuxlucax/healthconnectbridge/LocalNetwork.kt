package io.github.mateuxlucax.healthconnectbridge

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Android 17 local network protection: apps targeting 37+ need the runtime permission
 * [PERMISSION] to connect to the local network. Without it, TCP connections time out.
 */
object LocalNetwork {
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    /** First SDK where the permission exists and is enforced. */
    const val FIRST_SDK = 37

    /**
     * Whether the server at [url] is on the local network: its host ends with `.local` (mDNS, which itself
     * needs the permission, so it isn't resolved) or any address it resolves to is private, loopback,
     * link-local, unique local ([ServerUrl.isPrivate]) or CGNAT (100.64/10, which Android counts as local).
     * Blocking: resolves the host, so call it off the main thread. A host that doesn't resolve isn't local.
     */
    fun isLocal(url: String, resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName): Boolean {
        val host = ServerUrl.host(url) ?: return false
        if (host.endsWith(".local")) return true
        val addresses = try {
            resolve(host)
        } catch (e: Exception) {
            return false
        }
        return addresses.any { ServerUrl.isPrivate(it) || isCgnat(it) }
    }

    private fun isCgnat(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 4 && (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 64
    }

    /** Below [FIRST_SDK] the permission doesn't exist and access is implicitly granted. */
    fun isGranted(sdk: Int, permissionGranted: () -> Boolean): Boolean = sdk < FIRST_SDK || permissionGranted()

    fun isGranted(context: Context): Boolean = isGranted(Build.VERSION.SDK_INT) {
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    /** True when connecting to [url] would be blocked: access isn't granted and the server is local. */
    suspend fun isBlocked(context: Context, url: String): Boolean =
        !isGranted(context) && withContext(Dispatchers.IO) { isLocal(url) }
}
