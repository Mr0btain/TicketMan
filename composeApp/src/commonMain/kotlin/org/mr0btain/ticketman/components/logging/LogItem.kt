package org.mr0btain.ticketman.components.logging


data class LogItem(
    val type: LogType = LogType.INFO,
    val message: String,
    val caller: String = "",
    val throwable: Throwable? = null
)
