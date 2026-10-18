package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine

/**
 * The platform's HTTP engine. Where the app checks certificates itself (desktop and Android), [trust] decides
 * which self-made server certificates it accepts
 */
expect fun getHttpEngine(trust: ServerTrust): HttpClientEngine
