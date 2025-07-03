package org.mr0btain.ticketman.components.logging

import java.util.logging.Level
import java.util.logging.Logger

/*
object AppLogger {
    private val logger: Logger = Logger.getLogger(AppLogger::class.java.name)

    init {
        logger.level = Level.ALL
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        if (throwable != null) {
            logger.log(Level.SEVERE, "ERROR: [$tag] $message", throwable)
        } else {
            logger.warning("ERROR: [$tag] $message")
        }
    }

    fun d(tag: String, message: String) {
        logger.info("DEBUG: [$tag] $message")
    }

    fun i(tag: String, message: String) {
        logger.info("[$tag] $message")
    }

    fun m(tag: String, message: String) {
        logger.log(Level.ALL, "[$tag] $message")
        //println("[$tag] $message")
    }
}
 */