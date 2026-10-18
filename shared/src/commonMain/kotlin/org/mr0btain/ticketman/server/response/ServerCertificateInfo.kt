package org.mr0btain.ticketman.server.response

import kotlinx.serialization.Serializable

/**
 * The certificate the server answers HTTPS with, shown on the admin screen so staff can check it against what a
 * device is asked to trust
 * @param fingerprint SHA-256, written by `formatFingerprint`
 * @param generated True when the server made it itself, false when it was supplied with `SSL_KEYSTORE`
 * @param httpMode Whether plain HTTP is on, redirects to HTTPS, or is off: `on`, `redirect` or `off`
 */
@Serializable
data class ServerCertificateInfo(
    val fingerprint: String,
    val subject: String,
    val expiresAt: Long,
    val generated: Boolean,
    val httpsPort: Int,
    val httpMode: String
)
