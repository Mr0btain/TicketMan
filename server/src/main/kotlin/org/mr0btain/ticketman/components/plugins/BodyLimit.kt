package org.mr0btain.ticketman.components.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import org.mr0btain.ticketman.components.routing.TOO_LARGE_MESSAGE
import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.server.AdminPaths
import org.mr0btain.ticketman.server.ApiRoutes
import org.mr0btain.ticketman.server.ImagePaths
import org.mr0btain.ticketman.server.TimeClockPaths

private const val TAG = "BodyLimit"

/** How much of a request's body the server reads into memory, per kind of route */
object BodyLimits {
    /** Routes anyone can reach without logging in, whose bodies are a few fields */
    const val PUBLIC_FORM = 64L * 1024

    /** Far more than a payment event, which is a few KB */
    const val WEBHOOK = 1024L * 1024

    /** Everything else, all behind a login that's checked before the body is read */
    const val DEFAULT = 5L * 1024 * 1024

    /** A CSV import's rows, up to `ImportService.MAX_ROWS` of them */
    const val IMPORT = 32L * 1024 * 1024

    /**
     * The limit for a path, null for the two big transfers: photo uploads and restores are streamed to disk and
     * check their own sizes. Only exact paths go without, so anything else that reaches a route still has a limit
     */
    fun forPath(path: String): Long? = when {
        path == ApiRoutes.IMAGE + ImagePaths.UPLOAD || path == ApiRoutes.ADMIN + AdminPaths.RESTORE -> null
        path == ApiRoutes.ADMIN + AdminPaths.IMPORT_CUSTOMERS || path == ApiRoutes.ADMIN + AdminPaths.IMPORT_INVENTORY -> IMPORT
        path.startsWith("${ApiRoutes.STATUS}/payments/") -> WEBHOOK
        path == ApiRoutes.STATUS || path.startsWith("${ApiRoutes.STATUS}/") -> PUBLIC_FORM
        path == ApiRoutes.AUTH || path.startsWith("${ApiRoutes.AUTH}/") -> PUBLIC_FORM
        path == ApiRoutes.TIME_CLOCK + TimeClockPaths.PUNCH -> PUBLIC_FORM
        else -> DEFAULT
    }
}

/**
 * Turns away bodies over [BodyLimits], which Ktor would otherwise read whole into memory, so a big enough request
 * could run the server out of memory without logging in. One that says its size up front is refused before a byte is
 * read. One that doesn't (chunked) is cut off as it passes the limit, and the [PayloadTooLargeException] answers `413`
 */
fun Application.configureBodyLimits() {
    install(BodyLimit)
}

private val BodyLimit = createApplicationPlugin("BodyLimit") {
    onCall { call ->
        val path = call.request.path()
        val limit = BodyLimits.forPath(path) ?: return@onCall
        val length = call.request.contentLength() ?: return@onCall

        if (length > limit) {
            AppLogger.i(TAG, "Turned away $length bytes for $path")
            call.respondText(TOO_LARGE_MESSAGE, status = HttpStatusCode.PayloadTooLarge)
        }
    }

    on(ReceiveRequestBytes) { call, body ->
        val limit = BodyLimits.forPath(call.request.path())
        if (limit == null) body else call.capped(body, limit)
    }
}

// What's left of [body] up to [limit], ending in the exception past it
private fun ApplicationCall.capped(body: ByteReadChannel, limit: Long): ByteReadChannel =
    writer {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L

        while (true) {
            val read = body.readAvailable(buffer)
            if (read < 0) break

            total += read
            if (total > limit) throw PayloadTooLargeException(limit)
            channel.writeFully(buffer, 0, read)
            channel.flush()
        }
    }.channel

private const val BUFFER_BYTES = 8 * 1024
