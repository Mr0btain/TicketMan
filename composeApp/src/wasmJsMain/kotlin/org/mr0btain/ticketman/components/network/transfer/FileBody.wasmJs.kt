package org.mr0btain.ticketman.components.network.transfer

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.ktor.http.*
import io.ktor.http.content.*

// A copy or two of this fits in a browser tab's memory alongside the app
actual val fileBodyLimitBytes: Long? = 512L * 1024 * 1024

// The browser hands over picked files whole, so this holds it in memory while it uploads
actual suspend fun fileBody(file: PlatformFile, contentType: ContentType): OutgoingContent =
    ByteArrayContent(file.readBytes(), contentType)
