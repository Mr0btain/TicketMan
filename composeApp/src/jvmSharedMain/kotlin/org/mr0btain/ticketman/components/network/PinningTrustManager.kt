package org.mr0btain.ticketman.components.network

import okhttp3.internal.tls.OkHostnameVerifier
import org.mr0btain.ticketman.server.tls.formatFingerprint
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

internal fun fingerprintOf(certificate: X509Certificate): String =
    formatFingerprint(MessageDigest.getInstance("SHA-256").digest(certificate.encoded))

/** The certificate authorities [keyStore] holds, or the system's (`javax.net.ssl.trustStore` if set) when null */
internal fun systemTrustManager(keyStore: KeyStore? = null): X509TrustManager =
    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .apply { init(keyStore) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()

/** The DNS names and IP addresses [certificate] is for, or its common name when it lists none */
internal fun namesOf(certificate: X509Certificate): List<String> {
    // Entries are [type, value]: 2 is a DNS name, 7 an IP address
    val alternatives = certificate.subjectAlternativeNames.orEmpty()
        .filter { (it[0] as? Int) in setOf(2, 7) }
        .mapNotNull { it[1] as? String }
    if (alternatives.isNotEmpty()) return alternatives.distinct()

    return certificate.subjectX500Principal.name.split(',')
        .map { it.trim() }
        .filter { it.startsWith("CN=", ignoreCase = true) }
        .map { it.substring(3) }
}

/**
 * Accepts what the system trusts, plus the self-made certificate [ServerTrust] was told to trust for that address.
 * Anything else is refused and handed to [ServerTrust.refuse], so the app can ask whether to trust it
 */
internal class PinningTrustManager(
    private val trust: ServerTrust,
    /** What the system trusts. Tests hand in their own */
    private val system: X509TrustManager = systemTrustManager()
) : X509ExtendedTrustManager() {
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost ?: socket.inetAddress?.hostName
        check(chain, host, socket.port) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: system.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) {
        check(chain, engine.peerHost, engine.peerPort) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: system.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        check(chain, null, -1) { system.checkServerTrusted(chain, authType) }
    }

    // Servers never ask this app for a certificate
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) = refuseClients()
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) = refuseClients()
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = refuseClients()

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    private fun refuseClients(): Nothing = throw CertificateException("Client certificates aren't checked here")

    private inline fun check(chain: Array<out X509Certificate>, host: String?, port: Int, systemCheck: () -> Unit) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("The server sent no certificate")
        val fingerprint = fingerprintOf(leaf)
        val address = host?.let { ServerTrust.addressOf(it, port) }

        if (address != null && trust.isTrusted(address, fingerprint)) return

        try {
            systemCheck()
        } catch (ex: CertificateException) {
            if (address != null) {
                trust.refuse(UntrustedCertificate(address, fingerprint, leaf.subjectX500Principal.name, trust.trustedFingerprint(address)))
            }
            throw ex
        }
    }
}

/**
 * OkHttp's usual check that the certificate is for the host it connected to. A trusted self-made certificate
 * passes either way, since the fingerprint already pins it to that address and it may not list every name the
 * server is reached by. Anything else is handed to [ServerTrust.refuseName] with the names it is for
 */
internal class PinningHostnameVerifier(private val trust: ServerTrust) : HostnameVerifier {
    override fun verify(host: String, session: SSLSession): Boolean {
        if (OkHostnameVerifier.verify(host, session)) return true

        val leaf = session.peerCertificates.firstOrNull() as? X509Certificate ?: return false
        val address = ServerTrust.addressOf(host, session.peerPort)
        if (trust.isTrusted(address, fingerprintOf(leaf))) return true

        trust.refuseName(WrongServerName(address, namesOf(leaf)))
        return false
    }
}
