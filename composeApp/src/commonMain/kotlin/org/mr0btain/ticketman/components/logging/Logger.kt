package org.mr0btain.ticketman.components.logging

import kotlinx.coroutines.flow.MutableStateFlow

object Logger {
    private const val LOG_PREFIX = "TicketMan"

    // MutableStateFlow to hold logs
    val logs = MutableStateFlow<MutableList<LogItem>>(mutableListOf())

    fun d(message: String, caller: String, prefix: String = LOG_PREFIX) {
        logs.value.add(
            LogItem(
                type = LogType.INFO,
                message = "[$prefix] $message",
                caller = caller
            )
        )

        println("$LOG_PREFIX$message")
    }

    fun w(message: String, caller: String, prefix: String = LOG_PREFIX) {
        logs.value.add(
            LogItem(
                type = LogType.WARNING,
                message = "$prefix$message",
                caller = caller
            )
        )

        println("$LOG_PREFIX$message")
    }

    fun e(message: String, caller: String, throwable: Throwable? = null, prefix: String = LOG_PREFIX) {
        // Get stack trace and log it
        logs.value.addAll(getStackTrace())

        // Add to out logger
        logs.value.add(
            LogItem(
                type = LogType.INFO,
                message = "$prefix$message",
                caller = caller,
                throwable = throwable
            )
        )
    }

    fun dump(): List<LogItem> { return logs.value }

    private fun getStackTrace(): List<LogItem> {
        return Thread.currentThread().stackTrace.map { element ->
            LogItem(
                type = LogType.INFO,
                message = element.toString(),
                caller = "(${element.fileName}:${element.lineNumber}) ${element.methodName}()"
            )
        }
    }
}