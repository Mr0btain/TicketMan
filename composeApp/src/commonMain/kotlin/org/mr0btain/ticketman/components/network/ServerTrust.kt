package org.mr0btain.ticketman.components.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.server.tls.sameFingerprint
import kotlin.concurrent.Volatile

/**
 * A server's certificate that this device refused, because no authority vouches for it and it isn't the one
 * trusted for that address before
 * @param address `host:port`, from [ServerTrust.addressOf]
 * @param trustedBefore The fingerprint trusted for this address until now, null the first time
 */
data class UntrustedCertificate(
    val address: String,
    val fingerprint: String,
    val subject: String,
    val trustedBefore: String?
) {
    /** The server had a different certificate before, which is worth a stronger warning */
    val changed: Boolean get() = trustedBefore != null
}

/**
 * A server whose certificate is one this device trusts, but for other names than the one it was reached by, like a
 * proxy's public certificate for `tickets.myshop.com` reached by its IP address
 * @param address `host:port` as it was reached, from [ServerTrust.addressOf]
 * @param names The names (and addresses) the certificate is for
 */
data class WrongServerName(val address: String, val names: List<String>) {
    /** What to tell someone, naming what to connect with instead */
    val message: String
        get() = when {
            names.isEmpty() -> "This server's certificate isn't for ${address.substringBeforeLast(':')}. Check the address with your admin."
            else -> "This server's certificate is for ${names.joinToString(" or ")}. Connect with that name."
        }
}

/**
 * Which server certificates this device trusts on top of the ones a public authority vouches for. A server that
 * made its own certificate is trusted the first time someone checks its fingerprint and says yes, then only that
 * certificate is accepted from that address. Only desktop and Android check through this, browsers decide for
 * themselves
 */
class ServerTrust(private val localStorage: LocalStorage) {
    // Read on the HTTP client's threads during handshakes
    @Volatile
    private var trusted: Map<String, String> = localStorage.trustedCertificates

    private val _refused = MutableStateFlow<UntrustedCertificate?>(null)

    /** The last certificate refused, until it's trusted or cleared */
    val refused = _refused.asStateFlow()

    private val _wrongName = MutableStateFlow<WrongServerName?>(null)

    /** The last server turned down for its name, until cleared */
    val wrongName = _wrongName.asStateFlow()

    fun trustedFingerprint(address: String): String? = trusted[address]

    fun isTrusted(address: String, fingerprint: String): Boolean =
        trusted[address]?.let { sameFingerprint(it, fingerprint) } == true

    /** Called by the HTTP engine when it turns a certificate down */
    fun refuse(certificate: UntrustedCertificate) {
        _refused.value = certificate
    }

    /** Called by the HTTP engine when a certificate it trusts doesn't cover the name the server was reached by */
    fun refuseName(wrongName: WrongServerName) {
        _wrongName.value = wrongName
    }

    /** Forgets the last refused certificate and the last wrong name */
    fun clearRefused() {
        _refused.value = null
        _wrongName.value = null
    }

    /** Trusts this certificate for its address from now on, replacing any trusted before */
    fun trust(certificate: UntrustedCertificate) {
        trusted = trusted + (certificate.address to certificate.fingerprint)
        localStorage.trustedCertificates = trusted
        clearRefused()
    }

    companion object {
        fun addressOf(host: String, port: Int): String = "${host.lowercase()}:$port"
    }
}
