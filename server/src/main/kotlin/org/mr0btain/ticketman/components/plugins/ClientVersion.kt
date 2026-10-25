package org.mr0btain.ticketman.components.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.APP_VERSION_HEADER
import org.mr0btain.ticketman.server.AppVersion
import org.mr0btain.ticketman.server.VersionComparison
import org.mr0btain.ticketman.server.response.SERVER_PING_PATH

private const val TAG = "ClientVersion"

/**
 * Turns away API calls from a client a major version away from this server (a minor before 1.0), with
 * `426 Upgrade Required`, since breaking changes between them could read or write data wrongly. Clients send their
 * version in [APP_VERSION_HEADER]; one that doesn't (from before 0.9, a script, a browser on the status page) is let
 * through. `/api/ping` always answers, so a client can find out the server's version and say which to update
 */
fun Application.configureClientVersionCheck() {
    install(ClientVersionCheck)
}

private val ClientVersionCheck = createApplicationPlugin("ClientVersionCheck") {
    onCall { call ->
        val path = call.request.path()
        if (!path.startsWith("/api/") || path == SERVER_PING_PATH) return@onCall

        val client = AppVersion.parse(call.request.headers[APP_VERSION_HEADER]) ?: return@onCall
        val comparison = VersionComparison(client = client, server = AppVersion.current)
        if (!comparison.breaking) return@onCall

        AppLogger.i(TAG, "Turned away $path from a $client client")
        val update = if (comparison.serverBehind) "Update the server to $client" else "Update the app to ${AppVersion.current}"
        call.respondText(
            text = "This app is version $client and the server is ${AppVersion.current}, which can't work together. $update.",
            status = HttpStatusCode.UpgradeRequired
        )
    }
}
