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
import org.mr0btain.ticketman.error.Result
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
        return ((result as? Result.Error)?.error)?.type
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

        assertEquals(NetworkError.SERIALIZATION, (result as Result.Error).error.type)
    }

    @Test
    fun statusCodesKeepTheirMeaning() = runTest {
        assertEquals(NetworkError.SERVER_ERROR, errorFrom(MockEngine { respondError(HttpStatusCode.InternalServerError) }))
        assertEquals(NetworkError.TOO_MANY_REQUESTS, errorFrom(MockEngine { respondError(HttpStatusCode.TooManyRequests) }))
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
