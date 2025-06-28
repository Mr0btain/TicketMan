package org.mr0btain.ticketman.components.logging

import org.mr0btain.ticketman.error.SimpleError
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

    actual fun i(tag: String, message: SimpleError) {
        logger.info("[$tag] ${message.name}")
    }

    actual fun m(tag: String, message: String) {
        println("[$tag] $message")
    }

    actual fun m(tag: String, message: SimpleError) {
        logger.log(Level.ALL, "[$tag] $message")
        //println("[$tag] ${message.name}")
    }
}