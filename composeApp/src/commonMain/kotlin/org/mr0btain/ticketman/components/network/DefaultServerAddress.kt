package org.mr0btain.ticketman.components.network

/**
 * Where to look for a server before one is saved. On web that's the address the page came from, since the server
 * serves the web client itself. On a desktop debug build it's a development server on this machine
 * (`http://localhost:8080`). Blank elsewhere
 */
expect fun defaultServerAddress(): String
