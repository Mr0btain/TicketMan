package org.mr0btain.ticketman.logging

/** Writes to the browser console, open the developer tools to read it */
actual object AppLogger {
    actual var LOG_LEVEL: LogLevel = LogLevel.NORMAL

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (LOG_LEVEL == LogLevel.NONE) return
        println("ERROR: [$tag] $message${throwable?.let { "\n${it.stackTraceToString()}" }.orEmpty()}")
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) = e(tag, message.message, throwable)

    actual fun d(tag: String, message: String) {
        if (LOG_LEVEL == LogLevel.NORMAL) println("DEBUG: [$tag] $message")
    }

    actual fun i(tag: String, message: String) {
        if (LOG_LEVEL == LogLevel.NORMAL || LOG_LEVEL == LogLevel.HIGH) println("[$tag] $message")
    }

    actual fun i(tag: String, message: LogMessage) = i(tag, message.message)

    actual fun m(tag: String, message: String) {
        if (LOG_LEVEL == LogLevel.NORMAL || LOG_LEVEL == LogLevel.HIGH) println("[$tag] $message")
    }

    actual fun m(tag: String, message: LogMessage) = m(tag, message.message)
}
