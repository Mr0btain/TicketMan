package org.mr0btain.ticketman

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.server.config.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking
import org.mr0btain.ticketman.components.tls.HttpMode
import org.mr0btain.ticketman.components.tls.ServerTls
import org.mr0btain.ticketman.components.tls.TlsSettings
import org.mr0btain.ticketman.server.tls.formatFingerprint
import org.mr0btain.ticketman.server.tls.sameFingerprint
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.nio.file.Path
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.X509TrustManager
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.*

/**
 * The HTTPS certificate: made once and kept, or supplied, and a real server answering with it
 */
class HttpsTest {
    private lateinit var dataDir: Path

    @BeforeTest
    fun setUp() {
        dataDir = createTestDataDir()
    }

    @AfterTest
    fun tearDown() {
        cleanupTestDataDir(dataDir)
    }

    private fun settings(
        keyStore: String = "",
        password: String = "",
        alias: String = ""
    ) = TlsSettings(httpsPort = 8443, httpMode = HttpMode.ON, keyStorePath = keyStore, keyStorePassword = password, keyAlias = alias, privateKeyPassword = "")

    @Test
    fun aMadeCertificateIsKeptBetweenStarts() {
        val first = ServerTls.load(settings(), dataDir.toString())
        val second = ServerTls.load(settings(), dataDir.toString())

        assertTrue(first.generated)
        assertEquals(first.fingerprint, second.fingerprint)
        assertTrue(first.certificate.notAfter.time - System.currentTimeMillis() > 3_000L * 24 * 60 * 60 * 1000, "Should last about ten years")
    }

    @Test
    fun aSuppliedKeystoreIsUsed() {
        val file = File(dataDir.toFile(), "shop.p12")
        val keyStore = buildKeyStore { certificate("shop") { password = "shop-pass" } }
        file.outputStream().use { keyStore.store(it, "shop-pass".toCharArray()) }

        val certificate = ServerTls.load(settings(file.path, "shop-pass"), dataDir.toString())

        assertFalse(certificate.generated)
        assertEquals("shop", certificate.alias)
        assertFalse(ServerTls.folder(dataDir.toString()).exists(), "Nothing should be made when one is supplied")
    }

    @Test
    fun aBadSuppliedKeystoreStopsTheServer() {
        val file = File(dataDir.toFile(), "shop.p12")
        val keyStore = buildKeyStore { certificate("shop") { password = "shop-pass" } }
        file.outputStream().use { keyStore.store(it, "shop-pass".toCharArray()) }

        assertFails { ServerTls.load(settings(file.path, "wrong"), dataDir.toString()) }
        assertFails { ServerTls.load(settings(file.path, "shop-pass", alias = "other"), dataDir.toString()) }
        assertFails { ServerTls.load(settings(File(dataDir.toFile(), "missing.p12").path, "shop-pass"), dataDir.toString()) }
    }

    @Test
    fun settingsDefaultToOffWithoutTheSection() {
        assertFalse(TlsSettings.from(MapApplicationConfig()).enabled)

        val settings = TlsSettings.from(MapApplicationConfig("tls.port" to "8443", "tls.httpMode" to "Redirect"))
        assertTrue(settings.enabled)
        assertEquals(HttpMode.REDIRECT, settings.httpMode)
        assertEquals(HttpMode.ON, HttpMode.parse("nonsense"))

        // Left blank, plain HTTP redirects, except in development mode
        val blank = MapApplicationConfig("tls.port" to "8443", "tls.httpMode" to "")
        assertEquals(HttpMode.REDIRECT, TlsSettings.from(blank).httpMode)
        assertEquals(HttpMode.ON, TlsSettings.from(blank, developmentMode = true).httpMode)
        assertEquals(HttpMode.OFF, TlsSettings.from(MapApplicationConfig("tls.port" to "8443", "tls.httpMode" to "off"), developmentMode = true).httpMode)
    }

    @Test
    fun fingerprintsCompareHoweverTheyreWritten() {
        assertEquals("0A:FF:10", formatFingerprint(byteArrayOf(0x0a, 0xff.toByte(), 0x10)))
        assertTrue(sameFingerprint("0A:FF:10", "0a ff 10"))
        assertFalse(sameFingerprint("0A:FF:10", "0A:FF:11"))
    }

    @Test
    fun theServerAnswersHttpsWithItsCertificate() = runBlocking {
        val httpsPort = ServerSocket(0).use { it.localPort }
        val server = createServer(
            arrayOf(
                "-port=0",
                "-P:tls.port=$httpsPort",
                "-P:env.dataPath=${dataDir.invariantSeparatorsPathString}",
                "-P:jwt.secret=test-secret"
            )
        )

        server.start(wait = false)
        try {
            val expected = ServerTls.load(settings(), dataDir.toString()).fingerprint
            var seen: String? = null

            // Trusts exactly the certificate it saw, the way a device that pinned it would
            val pinning = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = throw UnsupportedOperationException()
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                    seen = formatFingerprint(MessageDigest.getInstance("SHA-256").digest(chain[0].encoded))
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }

            val connection = URI("https://localhost:$httpsPort/").toURL().openConnection() as HttpsURLConnection
            connection.sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(pinning), null) }.socketFactory
            assertEquals(200, connection.responseCode)
            assertEquals(expected, seen)

            // Java's own trust store has never heard of it
            val plain = URI("https://localhost:$httpsPort/").toURL().openConnection() as HttpsURLConnection
            assertFailsWith<SSLHandshakeException> { plain.responseCode }

            // Plain HTTP redirects to HTTPS by default, outside development mode
            val http = server.engine.resolvedConnectors().first { it.type == ConnectorType.HTTP }
            val redirected = URI("http://localhost:${http.port}/api/ping").toURL().openConnection() as java.net.HttpURLConnection
            redirected.instanceFollowRedirects = false
            assertEquals(302, redirected.responseCode)
            assertEquals("https://localhost:$httpsPort/api/ping", redirected.getHeaderField("Location"))
        } finally {
            server.stop(100, 1000)
        }
    }
}
