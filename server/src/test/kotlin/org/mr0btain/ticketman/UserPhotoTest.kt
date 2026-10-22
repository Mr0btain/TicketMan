package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.CustomerDatabase
import org.mr0btain.ticketman.components.database.ImageDatabase
import org.mr0btain.ticketman.components.database.InventoryDatabase
import org.mr0btain.ticketman.components.database.TicketDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.user.BadgeCode
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.request.UserPhotoRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.BadgeResponse
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.days

/** Users' profile photos and ID card codes */
class UserPhotoTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)
    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val clerk = User(username = "clerk", password = "clerk_pass", accessLevel = AccessLevel.RESTRICTED)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()
        UserDatabase().apply {
            add(admin)
            add(tech)
            add(clerk)
        }
    }

    private suspend fun ApplicationTestBuilder.login(user: User): String =
        json.decodeFromString<AuthResponse>(
            client.post("/api/auth") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(LoginRequest(user.username, user.password)))
            }.bodyAsText()
        ).accessToken!!

    private suspend fun ApplicationTestBuilder.upload(token: String): StoredImage {
        val png = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val response = client.submitFormWithBinaryData(
            url = "/api/image/upload",
            formData = formData {
                append(ImageLimits.UPLOAD_FIELD, png, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"me.png\"")
                    append(HttpHeaders.ContentType, "image/png")
                })
            }
        ) { bearerAuth(token) }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.setPhoto(of: User, photo: StoredImage?, token: String) =
        client.post("/api/user/set/photo/${of.identifier}") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(json.encodeToString(UserPhotoRequest(photo)))
        }

    private suspend fun ApplicationTestBuilder.userOf(user: User, token: String): User =
        json.decodeFromString(client.get("/api/user/get/${user.identifier}") { bearerAuth(token) }.bodyAsText())

    @Test
    fun usersSetTheirOwnPhotoAndAdminsSetAnyones() = testApplication {
        setup()
        val techToken = login(tech)
        val adminToken = login(admin)

        val mine = upload(techToken)
        val saved = setPhoto(tech, mine, techToken)
        assertEquals(HttpStatusCode.OK, saved.status)
        assertEquals(mine.identifier, json.decodeFromString<User>(saved.bodyAsText()).photo?.identifier)
        assertEquals(mine.identifier, userOf(tech, adminToken).photo?.identifier)

        // Not someone else's, not a photo that's already somewhere else, and Restricted users change nothing
        assertEquals(HttpStatusCode.Forbidden, setPhoto(clerk, upload(techToken), techToken).status)
        assertNull(json.decodeFromString<User>(setPhoto(clerk, mine, adminToken).bodyAsText()).photo)
        assertEquals(HttpStatusCode.Forbidden, setPhoto(clerk, null, login(clerk)).status)

        val theirs = upload(adminToken)
        assertEquals(theirs.identifier, json.decodeFromString<User>(setPhoto(clerk, theirs, adminToken).bodyAsText()).photo?.identifier)
        assertEquals(HttpStatusCode.NotFound, setPhoto(User(), null, adminToken).status)
    }

    @Test
    fun aProfilePhotoSurvivesTheSweepUntilItsReplaced() = testApplication {
        setup()
        val token = login(tech)
        val first = upload(token)
        setPhoto(tech, first, token)

        val images = ImageDatabase { listOf(CustomerDatabase(), TicketDatabase(), InventoryDatabase(), UserDatabase()) }
        val later = System.currentTimeMillis() + 2.days.inWholeMilliseconds
        images.sweep(later, 1.days.inWholeMilliseconds)
        assertEquals(HttpStatusCode.OK, client.get("/api/image/${first.identifier}/thumb") { bearerAuth(token) }.status)

        // Replaced, the old one goes with a sweep a day on
        setPhoto(tech, upload(token), token)
        assertEquals(listOf(first.identifier), images.sweep(later + 2.days.inWholeMilliseconds, 1.days.inWholeMilliseconds))
    }

    @Test
    fun onlyAdminsSeeAndReissueIdCardCodes() = testApplication {
        setup()
        val adminToken = login(admin)
        val techToken = login(tech)

        assertEquals(HttpStatusCode.NotFound, client.get("/api/user/badge/${tech.identifier}") { bearerAuth(adminToken) }.status)

        val issued = client.post("/api/user/badge/${tech.identifier}") { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.Created, issued.status)
        val code = json.decodeFromString<BadgeResponse>(issued.bodyAsText()).code
        assertEquals(code, BadgeCode.parse(BadgeCode.qrText(code)))
        assertTrue(userOf(tech, techToken).hasBadge)
        assertEquals(code, json.decodeFromString<BadgeResponse>(client.get("/api/user/badge/${tech.identifier}") { bearerAuth(adminToken) }.bodyAsText()).code)

        // A new one cancels the old card
        val next = json.decodeFromString<BadgeResponse>(client.post("/api/user/badge/${tech.identifier}") { bearerAuth(adminToken) }.bodyAsText()).code
        assertNotEquals(code, next)

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/user/badge/${tech.identifier}") { bearerAuth(techToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/user/badge/${tech.identifier}") { bearerAuth(techToken) }.status)
    }
}
