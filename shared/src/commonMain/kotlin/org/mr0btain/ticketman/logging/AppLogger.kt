package org.mr0btain.ticketman.logging

import org.mr0btain.ticketman.error.ErrorCode

/**
 * Logger to log any messages or errors
 */
expect object AppLogger {
    /** Exceptions, thrown when something bad has happened */
    fun e(tag: String, message: String, throwable: Throwable? = null)

    /** Debug messages */
    fun d(tag: String, message: String)

    /** Info */
    fun i(tag: String, message: String)
    fun i(tag: String, message: ErrorCode)

    /** General message */
    fun m(tag: String, message: String)
    fun m(tag: String, message: ErrorCode)
}