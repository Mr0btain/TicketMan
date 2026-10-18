package org.mr0btain.ticketman.components.routing

import io.ktor.http.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*

private object WebClient

/** Whether the server was built with `-PwithWeb`, which puts the web client in its resources under `web/` */
fun webClientBundled(basePackage: String = "web"): Boolean =
    WebClient::class.java.classLoader.getResource("$basePackage/index.html") != null

/**
 * The web client at `/`, so it's on the same address as the API: no CORS, and one certificate for the browser to
 * accept. Specific routes like `/api/...` still win over these files
 */
fun Route.webRoute(basePackage: String = "web") {
    staticResources("/", basePackage, index = "index.html") {
        // The build gzips the big files next to themselves
        preCompressed(CompressedFileType.GZIP)

        // File names stay the same between builds, so browsers check every time. The ETag makes that a 304
        cacheControl { listOf(CacheControl.NoCache(null)) }
        etag(ETagProvider.StrongSha256)
    }
}
