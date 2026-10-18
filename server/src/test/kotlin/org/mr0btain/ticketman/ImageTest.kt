package org.mr0btain.ticketman

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.components.database.ImageDatabase
import org.mr0btain.ticketman.components.database.UserDatabase
import org.mr0btain.ticketman.components.image.ImageStore
import org.mr0btain.ticketman.components.service.ImageService
import org.mr0btain.ticketman.data.customer.Customer
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.user.AccessLevel
import org.mr0btain.ticketman.data.inventory.Inventory
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.server.request.BackupRequest
import org.mr0btain.ticketman.server.request.LoginRequest
import org.mr0btain.ticketman.server.response.AuthResponse
import org.mr0btain.ticketman.server.response.BackupManifest
import org.mr0btain.ticketman.server.response.BackupTicket
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Photo upload, serving, linking onto notes and the sweeper
 */
@OptIn(ExperimentalUuidApi::class)
class ImageTest {
    private val dataDir = createTestDataDir()
    private val json = Json { ignoreUnknownKeys = true }

    private val tech = User(username = "tech", password = "tech_pass", accessLevel = AccessLevel.NORMAL)
    private val viewer = User(username = "viewer", password = "viewer_pass", accessLevel = AccessLevel.RESTRICTED)
    private val owner = User(username = "owner", password = "owner_pass", accessLevel = AccessLevel.ADMIN)

    @AfterTest
    fun cleanup() = cleanupTestDataDir(dataDir)

    private suspend fun ApplicationTestBuilder.setup() {
        configureTestServer(dataDir)
        startApplication()

        UserDatabase().apply {
            add(tech)
            add(viewer)
        }
    }

    private fun imageService() = ImageService(ImageDatabase(), ImageStore(File(dataDir.toFile(), "server/images")))

