package org.mr0btain.ticketman.components.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.components.data.local.LocalStorage
import org.mr0btain.ticketman.server.tls.sameFingerprint
import kotlin.concurrent.Volatile

/**
 * The certificate this device trusts for one server address on top of the ones a public authority vouches for:
 * the private certificate authority its proxy's certificates come from (like Caddy's local CA), or the server's
 * own certificate when it was made by itself
 * @param certificate The certificate itself, DER as base64, so the chain can be checked against it
 */
@Serializable
data class TrustedAuthority(val fingerprint: String, val certificate: String)

/**
 * A server's certificate that this device refused, because no authority vouches for it and it doesn't come from
 * the one trusted for that address before. What's offered to trust is [certificate], which is the authority behind
 * the server's certificate when the proxy hands it out ([isAuthority]), or else the top one the server sent
 * @param address `host:port`, from [ServerTrust.addressOf]
 * @param fingerprint [certificate]'s
 * @param subject [certificate]'s name, like `CN=Caddy Local Authority - 2026 ECC Root`
 * @param certificate DER as base64
 * @param isAuthority Whether [certificate] is a certificate authority, rather than the server's own certificate
 * @param trustedBefore The fingerprint trusted for this address until now, null the first time
 */
data class UntrustedCertificate(
    val address: String,
    val fingerprint: String,
    val subject: String,
    val certificate: String = "",
    val isAuthority: Boolean = false,
    val trustedBefore: String? = null
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
 * Which server certificates this device trusts on top of the ones a public authority vouches for. A proxy with a
 * private certificate is trusted the first time someone checks the fingerprint and says yes, then only certificates
 * from that same authority are accepted from that address. Only desktop and Android check through this, browsers
 * decide for themselves
 */
class ServerTrust(private val localStorage: LocalStorage) {
    // Read on the HTTP client's threads during handshakes
    @Volatile
    private var trusted: Map<String, TrustedAuthority> = localStorage.trustedAuthorities

    private val _refused = MutableStateFlow<UntrustedCertificate?>(null)

    /** The last certificate refused, until it's trusted or cleared */
    val refused = _refused.asStateFlow()

    private val _wrongName = MutableStateFlow<WrongServerName?>(null)

    /** The last server turned down for its name, until cleared */
    val wrongName = _wrongName.asStateFlow()

    fun trustedAuthority(address: String): TrustedAuthority? = trusted[address]

    fun trustedFingerprint(address: String): String? = trusted[address]?.fingerprint

    /** Whether [fingerprint] is the very certificate trusted for [address] */
    fun isTrusted(address: String, fingerprint: String): Boolean =
        trusted[address]?.let { sameFingerprint(it.fingerprint, fingerprint) } == true

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
        trusted = trusted + (certificate.address to TrustedAuthority(certificate.fingerprint, certificate.certificate))
        localStorage.trustedAuthorities = trusted
        clearRefused()
    }

    companion object {
        /**
         * Where a proxy with a private CA hands out the CA's own certificate (docker/Caddyfile), so this device
         * trusts the CA rather than a server certificate the proxy replaces every few hours
         */
        const val AUTHORITY_PATH = "/.well-known/ticketman/ca.pem"

        fun addressOf(host: String, port: Int): String = "${host.lowercase()}:$port"
    }
}
