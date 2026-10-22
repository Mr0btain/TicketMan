package org.mr0btain.ticketman.components.network

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.network.tls.certificates.KeyType
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import kotlinx.coroutines.runBlocking
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.security.auth.x500.X500Principal
import kotlin.test.*

/**
 * The desktop engine against a real HTTPS server with a private certificate: refused until trusted, then only that
 * certificate, or certificates from that same CA, are accepted from that address
 */
class PinnedHttpsTest {
    private val servers = mutableListOf<HttpsServer>()

    @AfterTest
    fun stopServers() {
        servers.forEach { it.stop(0) }
    }

    private fun certificate(
        domains: List<String> = listOf("localhost"),
        ips: List<InetAddress> = listOf(InetAddress.getByName("127.0.0.1"))
    ): KeyStore = buildKeyStore {
        certificate("server") {
            password = "pass"
            hash = HashAlgorithm.SHA256
            keySizeInBits = 2048
            this.domains = domains
            ipAddresses = ips
        }
    }

    private class Authority(val certificate: X509Certificate, val keys: KeyPair)

    /** A certificate authority, a root when [signedBy] is null, like Caddy's local CA and its intermediate */
    private fun authority(name: String, signedBy: Authority? = null): Authority {
        val store = buildKeyStore {
            certificate("ca") {
                password = "pass"
                hash = HashAlgorithm.SHA256
                keySizeInBits = 2048
                keyType = KeyType.CA
                subject = X500Principal("CN=$name")
                signedBy?.let { signWith(it.keys, it.certificate) }
            }
        }
        val certificate = store.getCertificate("ca") as X509Certificate
        return Authority(certificate, KeyPair(certificate.publicKey, store.getKey("ca", "pass".toCharArray()) as PrivateKey))
    }

    /** A server certificate from [issuer], sent with [issuer]'s own certificate the way Caddy sends its chain */
    private fun certificateFrom(issuer: Authority): KeyStore = buildKeyStore {
        certificate("server") {
            password = "pass"
            hash = HashAlgorithm.SHA256
            keySizeInBits = 2048
            domains = listOf("localhost")
            ipAddresses = listOf(InetAddress.getByName("127.0.0.1"))
            signWith(issuer.keys, issuer.certificate)
        }
    }

