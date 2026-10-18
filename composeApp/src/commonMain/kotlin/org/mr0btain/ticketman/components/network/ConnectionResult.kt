package org.mr0btain.ticketman.components.network

/**
 * Outcome of pointing the [RemoteHandler] at a server url
 */
enum class ConnectionResult {
    /** Url was blank or the server couldn't be reached */
    UNREACHABLE,

    /** The server answered with a certificate this device doesn't trust yet, see [ServerTrust.refused] */
    UNTRUSTED,

    /** Server is reachable but logging in with the stored credentials failed */
    CONNECTED,

    /** Server is reachable and we logged in with the stored credentials */
    LOGGED_IN
}
