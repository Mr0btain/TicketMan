package org.mr0btain.ticketman.logging

import android.util.Log

/** Writes to logcat and keeps each line in [LogBook] */
actual object AppLogger {
    actual var debug: Boolean = false

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        LogBook.record(LogLevel.ERROR, tag, message, throwable)
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) = e(tag, message.message, throwable)

    actual fun d(tag: String, message: String) {
        if (!debug) return
        Log.d(tag, message)
        LogBook.record(LogLevel.DEBUG, tag, message)
    }

    actual fun i(tag: String, message: String) {
        Log.i(tag, message)
        LogBook.record(LogLevel.INFO, tag, message)
    }

    actual fun m(tag: String, message: String) {
        println("[$tag] $message")
        LogBook.record(LogLevel.INFO, tag, message)
    }

    actual fun m(tag: String, message: LogMessage) = m(tag, message.message)
    actual fun i(tag: String, message: LogMessage) = i(tag, message.message)
}
