package org.mr0btain.ticketman.components.plugins

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.util.*
import org.mr0btain.ticketman.components.proxy.ProxySettings

/** Set on a call whose client address came from a trusted proxy's forwarded headers */
val ForwardedByProxy = AttributeKey<Boolean>("ForwardedByProxy")

/**
 * Takes the client's address and scheme from `X-Forwarded-For` and `X-Forwarded-Proto`, so login limits count each
 * device rather than the proxy, but only on requests whose connection comes from a trusted proxy
 * ([ProxySettings.trusts]). Anyone can send the headers, so from anywhere else they're ignored. Used in place of
 * Ktor's `XForwardedHeaders`, which can't tell who sent them
 */
fun Application.configureProxy(settings: ProxySettings) {
    if (!settings.usesForwardedHeaders) return

    install(createApplicationPlugin("TrustedProxyHeaders") {
        on(CallSetup) { call -> call.useForwardedHeaders(settings) }
    })
}

/**
 * The last address in `X-Forwarded-For` is the one the nearest proxy added, earlier ones came from the client (or
 * proxies further out) and can't be checked, so only the last one is used
 */
internal fun ApplicationCall.useForwardedHeaders(settings: ProxySettings) {
    if (!settings.trusts(request.local.remoteAddress)) return

    val client = request.headers["X-Forwarded-For"].lastValue() ?: return
    val origin = mutableOriginConnectionPoint

    origin.remoteHost = client
    origin.remoteAddress = client
    request.headers["X-Forwarded-Proto"].lastValue()?.lowercase()?.let { origin.scheme = it }
    attributes.put(ForwardedByProxy, true)
}

private fun String?.lastValue(): String? = this?.split(',')?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
