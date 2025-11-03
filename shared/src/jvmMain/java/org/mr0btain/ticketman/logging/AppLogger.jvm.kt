package org.mr0btain.ticketman.logging

import org.mr0btain.ticketman.error.ErrorCode
import org.mr0btain.ticketman.logging.SimpleResult
import java.util.logging.Level
import java.util.logging.Logger

actual object AppLogger {
    private val logger: Logger = Logger.getLogger(AppLogger::class.java.name)
    actual var LOG_LEVEL: LogLevel = LogLevel.NORMAL

    init {
        logger.level = when (LOG_LEVEL) {
            LogLevel.NONE -> Level.OFF
            LogLevel.NORMAL -> Level.FINEST
            LogLevel.HIGH -> Level.INFO
            LogLevel.SEVERE -> Level.SEVERE
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        logger.log(Level.SEVERE, "ERROR: [$tag] $message", throwable)
    }

    actual fun e(tag: String, message: ErrorCode, throwable: Throwable?) {
        logger.log(Level.SEVERE, "ERROR: [$tag] ${message.message}", throwable)
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