package org.mr0btain.ticketman.components.network

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.extensions.HashAlgorithm
import kotlinx.coroutines.runBlocking
import org.mr0btain.ticketman.components.data.local.InMemoryKeyValueStore
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.test.*

/**
 * The desktop engine against a real HTTPS server with a self-made certificate: refused until trusted, then only
 * that certificate is accepted from that address
 */
class PinnedHttpsTest {
    private val servers = mutableListOf<HttpsServer>()

    @AfterTest
    fun stopServers() {
        servers.forEach { it.stop(0) }
    }

    private fun certificate(domains: List<String> = listOf("localhost")): KeyStore = buildKeyStore {
        certificate("server") {
            password = "pass"
            hash = HashAlgorithm.SHA256
            keySizeInBits = 2048
            this.domains = domains
            ipAddresses = listOf(InetAddress.getByName("127.0.0.1"))
        }
    }

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
    fun connectingReportsAnUntrustedServer() = runBlocking {
        val port = serve(certificate())
        val storage = LocalStorage(InMemoryKeyValueStore())
        val trust = ServerTrust(storage)
        val remote = RemoteHandler(getHttpEngine(trust), storage, trust)

        assertEquals(ConnectionResult.UNTRUSTED, remote.setUrl("https://localhost:$port"))

        trust.trust(trust.refused.value!!)
        assertEquals(ConnectionResult.CONNECTED, remote.setUrl("https://localhost:$port"))
    }
}
