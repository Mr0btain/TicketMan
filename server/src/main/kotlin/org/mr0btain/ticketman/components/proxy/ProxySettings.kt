package org.mr0btain.ticketman.components.proxy

import io.ktor.server.config.*

/**
 * The `env.trustedProxies` setting. HTTPS is always a reverse proxy's job, so the server only answers plain HTTP,
 * and each client's address comes from the proxy's `X-Forwarded-For` header. Only a proxy in [trusted] is believed,
 * anyone can send the header
 * @param trusted `TRUSTED_PROXIES`, the addresses forwarded headers are taken from. Empty when it's `none`
 * @param rejected Entries in `TRUSTED_PROXIES` that aren't an address or range, for the startup log
 */
data class ProxySettings(
    val trusted: List<IpRange> = emptyList(),
    val rejected: List<String> = emptyList()
) {
    /** Whether forwarded headers are read at all */
    val usesForwardedHeaders: Boolean get() = trusted.isNotEmpty()

    /** Whether a request whose connection came from [remoteAddress] may say who the client really is */
    fun trusts(remoteAddress: String): Boolean {
        val address = ipLiteral(remoteAddress) ?: return false
        return trusted.any { it.contains(address) }
    }

    companion object {
        /** Loopback and the private ranges, which covers any Docker network with nothing set */
        val DEFAULT_TRUSTED = listOf("127.0.0.1", "::1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7")

        /** `TRUSTED_PROXIES=none`, for a server devices reach directly, so nobody can say they're someone else */
        const val NONE = "none"

        fun from(config: ApplicationConfig): ProxySettings {
            val list = config.propertyOrNull("env.trustedProxies")?.getString()
                ?.split(',', ' ', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }
                .orEmpty()
                .ifEmpty { DEFAULT_TRUSTED }

            if (list.singleOrNull()?.lowercase() == NONE) return ProxySettings()

            return ProxySettings(
                trusted = list.mapNotNull { IpRange.parse(it) },
                rejected = list.filter { IpRange.parse(it) == null }
            )
        }
    }
}

/**
 * One address, or a range of them written as CIDR (`10.0.0.0/8`, `fc00::/7`)
 * @param text As it was written, for the log
 */
class IpRange private constructor(private val network: ByteArray, private val prefix: Int, val text: String) {
    /** Whether [address] (4 bytes for IPv4, 16 for IPv6) is in the range */
    fun contains(address: ByteArray): Boolean {
        if (address.size != network.size) return false

        return (0 until prefix).all { i ->
            val shift = 7 - i % 8
            (network[i / 8].toInt() shr shift and 1) == (address[i / 8].toInt() shr shift and 1)
        }
    }

    override fun toString() = text

    companion object {
        /** Null when [text] isn't an address or a range */
        fun parse(text: String): IpRange? {
            val trimmed = text.trim()
            val address = trimmed.substringBefore('/')
            val bits = if ('/' in trimmed) trimmed.substringAfter('/') else null

            val bytes = ipLiteral(address) ?: return null
            val max = bytes.size * 8
            val prefix = if (bits == null) max else bits.toIntOrNull()?.takeIf { it in 0..max } ?: return null

            return IpRange(bytes, prefix, trimmed)
        }
    }
}

/**
 * The bytes of an IP address written out, never looking a name up (Java's `InetAddress.getByName` does for anything
 * that isn't a valid address). An IPv4 address mapped into IPv6 (`::ffff:10.0.0.1`) comes back as IPv4. `localhost`
 * counts as `127.0.0.1`, since that's what Ktor's test engine reports
 */
fun ipLiteral(text: String): ByteArray? {
    val value = text.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
    if (value.equals("localhost", ignoreCase = true)) return byteArrayOf(127, 0, 0, 1)

    ipv4(value)?.let { return it }
    if (':' !in value || value.any { !(it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.') }) return null

    // Only hex, colons and dots from here, which Java reads as an IPv6 literal and never looks up
    return try {
        java.net.InetAddress.getByName(value).address
    } catch (_: Exception) {
        null
    }
}

private fun ipv4(value: String): ByteArray? {
    val parts = value.split('.')
    if (parts.size != 4) return null

    return parts.map { part ->
        if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
        part.toInt().takeIf { it <= 255 }?.toByte() ?: return null
    }.toByteArray()
}
