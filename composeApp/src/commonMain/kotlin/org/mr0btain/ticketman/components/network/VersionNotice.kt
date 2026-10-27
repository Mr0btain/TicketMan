package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.mr0btain.ticketman.server.API_VERSION
import org.mr0btain.ticketman.server.APP_VERSION
import org.mr0btain.ticketman.server.APP_VERSION_HEADER
import org.mr0btain.ticketman.server.AppVersion
import org.mr0btain.ticketman.server.VersionComparison
import org.mr0btain.ticketman.server.VersionGap

/**
 * What to tell someone about the server being from another version than this app, from [versionNoticeOf]
 * @param blocking They're a major version apart (a minor before 1.0), with breaking changes between them, so nothing
 * is loaded from the server until one is updated
 * @param updateServer Whether the server is the one behind, otherwise it's this app
 * @param message What's wrong and which one to update
 * @param appVersion This app's version
 * @param serverVersion The server's, null for one too old to say
 */
data class VersionNotice(
    val blocking: Boolean,
    val updateServer: Boolean,
    val message: String,
    val appVersion: String,
    val serverVersion: String?
)

/**
 * Compares the server's release ([serverVersion]) with this app's. A major version apart is [VersionNotice.blocking],
 * a minor apart is a notice saying which to update, a patch apart is nothing. A server that doesn't say its release
 * is from before 0.9, so only its [serverApiVersion] is checked, as before, and only ever warned about. The API
 * version still warns between two builds of the same release
 * @return Null when they match
 */
fun versionNoticeOf(
    serverVersion: AppVersion?,
    serverApiVersion: Int?,
    appVersion: AppVersion = AppVersion.current,
    appApiVersion: Int = API_VERSION
): VersionNotice? {
    fun notice(blocking: Boolean, updateServer: Boolean, message: String) =
        VersionNotice(blocking, updateServer, message, appVersion.toString(), serverVersion?.toString())

    if (serverVersion != null) {
        val comparison = VersionComparison(client = appVersion, server = serverVersion)
        val versions = "This app is version $appVersion and the server is $serverVersion."

        when (comparison.gap) {
            VersionGap.MAJOR -> return notice(
                blocking = true,
                updateServer = comparison.serverBehind,
                message = if (comparison.serverBehind) {
                    "$versions The server is too old for this app, so nothing can be loaded from it. Update the server to $appVersion."
                } else {
                    "$versions This app is too old for the server, so nothing can be loaded from it. Update the app to $serverVersion."
                }
            )

            VersionGap.MINOR -> return notice(
                blocking = false,
                updateServer = comparison.serverBehind,
                message = if (comparison.serverBehind) {
                    "$versions Everything still works, but newer features may not. Update the server to $appVersion."
                } else {
                    "$versions Everything still works, but newer features may not. Update the app to $serverVersion."
                }
            )

            VersionGap.NONE -> Unit
        }
    }

    return when {
        serverApiVersion == appApiVersion -> null
        serverApiVersion == null || serverApiVersion < appApiVersion ->
            notice(blocking = false, updateServer = true, message = "The server is older than this app, so some things may not work. Update the server.")
        else -> notice(blocking = false, updateServer = false, message = "The server is newer than this app, so some things may not work. Update the app.")
    }
}

/**
 * Sends this app's version with every request, so a server a major version away turns it away (426) instead of
 * reading or writing data it doesn't understand
 * @param onRejected Called when the server turned a request away for it, like after the server was updated while
 * this app was open
 */
fun HttpClientConfig<*>.sendAppVersion(onRejected: () -> Unit) {
    defaultRequest { header(APP_VERSION_HEADER, APP_VERSION) }

    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.UpgradeRequired) onRejected()
        }
    }
}