    // A system that trusts this certificate, the way a public one behind a proxy is trusted
    private fun systemTrusting(keyStore: KeyStore) = systemTrustManager(
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("public", keyStore.getCertificate("server"))
        }
    )

    /** Starts a server answering 200 on [port] (0 picks one) and returns its port */
    private fun serve(keyStore: KeyStore, port: Int = 0): Int {
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, "pass".toCharArray()) }
        val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }

        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", port), 0).apply {
            httpsConfigurator = HttpsConfigurator(context)
            createContext("/") { exchange ->
                // Answers the up check the way a TicketMan server does
                val body = if (exchange.requestURI.path == SERVER_PING_PATH) SERVER_PING_BODY.encodeToByteArray() else ByteArray(0)
                exchange.sendResponseHeaders(200, if (body.isEmpty()) -1 else body.size.toLong())
                if (body.isNotEmpty()) exchange.responseBody.use { it.write(body) }
                exchange.close()
            }
            start()
        }
        servers += server
        return server.address.port
    }

    private fun fingerprint(keyStore: KeyStore) = fingerprintOf(keyStore.getCertificate("server") as X509Certificate)

    private fun reachable(client: HttpClient, url: String): Boolean = runBlocking {
        try {
            client.get(url).status.value == 200
        } catch (ex: Exception) {
            false
        }
    }

    @Test
    fun aSelfMadeCertificateIsRefusedUntilTrusted() {
        val keyStore = certificate()
        val port = serve(keyStore)
        val trust = ServerTrust(LocalStorage(InMemoryKeyValueStore()))
        val client = HttpClient(getHttpEngine(trust))

        assertFalse(reachable(client, "https://localhost:$port/"))
        val refused = assertNotNull(trust.refused.value)
        assertEquals("localhost:$port", refused.address)
        assertEquals(fingerprint(keyStore), refused.fingerprint)
        assertFalse(refused.changed)

        trust.trust(refused)
        assertTrue(reachable(client, "https://localhost:$port/"))
    }

    @Test
    fun aDifferentCertificateAtATrustedAddressIsRefused() {
        val first = certificate()
        val port = serve(first)
        val trust = ServerTrust(LocalStorage(InMemoryKeyValueStore()))
        reachable(HttpClient(getHttpEngine(trust)), "https://localhost:$port/")
        trust.trust(trust.refused.value!!)

        // Same address, new key, like something pretending to be the server
        servers.removeFirst().stop(0)
        val second = certificate()
        serve(second, port)

        assertFalse(reachable(HttpClient(getHttpEngine(trust)), "https://localhost:$port/"))
        val refused = assertNotNull(trust.refused.value)
        assertTrue(refused.changed)
        assertEquals(fingerprint(first), refused.trustedBefore)
        assertEquals(fingerprint(second), refused.fingerprint)
    }

    @Test
    fun aTrustedCertificateWorksUnderAnyName() {
        // Made for another name, the way a server reached by a name it didn't know about would look
        val keyStore = certificate(domains = listOf("somewhere.else"))
        val port = serve(keyStore)
        val trust = ServerTrust(LocalStorage(InMemoryKeyValueStore()))
        val client = HttpClient(getHttpEngine(trust))

        assertFalse(reachable(client, "https://localhost:$port/"))
        trust.trust(trust.refused.value!!)
        assertTrue(reachable(client, "https://localhost:$port/"))
    }

    @Test
    fun aPublicCertificateReachedByTheWrongNameSaysWhichNameItsFor() = runBlocking {
        val keyStore = certificate(domains = listOf("localhost"), ips = emptyList())
        val port = serve(keyStore)
        val storage = LocalStorage(InMemoryKeyValueStore())
        val trust = ServerTrust(storage)
        val remote = RemoteHandler(pinnedHttpEngine(trust, systemTrusting(keyStore)), storage, trust)

        // By IP: the certificate passes the system check but isn't for 127.0.0.1
        assertEquals(ConnectionResult.WRONG_NAME, remote.setUrl("https://127.0.0.1:$port"))
        assertNull(trust.refused.value, "Nothing to trust, so no trust dialog")
        val wrongName = assertNotNull(trust.wrongName.value)
        assertEquals("127.0.0.1:$port", wrongName.address)
        assertEquals(listOf("localhost"), wrongName.names)
        assertEquals("This server's certificate is for localhost. Connect with that name.", wrongName.message)

        // By the name it's for, no dialog and nothing left over
        assertEquals(ConnectionResult.CONNECTED, remote.setUrl("https://localhost:$port"))
        assertNull(trust.wrongName.value)
    }

    @Test
    fun connectingReportsAnUntrustedServer() = runBlocking {
        val port = serve(certificate())
        val storage = LocalStorage(InMemoryKeyValueStore())
        val trust = ServerTrust(storage)
        val remote = RemoteHandler(getHttpEngine(trust), storage, trust)

        assertEquals(ConnectionResult.UNTRUSTED, remote.setUrl("https://localhost:$port"))

        trust.trust(trust.refused.value!!)
        assertEquals(ConnectionResult.CONNECTED, remote.setUrl("https://localhost:$port"))
    }

    @Test
    fun aProxysPrivateCaIsTrustedRatherThanItsShortLivedCertificate() {
        val root = authority("Shop Root")
        val port = serve(certificateFrom(authority("Shop Intermediate", root)))
        val trust = ServerTrust(LocalStorage(InMemoryKeyValueStore()))
        val client = HttpClient(pinnedHttpEngine(trust, fetchAuthority = { _, _ -> root.certificate }))

        assertFalse(reachable(client, "https://localhost:$port/"))
        val refused = assertNotNull(trust.refused.value)
        assertTrue(refused.isAuthority)
        assertEquals(fingerprintOf(root.certificate), refused.fingerprint)
        assertEquals("CN=Shop Root", refused.subject)

        trust.trust(refused)
        assertTrue(reachable(client, "https://localhost:$port/"))

        // Caddy makes a new certificate every 12 hours and a new intermediate every week, from the same root
        servers.removeFirst().stop(0)
        serve(certificateFrom(authority("Next Intermediate", root)), port)
        trust.clearRefused()
        assertTrue(reachable(HttpClient(pinnedHttpEngine(trust, fetchAuthority = { _, _ -> null })), "https://localhost:$port/"))
        assertNull(trust.refused.value, "Nothing new to ask about")
    }

    @Test
    fun aCertificateFromAnotherCaAtATrustedAddressIsRefused() {
        val root = authority("Shop Root")
        val port = serve(certificateFrom(authority("Shop Intermediate", root)))
        val trust = ServerTrust(LocalStorage(InMemoryKeyValueStore()))
        reachable(HttpClient(pinnedHttpEngine(trust, fetchAuthority = { _, _ -> root.certificate })), "https://localhost:$port/")
        trust.trust(trust.refused.value!!)

        // Something else on the address, with its own CA
        servers.removeFirst().stop(0)
        val other = authority("Other Root")
        serve(certificateFrom(other), port)

        assertFalse(reachable(HttpClient(pinnedHttpEngine(trust, fetchAuthority = { _, _ -> other.certificate })), "https://localhost:$port/"))
        val refused = assertNotNull(trust.refused.value)
        assertTrue(refused.changed)
        assertEquals(fingerprintOf(root.certificate), refused.trustedBefore)
        assertEquals(fingerprintOf(other.certificate), refused.fingerprint)
    }

    @Test
    fun withoutTheCaTheTopCertificateSentIsOffered() {
        val intermediate = authority("Shop Intermediate", authority("Shop Root"))
        val port = serve(certificateFrom(intermediate))
        // A CA the proxy hands out that didn't sign this chain is no use, and isn't offered
        val unrelated = authority("Unrelated Root")

        listOf<X509Certificate?>(null, unrelated.certificate).forEach { handedOut ->
            val trust = ServerTrust(LocalStorage(InMemoryKeyValueStore()))
            assertFalse(reachable(HttpClient(pinnedHttpEngine(trust, fetchAuthority = { _, _ -> handedOut })), "https://localhost:$port/"))

            val refused = assertNotNull(trust.refused.value)
            assertEquals(fingerprintOf(intermediate.certificate), refused.fingerprint)
            assertTrue(refused.isAuthority)
        }
    }

    @Test
    fun theCaIsFetchedFromTheProxy() {
        val root = authority("Shop Root")
        val keyStore = certificateFrom(authority("Shop Intermediate", root))
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, "pass".toCharArray()) }
        val pem = "-----BEGIN CERTIFICATE-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(root.certificate.encoded) +
            "\n-----END CERTIFICATE-----\n"

        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) })
            createContext(ServerTrust.AUTHORITY_PATH) { exchange ->
                val body = pem.encodeToByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
        servers += server

        assertEquals(root.certificate, fetchProxyAuthority("localhost", server.address.port))
        assertEquals(root.certificate, certificateOf(Base64.getEncoder().encodeToString(root.certificate.encoded)))
        assertNull(certificateOf("not a certificate"))
    }
}
