package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * Apple's URL loading system, with certificates checked by [DarwinTrust] so a proxy's private certificate authority
 * is trusted the same way as on desktop and Android
 */
actual fun getHttpEngine(trust: ServerTrust): HttpClientEngine {
    val check = DarwinTrust(trust)

    return Darwin.create {
        handleChallenge { _, _, challenge, completionHandler -> check.handle(challenge, completionHandler) }
    }
}
