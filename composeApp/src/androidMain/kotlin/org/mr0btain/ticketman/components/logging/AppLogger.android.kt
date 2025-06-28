package org.mr0btain.ticketman.components.logging

import android.util.Log

actual object AppLogger {
    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }

    actual fun d(tag: String, message: String) { Log.d(tag, message) }

    actual fun i(tag: String, message: String) { Log.i(tag, message) }

    actual fun m(tag: String, message: String) { println("[$tag] $message") }

    actual fun m(tag: String, message: SimpleError) { println("[$tag] ${message.name}") }
}