package org.mr0btain.ticketman.logging

import java.util.logging.Level
import java.util.logging.Logger

/** Writes to java.util.logging (the console) and keeps each line in [LogBook] */
actual object AppLogger {
    private val logger: Logger = Logger.getLogger(AppLogger::class.java.name)
    actual var debug: Boolean = false

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        logger.log(Level.SEVERE, "ERROR: [$tag] $message", throwable)
        LogBook.record(LogLevel.ERROR, tag, message, throwable)
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) = e(tag, message.message, throwable)

    actual fun d(tag: String, message: String) {
        if (!debug) return
        logger.info("DEBUG: [$tag] $message")
        LogBook.record(LogLevel.DEBUG, tag, message)
    }

    actual fun i(tag: String, message: String) {
        logger.info("[$tag] $message")
        LogBook.record(LogLevel.INFO, tag, message)
    }

    actual fun i(tag: String, message: LogMessage) = i(tag, message.message)

    actual fun m(tag: String, message: String) {
        println("[$tag] $message")
        LogBook.record(LogLevel.INFO, tag, message)
    }

    actual fun m(tag: String, message: LogMessage) = m(tag, message.message)
}
