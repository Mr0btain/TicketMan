package org.mr0btain.ticketman.error

import org.mr0btain.ticketman.logging.AppLogger

fun appLogTryCatch(tag: String, function: () -> Unit) {
    try {
        function()
    } catch (ex: Exception) {
        AppLogger.i(tag, SimpleResult.FAIL)
        ex.printStackTrace()
    }
}

fun <T> appLogTryCatch(tag: String, function: () -> T): T? {
    return try {
        function()
    } catch (ex: Exception) {
        AppLogger.i(tag, SimpleResult.FAIL)
        ex.printStackTrace()
        null
    }
}