package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.plugins.BodyLimits
import org.mr0btain.ticketman.components.routing.TOO_LARGE_MESSAGE
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import kotlin.test.*

/**
 * Bodies over the limit are turned away rather than read into memory. The in-process test engine isn't Netty, so a
 * change here also wants checking against a real `:server:run`
 */
class BodyLimitTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.sized(path: String, bytes: Long, type: ContentType) =
        client.post(path) {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override val contentLength = bytes
                override val contentType = type
                override fun readFrom() = ByteReadChannel(ByteArray(bytes.toInt()) { 'a'.code.toByte() })
            })
        }

    // Says nothing up front about how big it is, like a chunked request
    private suspend fun ApplicationTestBuilder.unsized(path: String, bytes: Long, type: ContentType) =
        client.post(path) {
            setBody(object : OutgoingContent.ReadChannelContent() {
                override val contentType = type
                override fun readFrom() = ByteReadChannel(ByteArray(bytes.toInt()) { 'a'.code.toByte() })
            })
        }

    @Test
    fun eachKindOfRouteHasItsLimit() {
        assertEquals(BodyLimits.PUBLIC_FORM, BodyLimits.forPath("/status"))
        assertEquals(BodyLimits.PUBLIC_FORM, BodyLimits.forPath("/status/book"))
        assertEquals(BodyLimits.PUBLIC_FORM, BodyLimits.forPath("/api/auth"))
        assertEquals(BodyLimits.PUBLIC_FORM, BodyLimits.forPath("/api/auth/pin"))
        assertEquals(BodyLimits.PUBLIC_FORM, BodyLimits.forPath("/api/timeclock/punch"))
        assertEquals(BodyLimits.WEBHOOK, BodyLimits.forPath("/status/payments/stripe/webhook"))
        assertEquals(BodyLimits.IMPORT, BodyLimits.forPath("/api/admin/import/customers"))
        assertEquals(BodyLimits.DEFAULT, BodyLimits.forPath("/api/data/ticket/add"))
        assertNull(BodyLimits.forPath("/api/image/upload"))
        assertNull(BodyLimits.forPath("/api/admin/restore"))

        // Only those exact paths go without a limit
        assertEquals(BodyLimits.DEFAULT, BodyLimits.forPath("/api/image/upload/"))
        assertEquals(BodyLimits.DEFAULT, BodyLimits.forPath("/statusanything"))
    }

    @Test
    fun aBodyThatSaysItsTooBigIsRefused() = testApplication {
        configureTestServer(dataDir)
        startApplication()

        val over = BodyLimits.PUBLIC_FORM + 1
        listOf(
            sized("/status", over, ContentType.Application.FormUrlEncoded),
            sized("/api/auth", over, ContentType.Application.Json),
            sized("/status/payments/stripe/webhook", BodyLimits.WEBHOOK + 1, ContentType.Application.Json)
        ).forEach { response ->
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals(TOO_LARGE_MESSAGE, response.bodyAsText())
        }
    }

    @Test
    fun aBodyThatDoesntSayIsCutOff() = testApplication {
        configureTestServer(dataDir)
        startApplication()

        val over = BodyLimits.PUBLIC_FORM + 1
        assertEquals(HttpStatusCode.PayloadTooLarge, unsized("/status", over, ContentType.Application.FormUrlEncoded).status)
        assertEquals(HttpStatusCode.PayloadTooLarge, unsized("/api/auth", over, ContentType.Application.Json).status)
        assertEquals(HttpStatusCode.PayloadTooLarge, unsized("/status/payments/stripe/webhook", BodyLimits.WEBHOOK + 1, ContentType.Application.Json).status)

        // Under it, it reaches the route as usual
        assertEquals(HttpStatusCode.NotFound, unsized("/status", 1_000, ContentType.Application.FormUrlEncoded).status)
    }

    @Test
    fun ordinaryRequestsAndBigImportsGetThrough() = testApplication {
        configureTestServer(dataDir)
        startApplication()
        val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
        UserDatabase().add(admin)

        val login = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(admin.username, admin.password)))
        }
        assertEquals(HttpStatusCode.Accepted, login.status)
        val token = json.decodeFromString<AuthResponse>(login.bodyAsText()).accessToken!!

        // Over the default limit, in the import's own
        val rows = (1..10_000).joinToString(",", "[", "]") { """{"name":"Customer $it ${"x".repeat(600)}"}""" }
        assertTrue(rows.length > BodyLimits.DEFAULT)
        val imported = client.post("/api/admin/import/customers?dryRun=true") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(rows)
        }
        assertEquals(HttpStatusCode.OK, imported.status, imported.bodyAsText().take(200))
    }
}
