package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.mr0btain.ticketman.components.routing.webClientBundled
import org.mr0btain.ticketman.components.routing.webRoute
import org.mr0btain.ticketman.server.response.SERVER_PING_BODY
import kotlin.test.*

/**
 * The up check, the web client served at /, and CORS for the web dev server only in development
 */
class WebClientTest {
    private val dataDir = createTestDataDir()

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    @Test
    fun pingSaysWhatItIs() = testApplication {
        configureTestServer(dataDir)

        val response = client.get("/api/ping")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(SERVER_PING_BODY, response.bodyAsText())

        // Without the web client built in, / is still the old up check
        assertFalse(webClientBundled())
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun theWebClientIsServedAtTheRoot() = testApplication {
        application {
            this.install(ConditionalHeaders)
            routing {
                get("/api/ping") { call.respondText(SERVER_PING_BODY) }
                webRoute("test-web")
            }
        }
        assertTrue(webClientBundled("test-web"))

        val page = client.get("/")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("<title>TicketMan</title>"))
        assertEquals("no-cache", page.headers[HttpHeaders.CacheControl])

        // A browser that takes gzip gets the copy made at build time
        val script = client.get("/composeApp.js") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals("gzip", script.headers[HttpHeaders.ContentEncoding])

        // Asking again with the ETag it was given costs nothing
        val etag = assertNotNull(page.headers[HttpHeaders.ETag])
        assertEquals(HttpStatusCode.NotModified, client.get("/") { header(HttpHeaders.IfNoneMatch, etag) }.status)

        // API routes still win, and missing ones aren't answered with the page
        assertEquals(SERVER_PING_BODY, client.get("/api/ping").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/api/nothing-here").status)
    }

    private suspend fun ApplicationTestBuilder.preflight() = client.options("/api/auth/login") {
        header(HttpHeaders.Origin, "http://localhost:8080")
        header(HttpHeaders.AccessControlRequestMethod, "POST")
        header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type")
    }

    @Test
    fun theDevServerIsAllowedInDevelopment() = testApplication {
        configureTestServer(dataDir, developmentMode = true)

        val response = preflight()
        assertEquals(HttpStatusCode.OK, response.status)
        // Any host, since CORS_HOSTS isn't set. Logins are bearer tokens, not cookies, so * is fine
        assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun otherSitesArentAllowedOnARealServer() = testApplication {
        configureTestServer(dataDir, developmentMode = false)

        assertNull(preflight().headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
