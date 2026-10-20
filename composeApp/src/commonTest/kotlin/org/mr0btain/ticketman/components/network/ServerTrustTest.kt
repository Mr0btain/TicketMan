package org.mr0btain.ticketman.components.network

import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import kotlin.test.*

/**
 * Server addresses default to HTTPS, older saved ones keep HTTP, and trusted certificates are kept per address
 */
class ServerTrustTest {
    @Test
    fun anAddressWithNoSchemeMeansHttps() {
        assertEquals("https://192.168.1.20:8443", normalizeServerAddress(" 192.168.1.20:8443/ "))
        assertEquals("http://192.168.1.20:8080", normalizeServerAddress("http://192.168.1.20:8080"))
        assertEquals("", normalizeServerAddress("  "))
    }

    @Test
    fun anOldSavedAddressKeepsPlainHttp() {
        val store = InMemoryKeyValueStore().apply { values["server_url"] = "192.168.1.20:8080" }

        assertEquals("http://192.168.1.20:8080", LocalStorage(store).serverUrl)
        assertEquals("http://192.168.1.20:8080", store.values["server_url"], "The fixed address should be saved")
        assertEquals("https://shop:8443", migrateSavedServerAddress("https://shop:8443"))
    }

    @Test
    fun plainHttpIsNotSecure() {
        assertFalse(isSecureServerAddress("http://192.168.1.20:8080"))
        assertTrue(isSecureServerAddress("https://192.168.1.20:8443"))
        assertTrue(isSecureServerAddress(""))
    }

    @Test
    fun certificatesAreKeyedByHostAndPort() {
        assertEquals("shop.local:8443", trustAddressOf("https://Shop.local:8443/api"))
        assertEquals("shop.local:443", trustAddressOf("https://shop.local"))
    }

    @Test
    fun aTrustedCertificateIsRemembered() {
        val store = InMemoryKeyValueStore()
        val trust = ServerTrust(LocalStorage(store))
        val certificate = UntrustedCertificate("shop:8443", "AA:BB", "CN=shop", trustedBefore = null)

        trust.refuse(certificate)
        assertEquals(certificate, trust.refused.value)
        assertFalse(certificate.changed)

        trust.trust(certificate)
        assertNull(trust.refused.value)
        assertTrue(trust.isTrusted("shop:8443", "aa:bb"))
        assertFalse(trust.isTrusted("shop:8444", "AA:BB"), "Only for the address it came from")

        // A fresh start reads it back
        assertTrue(ServerTrust(LocalStorage(store)).isTrusted("shop:8443", "AA:BB"))
    }

    @Test
    fun aWrongNameSaysWhatTheCertificateIsFor() {
        assertEquals(
            "This server's certificate is for tickets.myshop.com or www.myshop.com. Connect with that name.",
            WrongServerName("10.0.0.5:443", listOf("tickets.myshop.com", "www.myshop.com")).message
        )
        assertEquals(
            "This server's certificate isn't for 10.0.0.5. Check the address with your admin.",
            WrongServerName("10.0.0.5:443", emptyList()).message
        )

        val trust = ServerTrust(LocalStorage(InMemoryKeyValueStore()))
        trust.refuseName(WrongServerName("10.0.0.5:443", listOf("tickets.myshop.com")))
        trust.clearRefused()
        assertNull(trust.wrongName.value)
    }
}
