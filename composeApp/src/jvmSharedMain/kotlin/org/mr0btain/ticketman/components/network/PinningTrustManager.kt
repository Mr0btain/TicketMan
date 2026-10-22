package org.mr0btain.ticketman.components.network

import okhttp3.internal.tls.OkHostnameVerifier
import org.mr0btain.ticketman.server.tls.formatFingerprint
import org.mr0btain.ticketman.server.tls.sameFingerprint
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
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
 * Accepts what the system trusts, plus certificates from the authority [ServerTrust] was told to trust for that
 * address (or that very certificate, for one a server made itself). Anything else is refused and handed to
 * [ServerTrust.refuse], so the app can ask whether to trust it
 * @param fetchAuthority Asks a proxy for its private CA's own certificate, see [fetchProxyAuthority]. Tests hand in
 * their own
 */
internal class PinningTrustManager(
    private val trust: ServerTrust,
    /** What the system trusts. Tests hand in their own */
    private val system: X509TrustManager = systemTrustManager(),
    private val fetchAuthority: (host: String, port: Int) -> X509Certificate? = ::fetchProxyAuthority
) : X509ExtendedTrustManager() {
    // A trust manager per trusted authority, by fingerprint, made the first time it's needed
    private val authorityChecks = ConcurrentHashMap<String, X509TrustManager>()

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost ?: socket.inetAddress?.hostName
        check(chain, authType, host, socket.port) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, socket) ?: system.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) {
        check(chain, authType, engine.peerHost, engine.peerPort) {
            (system as? X509ExtendedTrustManager)?.checkServerTrusted(chain, authType, engine) ?: system.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        check(chain, authType, null, -1) { system.checkServerTrusted(chain, authType) }
    }

    // Servers never ask this app for a certificate
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, socket: Socket) = refuseClients()
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String, engine: SSLEngine) = refuseClients()
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = refuseClients()

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    private fun refuseClients(): Nothing = throw CertificateException("Client certificates aren't checked here")

    private inline fun check(chain: Array<out X509Certificate>, authType: String, host: String?, port: Int, systemCheck: () -> Unit) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("The server sent no certificate")
        val address = host?.let { ServerTrust.addressOf(it, port) }
        val authority = address?.let { trust.trustedAuthority(it) }

        if (authority != null && isFrom(authority, chain, authType)) return

        try {
            systemCheck()
        } catch (ex: CertificateException) {
            if (address != null) {
                val anchor = anchorOf(chain, host, port)
                val fingerprint = fingerprintOf(anchor)

                // From the authority already trusted, so it failed for another reason (like being out of date), which
                // trusting it again wouldn't fix
                if (authority == null || !sameFingerprint(authority.fingerprint, fingerprint)) {
                    trust.refuse(
                        UntrustedCertificate(
                            address = address,
                            fingerprint = fingerprint,
                            subject = anchor.subjectX500Principal.name,
                            certificate = Base64.getEncoder().encodeToString(anchor.encoded),
                            isAuthority = anchor != leaf,
                            trustedBefore = authority?.fingerprint
                        )
                    )
                }
            }
            throw ex
        }
    }

    /** Whether [chain] is [authority]'s own certificate, or one it issued that's still in date */
    private fun isFrom(authority: TrustedAuthority, chain: Array<out X509Certificate>, authType: String): Boolean {
        if (sameFingerprint(authority.fingerprint, fingerprintOf(chain.first()))) return true

        val check = authorityChecks.getOrPut(authority.fingerprint) {
            val certificate = certificateOf(authority.certificate) ?: return false
            systemTrustManager(
                KeyStore.getInstance("PKCS12").apply {
                    load(null, null)
                    setCertificateEntry("authority", certificate)
                }
            )
        }

        return try {
            check.checkServerTrusted(chain, authType)
            true
        } catch (ex: CertificateException) {
            false
        }
    }

    /**
     * What to offer to trust for [chain]: the top certificate when it signed itself (a self-made one, or a proxy that
     * sends its root), else the proxy's CA when it hands one out that signed the top, else the top certificate
     */
    private fun anchorOf(chain: Array<out X509Certificate>, host: String, port: Int): X509Certificate {
        val top = chain.last()
        if (top.isSelfSigned()) return top

        val authority = try {
            fetchAuthority(host, port)
        } catch (ex: Exception) {
            null
        }

        return authority?.takeIf { it.isSelfSigned() && top.isSignedBy(it) } ?: top
    }
}

/** A certificate from base64 DER, null if it isn't one */
internal fun certificateOf(base64: String): X509Certificate? = try {
    CertificateFactory.getInstance("X.509").generateCertificate(Base64.getDecoder().decode(base64).inputStream()) as X509Certificate
} catch (ex: Exception) {
    null
}

private fun X509Certificate.isSelfSigned(): Boolean =
    subjectX500Principal == issuerX500Principal && isSignedBy(this)

private fun X509Certificate.isSignedBy(issuer: X509Certificate): Boolean = try {
    verify(issuer.publicKey)
    true
} catch (ex: Exception) {
    false
}

/** Biggest CA certificate file read, a real one is a couple of KB */
private const val AUTHORITY_MAX_BYTES = 64 * 1024

/**
 * The private CA's certificate a proxy hands out at [ServerTrust.AUTHORITY_PATH], or null when it doesn't. Read
 * without checking the connection's certificate, since that's the question being answered: whatever comes back is
 * only offered after it's checked to have signed the server's chain, and someone still has to match its fingerprint
 */
internal fun fetchProxyAuthority(host: String, port: Int): X509Certificate? {
    val uriHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
    val connection = URI("https", null, uriHost, port, ServerTrust.AUTHORITY_PATH, null, null).toURL().openConnection() as HttpsURLConnection
    val anything = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    connection.sslSocketFactory = SSLContext.getInstance("TLS").apply { init(null, arrayOf(anything), null) }.socketFactory
    connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
    connection.connectTimeout = AUTHORITY_TIMEOUT_MILLIS
    connection.readTimeout = AUTHORITY_TIMEOUT_MILLIS

    return try {
        if (connection.responseCode != 200) return null
        val bytes = connection.inputStream.use { it.readNBytes(AUTHORITY_MAX_BYTES) }
        // Reads PEM or DER
        CertificateFactory.getInstance("X.509").generateCertificate(bytes.inputStream()) as? X509Certificate
    } finally {
        connection.disconnect()
    }
}

private const val AUTHORITY_TIMEOUT_MILLIS = 3_000

/**
 * OkHttp's usual check that the certificate is for the host it connected to. A trusted self-made certificate (the
 * very one trusted, not one from a trusted authority) passes either way, since the fingerprint already pins it to
 * that address and it may not list every name the server is reached by. Anything else is handed to [ServerTrust.refuseName] with the names it is for
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
