package org.mr0btain.ticketman.components.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun getHttpEngine(): HttpClientEngine {
    return OkHttp.create()
}