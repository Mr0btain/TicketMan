package org.mr0btain.ticketman.logging

import android.util.Log

actual object AppLogger {
    actual var debug: Boolean = false

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) = e(tag, message.message, throwable)

    actual fun d(tag: String, message: String) { if (debug) Log.d(tag, message) }

    actual fun i(tag: String, message: String) { Log.i(tag, message) }

    actual fun m(tag: String, message: String) { println("[$tag] $message") }

    actual fun m(tag: String, message: LogMessage) { println("[$tag] ${message.message}") }
    actual fun i(tag: String, message: LogMessage) { Log.i("[$tag]", message.message) }
}