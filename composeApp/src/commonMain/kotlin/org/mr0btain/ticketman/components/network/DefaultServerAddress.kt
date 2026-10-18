package org.mr0btain.ticketman.components.network

/**
 * Where to look for a server before one is saved. On web that's the address the page came from, since the server
 * serves the web client itself. Blank elsewhere
 */
expect fun defaultServerAddress(): String
