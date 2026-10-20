package org.mr0btain.ticketman.components.network

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import kotlin.test.*

/**
 * Checks a failed call says what actually went wrong instead of always "Couldn't reach the server"
 */
class NetworkErrorTest {
    @Serializable
    private data class Body(val name: String)

    private fun client(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json() }
        install(HttpTimeout) { requestTimeoutMillis = 50 }
    }

    private suspend fun errorFrom(engine: MockEngine): NetworkError? {
        val result = handleActionHttpResponse("Test", networkRequest("Test") { client(engine).get("http://server/") })
        return ((result as? TmResult.Error)?.error)?.type
    }

    @Test
    fun timeoutIsATimeout() = runTest {
        val engine = MockEngine { delay(10_000); respondOk() }
        assertEquals(NetworkError.REQUEST_TIMEOUT, errorFrom(engine))
    }

    @Test
    fun noConnectionIsNoConnection() = runTest {
        val engine = MockEngine { throw IOException("Connection refused") }
        assertEquals(NetworkError.NO_INTERNET, errorFrom(engine))
    }

    @Test
    fun unreadableBodyIsASerializationError() = runTest {
        val engine = MockEngine {
            respond("""{"nope": 1}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val result = handleDataHttpResponse<Body>("Test", networkRequest("Test") { client(engine).get("http://server/") })

        assertEquals(NetworkError.SERIALIZATION, (result as TmResult.Error).error.type)
    }

    @Test
    fun statusCodesKeepTheirMeaning() = runTest {
        assertEquals(NetworkError.SERVER_ERROR, errorFrom(MockEngine { respondError(HttpStatusCode.InternalServerError) }))
        assertEquals(NetworkError.TOO_MANY_REQUESTS, errorFrom(MockEngine { respondError(HttpStatusCode.TooManyRequests) }))
    }

    private suspend fun serverErrorFrom(engine: MockEngine): ServerError =
        (handleActionHttpResponse("Test", networkRequest("Test") { client(engine).get("http://server/") }) as TmResult.Error).error

    // What nginx sends for a body over client_max_body_size
    private val nginxTooLarge = "<html>\r\n<head><title>413 Request Entity Too Large</title></head>\r\n<body>\r\n" +
        "<center><h1>413 Request Entity Too Large</h1></center>\r\n<hr><center>nginx</center>\r\n</body>\r\n</html>\r\n"

    @Test
    fun aProxysTooLargeIsTheProxys() = runTest {
        val html = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
        val nginx = serverErrorFrom(MockEngine { respond(nginxTooLarge, HttpStatusCode.PayloadTooLarge, html) })
        assertEquals(NetworkError.PROXY_TOO_LARGE, nginx.type)
        assertEquals(NetworkError.PROXY_TOO_LARGE.message, nginx.message, "Never the page itself")

        // Caddy's request_body limit sends nothing
        assertEquals(NetworkError.PROXY_TOO_LARGE, serverErrorFrom(MockEngine { respond("", HttpStatusCode.PayloadTooLarge) }).type)
    }

    @Test
    fun theServersTooLargeKeepsItsReason() = runTest {
        val error = serverErrorFrom(MockEngine { respond("That photo is too big", HttpStatusCode.PayloadTooLarge) })
        assertEquals(NetworkError.PAYLOAD_TOO_LARGE, error.type)
        assertEquals("That photo is too big", error.message)
    }

    @Test
    fun aProxyWithNoServerBehindItSaysSo() = runTest {
        listOf(HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout).forEach { status ->
            val error = serverErrorFrom(MockEngine { respond("<html><body>$status</body></html>", status) })
            assertEquals(NetworkError.PROXY_SERVER_DOWN, error.type, "$status")
            assertEquals(NetworkError.PROXY_SERVER_DOWN.message, error.message)
        }
    }

    @Test
    fun cancellationIsLetThrough() {
        assertFailsWith<CancellationException> {
            networkRequest("Test") { throw CancellationException("Left the screen") }
        }
    }

    @Test
    fun anythingElseIsUnknown() {
        assertEquals(NetworkError.UNKNOWN, networkErrorFor(IllegalStateException()))
    }
}
