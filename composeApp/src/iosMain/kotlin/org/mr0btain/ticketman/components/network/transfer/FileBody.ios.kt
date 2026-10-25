package org.mr0btain.ticketman.components.network.transfer

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.io.buffered

private const val CHUNK_BYTES = 64 * 1024

// Streamed from disk, so any size goes
actual val fileBodyLimitBytes: Long? = null

actual suspend fun fileBody(file: PlatformFile, contentType: ContentType): OutgoingContent =
    object : OutgoingContent.WriteChannelContent() {
        override val contentType: ContentType = contentType
        override val contentLength: Long = file.size()

        override suspend fun writeTo(channel: ByteWriteChannel) {
            file.source().buffered().use { source ->
                val buffer = ByteArray(CHUNK_BYTES)

                while (true) {
                    val read = source.readAtMostTo(buffer, 0, buffer.size)
                    if (read < 0) break

                    channel.writeFully(buffer, 0, read)
                }
            }
        }
    }
