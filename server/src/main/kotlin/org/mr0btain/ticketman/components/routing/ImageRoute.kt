package org.mr0btain.ticketman.components.routing

import org.mr0btain.ticketman.server.ImagePaths
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import org.mr0btain.ticketman.components.database.AuditLogDatabase
import org.mr0btain.ticketman.components.service.ImageService
import org.mr0btain.ticketman.data.audit.AuditAction
import org.mr0btain.ticketman.data.image.ImageLimits
import org.mr0btain.ticketman.data.image.ImageVariant
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.error.ImageError
import org.mr0btain.ticketman.error.Result
import org.mr0btain.ticketman.logging.AppLogger
import java.io.File
import kotlin.uuid.ExperimentalUuidApi

/**
 * Photo uploads and downloads, under /api/image
 *
 * Uploading gives back a pending [StoredImage]. Saving a note or item with that image's id links it, and photos
 * never linked are cleaned up a day later. Files never change once uploaded, so they're sent with a long
 * immutable cache lifetime and their hash as the ETag
 */
fun Route.imageRoute(
    service: ImageService,
    auditLog: AuditLogDatabase
) {
    val TAG = "ImageRoute"
    val UPLOAD_TAG = "$TAG:Upload"

    authenticate {
        // Multipart with the file in "image" and optionally a "description"
        post(ImagePaths.UPLOAD) {
            // Refused before reading a byte when the client says up front it's too big
            if ((call.request.contentLength() ?: 0) > ImageLimits.MAX_UPLOAD_BYTES + MULTIPART_OVERHEAD_BYTES) {
                call.respondText(ImageError.TOO_LARGE.message, status = HttpStatusCode.PayloadTooLarge)
                return@post
            }

            // Ktor's parser reads ahead, so its limit has room to spare and ours in receive() is the one that trips
            val multipart = call.receiveMultipart(formFieldLimit = ImageLimits.MAX_UPLOAD_BYTES * 2)
            var upload: File? = null
            var receiving: File? = null
            var description = ""
            var tooLarge = false

            try {
                multipart.forEachPart { part ->
                    try {
                        when {
                            part is PartData.FormItem && part.name == ImageLimits.DESCRIPTION_FIELD ->
                                description = part.value

                            part is PartData.FileItem && part.name == ImageLimits.UPLOAD_FIELD && upload == null && !tooLarge -> {
                                val target = service.newUploadFile().also { receiving = it }

                                if (service.receive(part.provider(), target) == null) {
                                    target.delete()
                                    tooLarge = true
                                } else {
                                    upload = target
                                }

                                receiving = null
                            }
                        }
                    } finally {
                        part.dispose()
                    }
                }
            } catch (ex: Exception) {
                // The client going away mid upload, or the rest of an oversized one being skipped
                AppLogger.i(UPLOAD_TAG, "Upload cut short: ${ex.message}")
                receiving?.delete()
                upload?.delete()
                upload = null
            }

            val received = upload

            when {
                tooLarge -> call.respondText(ImageError.TOO_LARGE.message, status = HttpStatusCode.PayloadTooLarge)
                received == null -> call.respondText(ImageError.MISSING.message, status = HttpStatusCode.BadRequest)
                else -> when (val stored = service.store(received, description, call.getIdentifierFromToken())) {
                    is Result.Success -> {
                        call.audit(auditLog, AuditAction.CREATE, "image", stored.data.identifier, "${stored.data.sizeBytes} bytes")
                        call.respond(HttpStatusCode.Created, stored.data)
                    }

                    is Result.Error -> call.respondText(stored.error.message, status = stored.error.status())
                }
            }
        }
    }

    authenticate("restricted") {
        get(ImagePaths.GET) {
            val image = call.uuidParameter("identifier")?.let { service.get(it, call.getIdentifierFromToken()) }

            if (image == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(HttpStatusCode.OK, image)
        }

        get(ImagePaths.FILE) {
            val identifier = call.uuidParameter("identifier")
            val variant = call.parameters["variant"]?.let { ImageVariant.fromPath(it) }
            val found = if (identifier != null && variant != null) service.fileFor(identifier, variant, call.getIdentifierFromToken()) else null

            if (found == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val (file, hash) = found
            val etag = "\"$hash-${variant!!.path}\""

            call.response.header(HttpHeaders.CacheControl, "private, max-age=$ONE_YEAR_SECONDS, immutable")
            call.response.header(HttpHeaders.ETag, etag)

            if (call.request.headers[HttpHeaders.IfNoneMatch]?.split(',')?.any { it.trim() == etag } == true) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }

            call.respondFile(file)
        }
    }
}

private fun ImageError.status(): HttpStatusCode = when (this) {
    ImageError.NOT_FOUND -> HttpStatusCode.NotFound
    ImageError.TOO_LARGE -> HttpStatusCode.PayloadTooLarge
    ImageError.UNSUPPORTED -> HttpStatusCode.UnsupportedMediaType
    ImageError.MISSING -> HttpStatusCode.BadRequest
    ImageError.FAILED -> HttpStatusCode.InternalServerError
}

private const val ONE_YEAR_SECONDS = 31_536_000

/** Room for the multipart boundaries and the description on top of the file */
private const val MULTIPART_OVERHEAD_BYTES = 64L * 1024
