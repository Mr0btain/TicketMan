package org.mr0btain.ticketman.components.network.transfer

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.size
import io.ktor.http.*
import io.ktor.http.content.*

/**
 * Request body that sends [file] as it's read, so a backup of any size goes up without being loaded into memory
 * first. The browser can't read a picked file as a stream here, so it reads it whole
 */
expect suspend fun fileBody(file: PlatformFile, contentType: ContentType): OutgoingContent

/**
 * Biggest file [fileBody] can send, or null for no limit. Only web has one, since it holds the whole file in memory
 * (Ktor's browser engine builds the request body before sending it, so a picked file can't be streamed)
 */
expect val fileBodyLimitBytes: Long?

/** Whether [file] is over [fileBodyLimitBytes] */
fun isTooBigToSend(file: PlatformFile): Boolean = fileBodyLimitBytes?.let { file.size() > it } ?: false
