package org.mr0btain.ticketman.logging


actual object AppLogger {
    actual var LOG_LEVEL: LogLevel
        get() = TODO("Not yet implemented")
        set(value) {}

    actual fun e(tag: String, message: String, throwable: Throwable?) {
    }

    actual fun e(tag: String, message: LogMessage, throwable: Throwable?) {
    }

    actual fun d(tag: String, message: String) {
    }

    actual fun i(tag: String, message: String) {
    }

    actual fun i(tag: String, message: LogMessage) {
    }

    actual fun m(tag: String, message: String) {
    }

    actual fun m(tag: String, message: LogMessage) {
    }
}