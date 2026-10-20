package org.mr0btain.ticketman

import org.mr0btain.ticketman.components.tls.HttpMode
import org.mr0btain.ticketman.components.tls.ProxySettings
import org.mr0btain.ticketman.components.tls.TlsSettings
import org.mr0btain.ticketman.logging.AppLogger
import java.time.ZoneId
import java.time.ZoneOffset

/** One line of [startupNotes], [warning] ones are logged as errors so they stand out */
data class StartupNote(val text: String, val warning: Boolean = false)

/**
 * What the log says on start about how devices reach the server and about its clock
 * @param httpPort The plain HTTP port
 * @param fingerprint The HTTPS certificate's, null when the server isn't doing HTTPS
 * @param zone The time zone "today" is worked out in (`ZoneId.systemDefault()`)
 * @param tzSet Whether the `TZ` variable was set
 */
fun startupNotes(
    proxy: ProxySettings,
    tls: TlsSettings,
    httpPort: Int,
    fingerprint: String?,
    zone: ZoneId,
    tzSet: Boolean
): List<StartupNote> = buildList {
    if (proxy.enabled) {
        add(StartupNote("Behind a reverse proxy (PROXY=on): HTTPS is the proxy's job, it should send requests to plain HTTP on port $httpPort"))

        if (proxy.trusted.isNotEmpty()) {
            add(StartupNote("Client addresses come from X-Forwarded-For on requests from ${proxy.trusted.joinToString(", ")}"))
        } else {
            add(StartupNote("TRUSTED_PROXIES has nothing usable in it, so every device is counted as the proxy's address", warning = true))
        }

        if (fingerprint != null) {
            add(StartupNote("The server's own HTTPS is on as well, on port ${tls.httpsPort}. Certificate fingerprint: $fingerprint"))
        }
        if (tls.enabled && tls.httpMode == HttpMode.REDIRECT) {
            add(StartupNote("HTTP_MODE=redirect is ignored behind a reverse proxy, it would send devices around the proxy", warning = true))
        }
    } else if (fingerprint != null) {
        add(StartupNote("HTTPS on port ${tls.httpsPort}, plain HTTP is ${tls.httpMode.name.lowercase()}"))
        add(StartupNote("Certificate fingerprint (devices are asked to check this): $fingerprint"))
    } else {
        add(StartupNote("No reverse proxy (PROXY=off) and the server's HTTPS is off, so devices connect over plain HTTP on port $httpPort"))
    }

    if (proxy.rejected.isNotEmpty()) {
        add(StartupNote("TRUSTED_PROXIES entries that aren't addresses or ranges were ignored: ${proxy.rejected.joinToString(", ")}", warning = true))
    }
    if (proxy.trustAny) {
        add(
            StartupNote(
                "TRUST_PROXY=true believes X-Forwarded-For from any address, so a device can pretend to be another. " +
                    "It's deprecated, set TRUSTED_PROXIES to the proxy's address instead",
                warning = true
            )
        )
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
