package org.mr0btain.ticketman.components.network.image

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.TmResult
import org.mr0btain.ticketman.server.NetworkError
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Checks photos go up as multipart to the server's upload route and failures come back readable
 */
@OptIn(ExperimentalUuidApi::class)
class ImageRemoteRepositoryTest {
    private val uploaded = StoredImage(width = 2560, height = 1920, sizeBytes = 400_000)
    private var bodies = mutableListOf<String>()
    private var respondWith: HttpStatusCode = HttpStatusCode.Created

    private val engine = MockEngine { request ->
        bodies += "${request.method.value} ${request.url.encodedPath} ${request.body.contentType?.withoutParameters()}\n" +
            request.body.toByteArray().decodeToString()

        if (respondWith == HttpStatusCode.Created) {
            respond(Json.encodeToString(uploaded), respondWith, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        } else {
            respond("Not a photo", respondWith)
        }
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout)
    }

    private val images = ImageRemoteRepository(client).apply { serverUrl = "http://localhost/" }

    @Test
    fun uploadSendsMultipartAndReturnsThePendingImage() = runTest {
        val result = images.upload("jpeg bytes".encodeToByteArray(), "IMG \"1\".jpg", description = "Back glass")

        assertIs<TmResult.Success<StoredImage>>(result)
        assertEquals(uploaded, result.data)

        val body = bodies.single()
        assertTrue(body.startsWith("POST /api/image/upload multipart/form-data"), body)
        assertContains(body, "name=\"${ImageLimits.UPLOAD_FIELD}\"")
        assertContains(body, "name=\"${ImageLimits.DESCRIPTION_FIELD}\"")
        assertContains(body, "Back glass")
        assertContains(body, "jpeg bytes")
        // Quotes in the name can't break the header
        assertContains(body, "filename=\"IMG 1.jpg\"")
    }

    @Test
    fun refusalsCarryTheServersReason() = runTest {
        respondWith = HttpStatusCode.UnsupportedMediaType

        val result = images.upload(byteArrayOf(1, 2, 3), "notes.txt")

        assertIs<TmResult.Error<*>>(result)
        assertEquals("Not a photo", (result as TmResult.Error).error.message)
    }

    @Test
    fun oversizedPhotosAreRefusedBeforeSending() = runTest {
        val result = images.upload(ByteArray((ImageLimits.MAX_UPLOAD_BYTES + 1).toInt()), "huge.jpg")

        assertIs<TmResult.Error<*>>(result)
        assertEquals(NetworkError.PAYLOAD_TOO_LARGE, (result as TmResult.Error).error.type)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun urlsPointAtTheVariantAndNeedAServer() {
        val identifier = Uuid.generateV7()

        assertEquals("http://localhost/api/image/$identifier/thumb", images.urlFor(identifier, ImageVariant.THUMB))
        assertNull(ImageRemoteRepository(client).urlFor(identifier, ImageVariant.THUMB))
    }
}
