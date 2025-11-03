package org.mr0btain.ticketman.error

import org.mr0btain.ticketman.logging.AppLogger
import org.mr0btain.ticketman.logging.SimpleResult

fun appLogTryCatch(tag: String, function: () -> Boolean) {
    try {
        function().also { successFlag ->
            if (successFlag) {
                AppLogger.m(tag, SimpleResult.SUCCESS)
            } else AppLogger.i(tag, SimpleResult.FAIL)
        }
    } catch (ex: Exception) {
        AppLogger.e(tag, SimpleResult.FAIL, ex)
    }
}

fun <T> appLogTryCatch(tag: String, function: () -> Pair<T, Boolean>): T? {
    return try {
        val (result, successFlag) = function()

        if (successFlag) {
            AppLogger.m(tag, SimpleResult.SUCCESS)
        } else AppLogger.i(tag, SimpleResult.FAIL)

        result
    } catch (ex: Exception) {
        AppLogger.e(tag, SimpleResult.FAIL, ex)
        null
    }
}
