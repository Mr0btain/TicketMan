package org.mr0btain.ticketman.components.service

import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val COPY_BUFFER_BYTES = 256 * 1024

/**
 * Streams a request body into [target] a chunk at a time, stopping once it passes [limit]
 * @return Bytes written, or null if it was too big (what was written is left for the caller to delete)
 */
suspend fun ByteReadChannel.copyToFile(target: File, limit: Long): Long? = withContext(Dispatchers.IO) {
    var total = 0L

    toInputStream().use { input ->
        target.outputStream().buffered(COPY_BUFFER_BYTES).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)

            while (true) {
                val read = input.read(buffer)
                if (read < 0) break

                total += read
                if (total > limit) return@withContext null

                output.write(buffer, 0, read)
            }
        }
    }

    total
}
