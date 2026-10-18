package org.mr0btain.ticketman.components.network.transfer

import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.*

/**
 * A picked file goes up as it's read, with its size known so the server can check for room first
 */
class FileBodyTest {
    @Test
    fun fileIsSentWholeWithItsLength() = runTest {
        val bytes = ByteArray(300_000) { (it % 253).toByte() }
        val file = File.createTempFile("backup", ".zip").apply {
            writeBytes(bytes)
            deleteOnExit()
        }

        var sentLength: Long? = null
        var sent = byteArrayOf()
        val client = HttpClient(MockEngine { request ->
            sentLength = request.body.contentLength
            sent = request.body.toByteArray()
            respond("", HttpStatusCode.Accepted)
        })

        client.post("http://localhost/upload") { setBody(fileBody(PlatformFile(file), ContentType.Application.Zip)) }

        assertEquals(bytes.size.toLong(), sentLength)
        assertContentEquals(bytes, sent)
    }
}
