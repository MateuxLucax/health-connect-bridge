package io.github.mateuxlucax.healthconnectbridge.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    @Test
    fun validUrls() {
        listOf(
            "http://192.168.0.10:8080", "https://health.example.com", "HTTP://host", " http://host/ ",
            "http://[::1]:8080", "https://example.com/prefix",
        ).forEach { assertTrue(it, ServerUrl.isValid(it)) }
    }

    @Test
    fun invalidUrls() {
        listOf(
            "", "192.168.0.10:8080", "ftp://example.com", "file:///etc/passwd", "http://", "http:// spaces",
            "mailto:me@example.com", "javascript:alert(1)", "http//example.com",
        ).forEach { assertFalse(it, ServerUrl.isValid(it)) }
    }

    @Test
    fun cleartextToLocalHostsIsFine() {
        listOf(
            "http://10.0.2.2:8080", "http://10.1.2.3", "http://172.16.0.1", "http://172.31.255.255",
            "http://192.168.0.10:8080", "http://127.0.0.1", "http://169.254.1.1", "http://localhost:8080",
            "http://LOCALHOST", "http://pi.local", "http://nas.lan", "http://server.home.arpa", "http://[::1]:8080",
            "http://[fd12:3456::1]", "http://[fc00::1]", "http://[fe80::1]", "http://[febf::1]",
        ).forEach { assertFalse(it, ServerUrl.isCleartextToPublicHost(it)) }
    }

    @Test
    fun cleartextToPublicHostsWarns() {
        listOf(
            "http://example.com", "http://8.8.8.8", "http://172.32.0.1", "http://172.15.0.1", "http://192.169.0.1",
            "http://11.0.0.1", "http://[2001:db8::1]", "http://[fec0::1]", "http://local.example.com",
            "http://example.lan.com",
        ).forEach { assertTrue(it, ServerUrl.isCleartextToPublicHost(it)) }
    }

    @Test
    fun httpsNeverWarns() {
        assertFalse(ServerUrl.isCleartextToPublicHost("https://example.com"))
        assertFalse(ServerUrl.isCleartextToPublicHost("not a url"))
    }
}