    private suspend fun ApplicationTestBuilder.token(user: User): String {
        val response = client.post("/api/auth") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest(user.username, user.password)))
        }

        return json.decodeFromString<AuthResponse>(response.bodyAsText()).accessToken!!
    }

    private suspend fun ApplicationTestBuilder.upload(token: String, bytes: ByteArray, name: String = "photo.png") =
        client.submitFormWithBinaryData(
            url = "/api/image/upload",
            formData = formData {
                append(ImageLimits.DESCRIPTION_FIELD, "Cracked screen")
                append(ImageLimits.UPLOAD_FIELD, bytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                    append(HttpHeaders.ContentType, "image/png")
                })
            }
        ) { bearerAuth(token) }

    private suspend fun ApplicationTestBuilder.uploadImage(token: String, width: Int = 400, height: Int = 300): StoredImage {
        val response = upload(token, png(width, height))
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.post(path: String, body: String, token: String) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.notes(customer: Uuid, token: String): List<Note> =
        json.decodeFromString(post("/api/data/customer/get/notes/$customer", "\"$customer\"", token).bodyAsText())

    private suspend fun ApplicationTestBuilder.newCustomer(token: String): Uuid {
        val customer = Customer(name = "Jane Doe")
        assertEquals(HttpStatusCode.Accepted, post("/api/data/customer/add", json.encodeToString(customer), token).status)
        return customer.identifier
    }

    /** A see through PNG, so flattening onto white is exercised too */
    private fun png(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            color = Color(200, 40, 40, 128)
            fillRect(0, 0, width / 2, height)
            dispose()
        }

        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    // ------------------------------------------ Uploading and serving ------------------------------------------

    @Test
    fun uploadMakesEveryVariantCappedAtItsSize() = testApplication {
        setup()
        val token = token(tech)

        val image = uploadImage(token, width = 3000, height = 2000)
        assertEquals(ImageLimits.MAX_EDGE, image.width)
        assertEquals(1707, image.height)
        assertEquals("Cracked screen", image.description)
        assertTrue(image.sizeBytes > 0)

        ImageVariant.entries.forEach { variant ->
            val response = client.get("/api/image/${image.identifier}/${variant.path}") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Image.JPEG, response.contentType()?.withoutParameters())
            assertTrue(response.headers[HttpHeaders.CacheControl].orEmpty().contains("immutable"))

            val decoded = ImageIO.read(ByteArrayInputStream(response.readRawBytes()))
            assertEquals(variant.maxEdge, maxOf(decoded.width, decoded.height), "$variant")
            // Transparent areas came out white, not black
            assertTrue(Color(decoded.getRGB(decoded.width - 1, 0)).red > 240, "$variant")
        }
    }

    @Test
    fun smallPhotosAreNotEnlarged() = testApplication {
        setup()
        val token = token(tech)
        val image = uploadImage(token, width = 200, height = 100)

        val thumb = client.get("/api/image/${image.identifier}/thumb") { bearerAuth(token) }
        val decoded = ImageIO.read(ByteArrayInputStream(thumb.readRawBytes()))

        assertEquals(200, image.width)
        assertEquals(200, decoded.width)
        assertEquals(100, decoded.height)
    }

    @Test
    fun repeatRequestWithEtagGetsNotModified() = testApplication {
        setup()
        val token = token(tech)
        val image = uploadImage(token)

        val first = client.get("/api/image/${image.identifier}/display") { bearerAuth(token) }
        val etag = assertNotNull(first.headers[HttpHeaders.ETag])

        val second = client.get("/api/image/${image.identifier}/display") {
            bearerAuth(token)
            header(HttpHeaders.IfNoneMatch, etag)
        }
        assertEquals(HttpStatusCode.NotModified, second.status)
        assertEquals(0, second.readRawBytes().size)
    }

    @Test
    fun rangeRequestsGetPartialContent() = testApplication {
        setup()
        val token = token(tech)
        val image = uploadImage(token)

        val response = client.get("/api/image/${image.identifier}/original") {
            bearerAuth(token)
            header(HttpHeaders.Range, "bytes=0-9")
        }
        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals(10, response.readRawBytes().size)
    }

    @Test
    fun nonImagesAndOversizedFilesAreRefused() = testApplication {
        setup()
        val token = token(tech)

        assertEquals(HttpStatusCode.UnsupportedMediaType, upload(token, "not a photo".encodeToByteArray()).status)
        assertEquals(
            HttpStatusCode.PayloadTooLarge,
            upload(token, ByteArray((ImageLimits.MAX_UPLOAD_BYTES + 1).toInt())).status
        )
        // Nothing was left behind in the temp folder
        assertTrue(File(dataDir.toFile(), "server/images/.tmp").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun oversizedUploadWithNoLengthUpFrontIsRefusedAndCleanedUp() = testApplication {
        setup()
        val token = token(tech)
        val bytes = ByteArray((ImageLimits.MAX_UPLOAD_BYTES + 2 * 1024 * 1024).toInt())

        // A streamed body has no Content-Length, so the size is only found while reading
        val response = client.post("/api/image/upload") {
            bearerAuth(token)
            setBody(MultiPartFormDataContent(formData {
                append(ImageLimits.UPLOAD_FIELD, ChannelProvider { ByteReadChannel(bytes) }, Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"huge.jpg\"")
                })
            }))
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertTrue(File(dataDir.toFile(), "server/images/.tmp").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun restrictedUsersCanViewButNotUpload() = testApplication {
        setup()
        val image = uploadImage(token(tech))
        val viewerToken = token(viewer)

        assertEquals(HttpStatusCode.Forbidden.value / 100, upload(viewerToken, png(10, 10)).status.value / 100)
        assertEquals(HttpStatusCode.OK, client.get("/api/image/${image.identifier}/thumb") { bearerAuth(viewerToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/image/${image.identifier}/thumb").status)
    }

    @Test
    fun unknownImageOrVariantIsNotFound() = testApplication {
        setup()
        val token = token(tech)
        val image = uploadImage(token)

        assertEquals(HttpStatusCode.NotFound, client.get("/api/image/${Uuid.generateV7()}/thumb") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/image/${image.identifier}/huge") { bearerAuth(token) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/image/../../etc/thumb") { bearerAuth(token) }.status)
    }

    // ------------------------------------------ Linking onto notes ------------------------------------------

    @Test
    fun noteOnlyLinksImagesThatWereUploaded() = testApplication {
        setup()
        val token = token(tech)
        val customer = newCustomer(token)
        val image = uploadImage(token)

        val note = Note(message = "Back glass cracked", images = listOf(image.copy(description = "Back"), StoredImage()))
        assertEquals(HttpStatusCode.Created, post("/api/data/customer/add/note/$customer", json.encodeToString(note), token).status)

        val saved = notes(customer, token).single()
        assertEquals(listOf(image.identifier), saved.images.map { it.identifier })
        assertEquals("Back", saved.images.single().description)

        // A photo already on one note can't be pulled onto another
        val other = Note(message = "Stolen photo", images = listOf(image))
        post("/api/data/customer/add/note/$customer", json.encodeToString(other), token)
        assertTrue(notes(customer, token).first { it.identifier == other.identifier }.images.isEmpty())
    }

    @Test
    fun updatingANoteSwapsItsImagesAndDeletesTheDroppedOnes() = testApplication {
        setup()
        val token = token(tech)
        val customer = newCustomer(token)
        val first = uploadImage(token)
        val second = uploadImage(token)

        val note = Note(message = "Before", images = listOf(first))
        post("/api/data/customer/add/note/$customer", json.encodeToString(note), token)

        val updated = note.copy(message = "After", images = listOf(second))
        assertEquals(
            HttpStatusCode.Accepted,
            post("/api/data/customer/update/note/${note.identifier}", json.encodeToString(updated), token).status
        )

        val saved = notes(customer, token).single()
        assertEquals("After", saved.message)
        assertEquals(listOf(second.identifier), saved.images.map { it.identifier })

        // The dropped photo can't be fetched any more, but its files stay until the sweep
        assertEquals(HttpStatusCode.NotFound, client.get("/api/image/${first.identifier}/thumb") { bearerAuth(token) }.status)
        assertTrue(File(dataDir.toFile(), "server/images").walkTopDown().any { it.name == first.identifier.toString() })
    }

    @Test
    fun removingANoteDeletesItsPhotos() = testApplication {
        setup()
        val token = token(tech)
        val customer = newCustomer(token)
        val photo = uploadImage(token)

        val note = Note(message = "With a photo", images = listOf(photo))
        post("/api/data/customer/add/note/$customer", json.encodeToString(note), token)

        val removed = client.delete("/api/data/customer/remove/note/${note.identifier}") { bearerAuth(token) }
        assertEquals(HttpStatusCode.Accepted, removed.status)

        assertTrue(notes(customer, token).isEmpty())
        assertEquals(HttpStatusCode.NotFound, client.get("/api/image/${photo.identifier}/thumb") { bearerAuth(token) }.status)

        // Removing it again finds nothing
        assertEquals(
            HttpStatusCode.NotFound,
            client.delete("/api/data/customer/remove/note/${note.identifier}") { bearerAuth(token) }.status
        )
    }

    // ------------------------------------------ Inventory photos ------------------------------------------

    @Test
    fun inventoryPhotosAreSetAsAWholeList() = testApplication {
        setup()
        val token = token(tech)
        val item = Inventory(name = "iPhone 13 screen", stock = 4)
        post("/api/data/inventory/add", json.encodeToString(item), token)

        val front = uploadImage(token)
        val back = uploadImage(token)

        assertEquals(
            HttpStatusCode.Accepted,
            post("/api/data/inventory/set/images/${item.identifier}", json.encodeToString(listOf(front, back)), token).status
        )
        suspend fun images(): List<StoredImage> =
            json.decodeFromString(client.get("/api/data/inventory/get/images/${item.identifier}") { bearerAuth(token) }.bodyAsText())

        assertEquals(setOf(front.identifier, back.identifier), images().map { it.identifier }.toSet())

        post("/api/data/inventory/set/images/${item.identifier}", json.encodeToString(listOf(back)), token)
        assertEquals(listOf(back.identifier), images().map { it.identifier })
        assertEquals(HttpStatusCode.NotFound, client.get("/api/image/${front.identifier}/thumb") { bearerAuth(token) }.status)

        assertEquals(
            HttpStatusCode.NotFound,
            post("/api/data/inventory/set/images/${Uuid.generateV7()}", "[]", token).status
        )
    }

    // ------------------------------------------ Backups ------------------------------------------

    @Test
    fun backupCarriesLivePhotosButNotDeletedOnes() = testApplication {
        setup()
        UserDatabase().add(owner)
        val token = token(tech)
        val customer = newCustomer(token)
        val kept = uploadImage(token)
        val dropped = uploadImage(token)

        val note = Note(message = "Photos", images = listOf(kept, dropped))
        post("/api/data/customer/add/note/$customer", json.encodeToString(note), token)
        post("/api/data/customer/update/note/${note.identifier}", json.encodeToString(note.copy(images = listOf(kept))), token)

        val ticket = json.decodeFromString<BackupTicket>(
            post("/api/admin/backup", json.encodeToString(BackupRequest(includeImages = true)), token(owner)).bodyAsText()
        )
        val names = ZipInputStream(ByteArrayInputStream(client.get(ticket.downloadPath).readRawBytes())).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }

        val keptFolder = "images/${kept.identifier.toString().take(2)}/${kept.identifier}"
        assertTrue(ImageVariant.entries.all { "$keptFolder/${it.path}.jpg" in names }, names.toString())
        assertTrue(names.none { dropped.identifier.toString() in it })
        assertTrue(BackupManifest.DATABASE_FILE in names)
    }

    // ------------------------------------------ Sweeping ------------------------------------------

    @Test
    fun sweeperRemovesDeletedAndAbandonedPhotosAfterADay() = testApplication {
        setup()
        val token = token(tech)
        val customer = newCustomer(token)
        val kept = uploadImage(token)
        val abandoned = uploadImage(token)
        val removed = uploadImage(token)

        val note = Note(message = "Photos", images = listOf(kept, removed))
        post("/api/data/customer/add/note/$customer", json.encodeToString(note), token)
        post("/api/data/customer/update/note/${note.identifier}", json.encodeToString(note.copy(images = listOf(kept))), token)

        val service = imageService()

        // Nothing goes before the grace period is up
        assertEquals(0, service.sweep())
        listOf(kept, abandoned, removed).forEach { assertTrue(service.store.exists(it.identifier)) }

        assertEquals(2, service.sweep(System.currentTimeMillis() + ImageService.GRACE_PERIOD.inWholeMilliseconds + 1.hours.inWholeMilliseconds))
        assertTrue(service.store.exists(kept.identifier))
        assertFalse(service.store.exists(abandoned.identifier))
        assertFalse(service.store.exists(removed.identifier))
        assertEquals(listOf(kept.identifier), notes(customer, token).single().images.map { it.identifier })
    }

    @Test
    fun removingTheParentEventuallyRemovesItsPhotos() = testApplication {
        setup()
        val token = token(tech)
        val customer = newCustomer(token)
        val image = uploadImage(token)

        post("/api/data/customer/add/note/$customer", json.encodeToString(Note(message = "Photo", images = listOf(image))), token)
        client.delete("/api/data/customer/remove/$customer") { bearerAuth(token) }

        val service = imageService()
        val now = System.currentTimeMillis()

        // First sweep notices nothing points at it, the one a day later removes it
        service.sweep(now)
        assertTrue(service.store.exists(image.identifier))
        service.sweep(now + ImageService.GRACE_PERIOD.inWholeMilliseconds + 1)
        assertFalse(service.store.exists(image.identifier))
    }

    @Test
    fun orphanedFoldersAndStaleTempFilesAreCleanedUp() = testApplication {
        setup()
        val service = imageService()
        val orphan = Uuid.generateV7()

        service.store.folderFor(orphan).apply { mkdirs(); File(this, "thumb.jpg").writeText("x") }
        val temp = File(service.store.tempFolder(), "upload-left-behind.bin").apply { writeText("x") }

        // Too new to be sure it isn't an upload in progress
        service.sweep()
        assertTrue(service.store.folderFor(orphan).exists())

        service.sweep(System.currentTimeMillis() + ImageService.ORPHAN_AGE.inWholeMilliseconds + 1)
        assertFalse(service.store.folderFor(orphan).exists())
        assertFalse(temp.exists())
    }
}
