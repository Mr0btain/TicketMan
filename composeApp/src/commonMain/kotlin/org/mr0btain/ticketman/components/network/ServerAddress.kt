package org.mr0btain.ticketman.components.network

import io.ktor.http.Url

/**
 * A server address as someone typed it. No scheme means HTTPS, trailing slashes are dropped. Blank stays blank
 */
fun normalizeServerAddress(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""

    return if ("://" in trimmed) trimmed else "https://$trimmed"
}

/**
 * Addresses saved before HTTPS had no scheme and always went out as plain HTTP, so they keep doing that rather
 * than suddenly trying HTTPS on a port that doesn't speak it
 */
fun migrateSavedServerAddress(saved: String): String {
    val trimmed = saved.trim()
    return if (trimmed.isBlank() || "://" in trimmed) trimmed else "http://$trimmed"
}

/** Whether traffic to [url] is encrypted. Blank counts as secure, there's nothing to warn about yet */
fun isSecureServerAddress(url: String): Boolean = url.isBlank() || url.trim().startsWith("https://", ignoreCase = true)

/** The `host:port` [ServerTrust] keys certificates by, null when [url] can't be read */
fun trustAddressOf(url: String): String? = try {
    Url(url).let { ServerTrust.addressOf(it.host, it.port) }
} catch (ex: Exception) {
    null
}
