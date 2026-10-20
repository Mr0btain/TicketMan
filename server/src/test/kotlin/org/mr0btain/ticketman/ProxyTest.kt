package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.tls.HttpMode
import org.mr0btain.ticketman.components.tls.IpRange
import org.mr0btain.ticketman.components.tls.ProxySettings
import org.mr0btain.ticketman.components.tls.TlsSettings
import org.mr0btain.ticketman.components.tls.ipLiteral
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.AccountRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.ServerInfoResponse
import java.time.ZoneId
import kotlin.test.*

/** PROXY, TRUSTED_PROXIES and what they change: HTTPS defaults, forwarded headers and the startup log */
class ProxyTest {
    private fun config(vararg pairs: Pair<String, String>) = MapApplicationConfig(*pairs)

    // As application.yaml has them, all left blank
    private val blankYaml = arrayOf("tls.port" to "", "tls.httpMode" to "", "env.proxy" to "", "env.trustedProxies" to "")

    @Test
    fun proxyModeIsTheDefaultOutsideDevelopment() {
        assertTrue(ProxySettings.from(config()).enabled)
        assertFalse(ProxySettings.from(config(), developmentMode = true).enabled)

        assertFalse(ProxySettings.from(config("env.proxy" to "off")).enabled)
        assertTrue(ProxySettings.from(config("env.proxy" to "ON"), developmentMode = true).enabled)
        assertTrue(ProxySettings.from(config("env.proxy" to "nonsense")).enabled)
    }

    @Test
    fun httpsDefaultsFollowTheMode() {
        val behindProxy = TlsSettings.from(config(*blankYaml))
        assertFalse(behindProxy.enabled)
        assertEquals(HttpMode.ON, behindProxy.httpMode)

        val direct = TlsSettings.from(config(*blankYaml, "env.proxy" to "off"))
        assertEquals(TlsSettings.DEFAULT_HTTPS_PORT, direct.httpsPort)
        assertEquals(HttpMode.REDIRECT, direct.httpMode)

        // `:server:run -Pdevelopment` still answers on both, as before
        val development = TlsSettings.from(config(*blankYaml), developmentMode = true)
        assertEquals(TlsSettings.DEFAULT_HTTPS_PORT, development.httpsPort)
        assertEquals(HttpMode.ON, development.httpMode)
    }

    @Test
    fun settingsGivenWinOverTheModesDefaults() {
        val httpsBehindProxy = TlsSettings.from(config(*blankYaml, "tls.port" to "9443", "tls.httpMode" to "off"))
        assertEquals(9443, httpsBehindProxy.httpsPort)
        assertEquals(HttpMode.OFF, httpsBehindProxy.httpMode)

        assertFalse(TlsSettings.from(config(*blankYaml, "env.proxy" to "off", "tls.port" to "0")).enabled)
    }

    @Test
    fun trustedProxiesDefaultToLoopbackAndPrivateRanges() {
        val settings = ProxySettings.from(config())

        listOf("127.0.0.1", "::1", "10.1.2.3", "172.18.0.2", "192.168.1.20", "fd12:3456::1", "localhost", "::ffff:10.0.0.9").forEach {
            assertTrue(settings.trusts(it), "$it should be trusted")
        }
        listOf("203.0.113.5", "172.32.0.1", "2001:db8::1", "proxy.example.com", "").forEach {
            assertFalse(settings.trusts(it), "$it shouldn't be trusted")
        }
    }

    @Test
    fun trustedProxiesCanBeNarrowed() {
        val settings = ProxySettings.from(config("env.trustedProxies" to "172.20.0.0/16, 2001:db8::/32 10.0.0.5, nonsense, 10.0.0.0/40"))

        assertTrue(settings.trusts("172.20.5.6"))
        assertTrue(settings.trusts("2001:db8:1::7"))
        assertTrue(settings.trusts("10.0.0.5"))
        assertFalse(settings.trusts("10.0.0.6"))
        assertFalse(settings.trusts("127.0.0.1"))
        assertEquals(listOf("nonsense", "10.0.0.0/40"), settings.rejected)
    }

    @Test
    fun directModeIgnoresForwardedHeadersUnlessTheOldSettingIsOn() {
        assertFalse(ProxySettings.from(config("env.proxy" to "off")).trusts("127.0.0.1"))
        assertFalse(ProxySettings.from(config("env.proxy" to "off")).usesForwardedHeaders)

        val old = ProxySettings.from(config("env.proxy" to "off", "env.trustProxy" to "true"))
        assertTrue(old.trusts("203.0.113.5"))
        assertTrue(old.usesForwardedHeaders)
    }

    @Test
    fun addressesAreReadWithoutLookingNamesUp() {
        assertContentEquals(byteArrayOf(192.toByte(), 168.toByte(), 0, 1), ipLiteral("192.168.0.1"))
        assertEquals(16, ipLiteral("[fe80::1%eth0]")?.size)
        assertNull(ipLiteral("999.1.1.1"))
        assertNull(ipLiteral("1.2.3"))
        assertNull(ipLiteral("example.com"))
        assertNull(IpRange.parse("10.0.0.0/"))
        assertNull(IpRange.parse("::1/129"))
        assertTrue(IpRange.parse("0.0.0.0/0")!!.contains(byteArrayOf(8, 8, 8, 8)))
    }

