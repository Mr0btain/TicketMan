package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

actual fun getHttpEngine(trust: ServerTrust): HttpClientEngine = pinnedHttpEngine(trust)

/** OkHttp checking certificates against [system] plus what [trust] holds. Tests hand in their own [system] */
internal fun pinnedHttpEngine(trust: ServerTrust, system: X509TrustManager = systemTrustManager()): HttpClientEngine = OkHttp.create {
    config {
        val trustManager = PinningTrustManager(trust, system)
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }

        sslSocketFactory(context.socketFactory, trustManager)
        hostnameVerifier(PinningHostnameVerifier(trust))
    }
}
