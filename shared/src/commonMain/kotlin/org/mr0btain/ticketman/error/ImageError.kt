package org.mr0btain.ticketman.error

enum class ImageError(override val message: String): Error {
    NOT_FOUND("Image not found!"),
    TOO_LARGE("That photo is too big"),
    UNSUPPORTED("That file isn't a photo we can read (use JPEG, PNG, GIF or BMP)"),
    MISSING("No photo was sent"),
    FAILED("The photo couldn't be saved")
}
