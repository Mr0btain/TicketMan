package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/**
 * The certificate the server answers HTTPS with, shown on the admin screen so staff can check it against what a
 * device is asked to trust
 * @param fingerprint SHA-256, written by `formatFingerprint`
 * @param generated True when the server made it itself, false when it was supplied with `SSL_KEYSTORE`
 * @param httpMode Whether plain HTTP is on, redirects to HTTPS, or is off: `on`, `redirect` or `off`
 * @param pendingFingerprint A new certificate made from the admin screen, used from the next start
 * @param pendingExpiresAt When the new certificate runs out
 */
@Serializable
data class ServerCertificateInfo(
    val fingerprint: String,
    val subject: String,
    val expiresAt: Long,
    val generated: Boolean,
    val httpsPort: Int,
    val httpMode: String,
    val pendingFingerprint: String? = null,
    val pendingExpiresAt: Long? = null
) {
    /** Whether it runs out within [RENEW_WARNING_MILLIS] of [now] */
    fun expiresSoon(now: Long): Boolean = expiresAt - now < RENEW_WARNING_MILLIS

    companion object {
        /** A year, when the admin screen starts warning that the certificate will run out */
        const val RENEW_WARNING_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}
