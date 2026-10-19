package org.mr0btain.ticketman.logging

import java.util.logging.Level
import java.util.logging.Logger

actual object AppLogger {
    private val logger: Logger = Logger.getLogger(AppLogger::class.java.name)
    actual var debug: Boolean = false

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        logger.log(Level.SEVERE, "ERROR: [$tag] $message", throwable)
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) {
        logger.log(Level.SEVERE, "ERROR: [$tag] ${message.message}", throwable)
    }

    actual fun d(tag: String, message: String) {
        if (debug) logger.info("DEBUG: [$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        logger.info("[$tag] $message")
    }

    actual fun i(tag: String, message: LogMessage) {
        logger.info("[$tag] ${message.message}")
    }

    actual fun m(tag: String, message: String) {
        println("[$tag] $message")
    }

    actual fun m(tag: String, message: LogMessage) {
        println("[$tag] ${message.message}")
    }
}
