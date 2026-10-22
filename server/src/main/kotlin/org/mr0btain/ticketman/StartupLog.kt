package org.mr0btain.ticketman

import org.mr0btain.ticketman.components.proxy.ProxySettings
import org.mr0btain.ticketman.logging.AppLogger
import java.time.ZoneId
import java.time.ZoneOffset

/** One line of [startupNotes], [warning] ones are logged as errors so they stand out */
data class StartupNote(val text: String, val warning: Boolean = false)

/**
 * Settings from when the server could do HTTPS itself. Set to anything, they get a warning that they're ignored
 */
val RETIRED_SETTINGS = listOf(
    "PROXY", "TRUST_PROXY", "SSL_PORT", "HTTP_MODE", "SSL_KEYSTORE", "SSL_KEYSTORE_PASSWORD", "SSL_KEY_ALIAS", "SSL_KEY_PASSWORD"
)

/**
 * What the log says on start about how devices reach the server and about its clock
 * @param httpPort The plain HTTP port
 * @param retired The [RETIRED_SETTINGS] that are set
 * @param oldCertificateFolder Whether `<dataPath>/server/tls/`, where the server kept the certificate it made, is
 * still there
 * @param zone The time zone "today" is worked out in (`ZoneId.systemDefault()`)
 * @param tzSet Whether the `TZ` variable was set
 */
fun startupNotes(
    proxy: ProxySettings,
    httpPort: Int,
    retired: List<String> = emptyList(),
    oldCertificateFolder: Boolean = false,
    zone: ZoneId,
    tzSet: Boolean
): List<StartupNote> = buildList {
    add(StartupNote("Plain HTTP on port $httpPort. HTTPS is the reverse proxy's job, it should send requests here"))

    if (proxy.usesForwardedHeaders) {
        add(StartupNote("Client addresses come from X-Forwarded-For on requests from ${proxy.trusted.joinToString(", ")}"))
    } else {
        add(StartupNote("TRUSTED_PROXIES is none, so X-Forwarded-For is ignored and every device behind a proxy counts as the proxy's address"))
    }

    if (proxy.rejected.isNotEmpty()) {
        add(StartupNote("TRUSTED_PROXIES entries that aren't addresses or ranges were ignored: ${proxy.rejected.joinToString(", ")}", warning = true))
    }
    if (retired.isNotEmpty()) {
        add(
            StartupNote(
                "${retired.joinToString(", ")} ${if (retired.size == 1) "is" else "are"} no longer used: the server " +
                    "doesn't do HTTPS itself any more, put a reverse proxy like Caddy in front of it. See docs/operations.md",
                warning = true
            )
        )
    }
    if (oldCertificateFolder) {
        add(StartupNote("server/tls/ holds the certificate the server used to make for itself. Nothing uses it now, it can be deleted"))
    }

    // Fixed at UTC, which is what a container gets unless told otherwise
    if (!tzSet && zone.normalized() == ZoneOffset.UTC) {
        add(
            StartupNote(
                "The time zone is UTC and TZ isn't set, so \"today\" on the end of day report and counter, due dates and " +
                    "the yearly ticket number rollover follow UTC. Set TZ to the shop's, like TZ=America/New_York",
                warning = true
            )
        )
    }
}

fun logStartupNotes(notes: List<StartupNote>) {
    notes.forEach { note ->
        if (note.warning) AppLogger.e("SERVER:Startup", note.text) else AppLogger.m("SERVER:Startup", note.text)
    }
}
