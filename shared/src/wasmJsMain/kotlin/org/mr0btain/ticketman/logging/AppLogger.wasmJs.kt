package org.mr0btain.ticketman.logging

/** Writes to the browser console, open the developer tools to read it, and keeps each line in [LogBook] */
actual object AppLogger {
    actual var debug: Boolean = false

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        println("ERROR: [$tag] $message${throwable?.let { "\n${it.stackTraceToString()}" }.orEmpty()}")
        LogBook.record(LogLevel.ERROR, tag, message, throwable)
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) = e(tag, message.message, throwable)

    actual fun d(tag: String, message: String) {
        if (!debug) return
        println("DEBUG: [$tag] $message")
        LogBook.record(LogLevel.DEBUG, tag, message)
    }

    actual fun i(tag: String, message: String) {
        println("[$tag] $message")
        LogBook.record(LogLevel.INFO, tag, message)
    }

    actual fun i(tag: String, message: LogMessage) = i(tag, message.message)

    actual fun m(tag: String, message: String) = i(tag, message)

    actual fun m(tag: String, message: LogMessage) = m(tag, message.message)
}