    @Test
    fun startupLogSaysWhoDoesHttps() {
        val utc = ZoneId.of("UTC")
        val shop = ZoneId.of("America/New_York")

        val proxied = startupNotes(ProxySettings.from(config()), TlsSettings.from(config(*blankYaml)), 8080, null, shop, tzSet = false)
        assertTrue(proxied.first().text.contains("HTTPS is the proxy's job"))
        assertTrue(proxied.any { it.text.contains("10.0.0.0/8") })
        assertTrue(proxied.none { it.warning })

        val direct = startupNotes(
            ProxySettings.from(config("env.proxy" to "off")),
            TlsSettings.from(config(*blankYaml, "env.proxy" to "off")),
            8080, "AB:CD", shop, tzSet = false
        )
        assertTrue(direct.any { it.text.contains("AB:CD") })
        assertTrue(direct.none { it.text.contains("proxy's job") })

        val old = startupNotes(ProxySettings.from(config("env.trustProxy" to "true", "env.trustedProxies" to "bad")), TlsSettings.from(config()), 8080, null, shop, false)
        assertEquals(3, old.count { it.warning }, "TRUST_PROXY, the bad entry, and nothing usable left in the list")
    }

    @Test
    fun startupLogWarnsAboutUtcOnlyWhenTzIsntSet() {
        val proxy = ProxySettings.from(config())
        val tls = TlsSettings.from(config())

        assertTrue(startupNotes(proxy, tls, 8080, null, ZoneId.of("Etc/UTC"), tzSet = false).any { it.warning && it.text.contains("TZ") })
        assertTrue(startupNotes(proxy, tls, 8080, null, ZoneId.of("Etc/UTC"), tzSet = true).none { it.text.contains("TZ") })
        assertTrue(startupNotes(proxy, tls, 8080, null, ZoneId.of("Europe/London"), tzSet = false).none { it.text.contains("TZ") })
    }

    private suspend fun ApplicationTestBuilder.requestFrom(forwardedFor: String, n: Int) =
        client.post("/api/auth/request-account") {
            contentType(ContentType.Application.Json)
            header("X-Forwarded-For", forwardedFor)
            setBody(Json.encodeToString(AccountRequest(username = "asker$n", password = "Bench#2026", name = "Asker $n")))
        }.status

    @Test
    fun behindTheProxyEachClientIsCountedApart() = testApplication {
        val dataDir = createTestDataDir()

        try {
            // The test engine's requests come from localhost, which the default list trusts
            configureTestServer(dataDir, extraConfig = mapOf("env.proxy" to "on"))
            startApplication()

            // Only the last address counts, the one the proxy added
            val statuses = (1..6).map { n -> requestFrom("10.9.9.$n, 203.0.113.1", n) }
            assertEquals(List(5) { HttpStatusCode.Accepted } + HttpStatusCode.TooManyRequests, statuses)
            assertEquals(HttpStatusCode.Accepted, requestFrom("203.0.113.2", 7))
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }

    // An admin's view of the Server tab, asked through a proxy that says the device is 203.0.113.9
    private suspend fun ApplicationTestBuilder.serverInfoThroughProxy(): ServerInfoResponse {
        val json = Json { ignoreUnknownKeys = true }
        UserDatabase().add(User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN))

        val token = json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest("boss", "boss_pass")))
            }.bodyAsText()
        ).accessToken!!

        val response = client.get("/api/admin/server") {
            bearerAuth(token)
            header("X-Forwarded-For", "203.0.113.9")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun theServerTabShowsTheProxyAtWork() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir, extraConfig = mapOf("env.proxy" to "on", "env.trustedProxies" to "127.0.0.1, 10.0.0.0/8"))
            startApplication()

            val info = serverInfoThroughProxy()
            assertTrue(info.proxy)
            assertEquals(listOf("127.0.0.1", "10.0.0.0/8"), info.trustedProxies)
            assertEquals("203.0.113.9", info.clientAddress)
            assertTrue(info.forwarded)
            assertEquals(ZoneId.systemDefault().id, info.timeZone)
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }

    @Test
    fun theServerTabShowsAProxyItDoesntTrust() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir, extraConfig = mapOf("env.proxy" to "on", "env.trustedProxies" to "172.20.0.2"))
            startApplication()

            // The proxy's own address for every device is what tells an admin it isn't trusted
            val info = serverInfoThroughProxy()
            assertEquals("localhost", info.clientAddress)
            assertFalse(info.forwarded)
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }

    @Test
    fun directTheServerTabHasNoProxy() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir, extraConfig = mapOf("env.proxy" to "off"))
            startApplication()

            val info = serverInfoThroughProxy()
            assertFalse(info.proxy)
            assertTrue(info.trustedProxies.isEmpty())
            assertFalse(info.forwarded)
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }

    @Test
    fun headersFromAnAddressNotInTheListAreIgnored() = testApplication {
        val dataDir = createTestDataDir()

        try {
            configureTestServer(dataDir, extraConfig = mapOf("env.proxy" to "on", "env.trustedProxies" to "172.20.0.2"))
            startApplication()

            // All counted as localhost, whatever they claim
            val statuses = (1..6).map { n -> requestFrom("203.0.113.$n", n) }
            assertEquals(List(5) { HttpStatusCode.Accepted } + HttpStatusCode.TooManyRequests, statuses)
        } finally {
            cleanupTestDataDir(dataDir)
        }
    }
}
