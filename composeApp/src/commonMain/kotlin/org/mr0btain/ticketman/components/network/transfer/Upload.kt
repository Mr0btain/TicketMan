package org.mr0btain.ticketman.components.network.transfer

import io.github.vinceglb.filekit.PlatformFile
import io.ktor.http.*
import io.ktor.http.content.*

/**
 * Request body that sends [file] as it's read, so a backup of any size goes up without being loaded into memory
 * first. The browser can't read a picked file as a stream here, so it reads it whole
 */
expect suspend fun fileBody(file: PlatformFile, contentType: ContentType): OutgoingContent
