package org.mr0btain.ticketman.logging


/**
 * Logger to log any messages or errors
 */
expect object AppLogger {
    /**
     * Whether [d] writes anything. Off until the app turns it on: clients from `isDebug()`, the server in
     * development mode
     */
    var debug: Boolean

    /** Exceptions, thrown when something bad has happened */
    fun e(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: LogMessage, throwable: Throwable? = null)

    /** Debug messages, only written when [debug] is on */
    fun d(tag: String, message: String)

    /** Info */
    fun i(tag: String, message: String)
    fun i(tag: String, message: LogMessage)

    /** General message */
    fun m(tag: String, message: String)
    fun m(tag: String, message: LogMessage)
}
