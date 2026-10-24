package org.mr0btain.ticketman.logging

import platform.Foundation.NSLog

/** Writes to the system log, read in Xcode's console or Console.app with the device connected */
actual object AppLogger {
    actual var debug: Boolean = false

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        log("ERROR: [$tag] $message${throwable?.let { "\n${it.stackTraceToString()}" }.orEmpty()}")
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) = e(tag, message.message, throwable)

    actual fun d(tag: String, message: String) {
        if (debug) log("DEBUG: [$tag] $message")
    }

    actual fun i(tag: String, message: String) = log("[$tag] $message")

    actual fun i(tag: String, message: LogMessage) = i(tag, message.message)

    actual fun m(tag: String, message: String) = log("[$tag] $message")

    actual fun m(tag: String, message: LogMessage) = m(tag, message.message)

    // NSLog reads its first argument as a format, so the message goes in as an argument and its % signs stay as they are
    private fun log(line: String) = NSLog("%@", line)
}
