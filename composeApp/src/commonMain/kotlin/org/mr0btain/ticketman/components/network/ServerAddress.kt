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

/**
 * Whether what's sent to [url] can't be read on the network: HTTPS, or a server on this same machine, whose traffic
 * never leaves it. Blank counts as secure, there's nothing to warn about yet
 */
fun isSecureServerAddress(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isBlank() || trimmed.startsWith("https://", ignoreCase = true)) return true

    val host = try {
        Url(trimmed).host.lowercase().removePrefix("[").removeSuffix("]")
    } catch (ex: Exception) {
        return false
    }
    return host == "localhost" || host == "::1" || host.startsWith("127.")
}

/** The `host:port` [ServerTrust] keys certificates by, null when [url] can't be read */
fun trustAddressOf(url: String): String? = try {
    Url(url).let { ServerTrust.addressOf(it.host, it.port) }
} catch (ex: Exception) {
    null
}
