package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine

/**
 * The platform's HTTP engine. Where the app checks certificates itself (desktop and Android), [trust] decides
 * which private certificates it accepts on top of the system's
 */
expect fun getHttpEngine(trust: ServerTrust): HttpClientEngine
