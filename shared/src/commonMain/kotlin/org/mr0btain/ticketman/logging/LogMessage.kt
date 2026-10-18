package org.mr0btain.ticketman.logging

/** Something [AppLogger] can print, like an [org.mr0btain.ticketman.error.Error] or a [SimpleResult] */
interface LogMessage {
    val message: String
}
