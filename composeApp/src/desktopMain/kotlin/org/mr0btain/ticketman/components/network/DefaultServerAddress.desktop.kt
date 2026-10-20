package org.mr0btain.ticketman.components.network

import org.mr0btain.ticketman.components.debug.BuildConfigImpl

/** A debug build looks for `:server:run -Pdevelopment` on this machine, so trying the app needs nothing typed */
actual fun defaultServerAddress(): String = if (BuildConfigImpl().isDebug()) DEV_SERVER_ADDRESS else ""

/** Where `:server:run -Pdevelopment` answers with `PORT` left at its default */
internal const val DEV_SERVER_ADDRESS = "http://localhost:8080"
