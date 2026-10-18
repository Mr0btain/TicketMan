package org.mr0btain.ticketman.data.image

/**
 * The sizes the server keeps of every photo. Each is a JPEG fitted inside a square of [maxEdge]
 * @param path Used in the url, `/api/image/{id}/{path}`, and as the file name on the server
 */
enum class ImageVariant(val path: String, val maxEdge: Int, val quality: Float) {
    /** Lists and note blocks */
    THUMB("thumb", 320, 0.75f),

    /** What the pager and popup show */
    DISPLAY("display", 1600, 0.82f),

    /** The upload itself, capped so a 48MP phone photo doesn't fill the disk */
    ORIGINAL("original", ImageLimits.MAX_EDGE, 0.9f);

    companion object {
        fun fromPath(path: String): ImageVariant? = entries.firstOrNull { it.path == path }
    }
}

object ImageLimits {
    /** Longest edge an image is kept at */
    const val MAX_EDGE = 2560

    /** Biggest upload the server takes */
    const val MAX_UPLOAD_BYTES = 25L * 1024 * 1024

    /** Images with more pixels than this are refused before decoding, so a tiny file can't claim to be huge */
    const val MAX_PIXELS = 50_000_000L

    /** Name of the file part in an upload */
    const val UPLOAD_FIELD = "image"

    /** Name of the optional description part in an upload */
    const val DESCRIPTION_FIELD = "description"
}
