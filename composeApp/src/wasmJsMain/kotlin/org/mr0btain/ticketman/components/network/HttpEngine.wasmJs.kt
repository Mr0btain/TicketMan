package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js

// The browser checks certificates itself, and asks the person when it doesn't trust one
actual fun getHttpEngine(trust: ServerTrust): HttpClientEngine = Js.create()
