package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mr0btain.ticketman.components.database.UserDatabaseImpl
import org.mr0btain.ticketman.components.service.RestoreService
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PagedRequest
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.BackupRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.BackupTicket
import org.mr0btain.ticketman.server.response.PendingRestore
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi

/**
 * Restoring from a backup: staged while running, swapped in on the next start
 */
@OptIn(ExperimentalUuidApi::class)
class RestoreTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val admin = User(username = "boss", password = "boss_pass", accessLevel = AccessLevel.ADMIN)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.token(): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(admin.username, admin.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.restore(bytes: ByteArray, token: String) =
        client.post("/api/admin/restore") {
            bearerAuth(token)
            contentType(ContentType.Application.Zip)
            setBody(bytes)
        }

    private suspend fun ApplicationTestBuilder.customerNames(token: String): List<String> =
        json.decodeFromString<List<Customer>>(
            post("/api/data/customer/fetch", json.encodeToString(PagedRequest(0, 50)), token).bodyAsText()
        ).map { it.name }

    /** Like a server stopping: H2 lets go of its files */
    private fun shutDownDatabase() {
        try {
            transaction { exec("SHUTDOWN") }
        } catch (_: Exception) {
            // Committing after SHUTDOWN fails, the database is closed either way
        }
    }

    private fun png(): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    @Test
    fun backupIsRestoredOnTheNextStartWithItsPhotos() {
        lateinit var backup: ByteArray
        lateinit var photo: StoredImage

        // First run: a customer with a photo, a backup, then a change made after it, then the restore
        testApplication {
            configureTestServer(dataDir)
            startApplication()
            UserDatabaseImpl().add(admin)
            val token = token()

            val customer = Customer(name = "Before Backup")
            post("/api/data/customer/add", json.encodeToString(customer), token)

            val uploaded = client.submitFormWithBinaryData(
                url = "/api/image/upload",
                formData = formData {
                    append(ImageLimits.UPLOAD_FIELD, png(), Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"a.png\"")
                    })
                }
            ) { bearerAuth(token) }
            assertEquals(HttpStatusCode.Created, uploaded.status, uploaded.bodyAsText())
            photo = json.decodeFromString(uploaded.bodyAsText())
            post("/api/data/customer/add/note/${customer.identifier}", json.encodeToString(Note(message = "Photo", images = listOf(photo))), token)

            val ticket = json.decodeFromString<BackupTicket>(
                post("/api/admin/backup", json.encodeToString(BackupRequest(includeImages = true)), token).bodyAsText()
            )
            backup = client.get(ticket.downloadPath).readRawBytes()

            post("/api/data/customer/add", json.encodeToString(Customer(name = "After Backup")), token)
            assertEquals(setOf("before backup", "after backup"), customerNames(token).map { it.lowercase() }.toSet())

            val staged = restore(backup, token)
            assertEquals(HttpStatusCode.Accepted, staged.status, staged.bodyAsText())
            val manifest = json.decodeFromString<PendingRestore>(staged.bodyAsText()).manifest
            assertTrue(manifest.includesImages)
            assertEquals(1, manifest.imageCount)

            // Nothing changes until the restart
            assertEquals(HttpStatusCode.OK, client.get("/api/admin/restore") { bearerAuth(token) }.status)
            assertEquals(2, customerNames(token).size)
        }
        shutDownDatabase()

        // Second run: the backup's data, the old data kept aside
        testApplication {
            configureTestServer(dataDir)
            startApplication()
            val token = token()

            assertEquals(listOf("before backup"), customerNames(token).map { it.lowercase() })
            assertEquals(HttpStatusCode.OK, client.get("/api/image/${photo.identifier}/thumb") { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.NoContent, client.get("/api/admin/restore") { bearerAuth(token) }.status)

            val serverDir = File(dataDir.toFile(), "server")
            assertEquals(1, serverDir.listFiles().orEmpty().count { it.name.startsWith(RestoreService.SET_ASIDE_PREFIX) })
        }
    }

    @Test
    fun archivesThatArentBackupsOrReachOutsideAreRefused() = testApplication {
        configureTestServer(dataDir)
        startApplication()
        UserDatabaseImpl().add(admin)
        val token = token()
        val manifest = json.encodeToString(
            BackupManifest(serverVersion = "1", createdAt = 0, includesImages = false, imageCount = 0, imageBytes = 0)
        ).encodeToByteArray()

        assertEquals(HttpStatusCode.BadRequest, restore("not a zip".encodeToByteArray(), token).status)
        assertEquals(HttpStatusCode.BadRequest, restore(zip("readme.txt" to byteArrayOf(1)), token).status)
        assertEquals(HttpStatusCode.BadRequest, restore(zip(BackupManifest.FILE_NAME to manifest), token).status)

        val escaping = restore(
            zip(
                BackupManifest.FILE_NAME to manifest,
                BackupManifest.DATABASE_FILE to zip(),
                "images/../../evil.sh" to byteArrayOf(1)
            ),
            token
        )
        assertEquals(HttpStatusCode.BadRequest, escaping.status)
        assertContains(escaping.bodyAsText(), "unexpected file")

        // Nothing was left waiting or lying around
        assertEquals(HttpStatusCode.NoContent, client.get("/api/admin/restore") { bearerAuth(token) }.status)
        assertTrue(File(dataDir.toFile(), "server/restore").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun waitingRestoreCanBeCancelled() = testApplication {
        configureTestServer(dataDir)
        startApplication()
        UserDatabaseImpl().add(admin)
        val token = token()

        val ticket = json.decodeFromString<BackupTicket>(
            post("/api/admin/backup", json.encodeToString(BackupRequest(includeImages = false)), token).bodyAsText()
        )
        assertEquals(HttpStatusCode.Accepted, restore(client.get(ticket.downloadPath).readRawBytes(), token).status)

        client.delete("/api/admin/restore") { bearerAuth(token) }
        assertEquals(HttpStatusCode.NoContent, client.get("/api/admin/restore") { bearerAuth(token) }.status)
    }
}
