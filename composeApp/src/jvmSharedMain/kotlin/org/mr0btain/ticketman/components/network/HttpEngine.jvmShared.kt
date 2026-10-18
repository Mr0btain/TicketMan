package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import javax.net.ssl.SSLContext

actual fun getHttpEngine(trust: ServerTrust): HttpClientEngine = OkHttp.create {
    config {
        val trustManager = PinningTrustManager(trust)
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }

        sslSocketFactory(context.socketFactory, trustManager)
        hostnameVerifier(PinningHostnameVerifier(trust))
    }
}
