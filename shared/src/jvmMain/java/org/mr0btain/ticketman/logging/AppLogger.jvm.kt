package org.mr0btain.ticketman.logging

import org.mr0btain.ticketman.error.ErrorCode
import org.mr0btain.ticketman.error.SimpleResult
import java.util.logging.Level
import java.util.logging.Logger

actual object AppLogger {
    private val logger: Logger = Logger.getLogger(AppLogger::class.java.name)

    init {
        logger.level = Level.FINE
    }

    actual fun e(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (throwable != null) {
            logger.log(Level.SEVERE, "ERROR: [$tag] $message", throwable)
        } else {
            logger.severe("ERROR: [$tag] $message")
        }
    }

    actual fun d(tag: String, message: String) {
        logger.info("DEBUG: [$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        logger.info("[$tag] $message")
    }

    actual fun i(tag: String, message: ErrorCode) {
        logger.info("[$tag] ${message.message}")
    }

    actual fun m(tag: String, message: String) {
        println("[$tag] $message")
    }

    actual fun m(tag: String, message: ErrorCode) {
        //logger.log(Level.ALL, "[$tag] $message")
        println("[$tag] ${message.message}")
    }
}