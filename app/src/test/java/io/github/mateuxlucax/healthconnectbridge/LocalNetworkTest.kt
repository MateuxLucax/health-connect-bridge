package io.github.mateuxlucax.healthconnectbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class LocalNetworkTest {
    private val resolved = mutableListOf<String>()

    /** Resolves every host to [ips] (IP literals, so no real DNS lookup) and records the lookups. */
    private fun resolver(vararg ips: String): (String) -> Array<InetAddress> = { host ->
        resolved += host
        ips.map { InetAddress.getByName(it) }.toTypedArray()
    }

    @Test
    fun hostResolvingToAPrivateAddressIsLocal() {
        // The reported case: a public-looking name that resolves to the LAN.
        assertTrue(LocalNetwork.isLocal("https://hc.home.mateux.dev", resolver("192.168.2.15")))
        assertEquals(listOf("hc.home.mateux.dev"), resolved)
    }

    @Test
    fun localRanges() {
        listOf(
            "10.0.2.2", "172.16.0.1", "172.31.255.255", "192.168.0.10", "127.0.0.1", "169.254.1.1",
            "100.64.0.1", "100.127.255.255", "::1", "fd12:3456::1", "fe80::1",
        ).forEach { assertTrue(it, LocalNetwork.isLocal("http://server:8080", resolver(it))) }
    }

    @Test
    fun publicRanges() {
        listOf("8.8.8.8", "172.32.0.1", "100.63.255.255", "100.128.0.1", "192.169.0.1", "2001:db8::1").forEach {
            assertFalse(it, LocalNetwork.isLocal("https://example.com", resolver(it)))
        }
    }

    @Test
    fun anyLocalAddressMakesItLocal() {
        assertTrue(LocalNetwork.isLocal("https://example.com", resolver("8.8.8.8", "192.168.1.2")))
    }

    @Test
    fun dotLocalIsLocalWithoutResolving() {
        assertTrue(LocalNetwork.isLocal("http://pi.local:8080", resolver("8.8.8.8")))
        assertTrue(LocalNetwork.isLocal("http://PI.LOCAL", resolver("8.8.8.8")))
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun unresolvableOrInvalidIsNotLocal() {
        assertFalse(LocalNetwork.isLocal("https://nowhere.example") { throw UnknownHostException(it) })
        assertFalse(LocalNetwork.isLocal("not a url", resolver("192.168.0.1")))
        assertFalse(LocalNetwork.isLocal("", resolver("192.168.0.1")))
    }

    @Test
    fun ipLiteralUrls() {
        assertTrue(LocalNetwork.isLocal("http://192.168.0.10:8080"))
        assertTrue(LocalNetwork.isLocal("http://[::1]:8080"))
        assertFalse(LocalNetwork.isLocal("http://8.8.8.8"))
    }

    @Test
    fun grantedBeforeAndroid17WithoutAsking() {
        var asked = false
        assertTrue(LocalNetwork.isGranted(36) { asked = true; false })
        assertTrue(LocalNetwork.isGranted(26) { asked = true; false })
        assertFalse(asked)
    }

    @Test
    fun fromAndroid17ThePermissionDecides() {
        assertTrue(LocalNetwork.isGranted(37) { true })
        assertFalse(LocalNetwork.isGranted(37) { false })
        assertFalse(LocalNetwork.isGranted(38) { false })
    }

    @Test
    fun permissionName() {
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LocalNetwork.PERMISSION)
    }
}
