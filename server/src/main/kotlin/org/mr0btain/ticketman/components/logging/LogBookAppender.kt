package org.mr0btain.ticketman.components.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import org.mr0btain.ticketman.logging.LogBook
import org.mr0btain.ticketman.logging.LogLevel

/**
 * Hands what the server's libraries log through SLF4J (Ktor, Netty, Exposed) to [LogBook], so it's in the log files
 * and the live view beside what [org.mr0btain.ticketman.logging.AppLogger] logs. Named in logback.xml
 */
class LogBookAppender : AppenderBase<ILoggingEvent>() {
    override fun append(event: ILoggingEvent) {
        val level = when {
            event.level.isGreaterOrEqual(Level.ERROR) -> LogLevel.ERROR
            event.level.isGreaterOrEqual(Level.WARN) -> LogLevel.WARN
            event.level.isGreaterOrEqual(Level.INFO) -> LogLevel.INFO
            else -> LogLevel.DEBUG
        }
        val trace = event.throwableProxy?.let { ThrowableProxyUtil.asString(it) }
        val message = event.formattedMessage.orEmpty() + trace?.let { "\n$it" }.orEmpty()
        LogBook.record(level, event.loggerName.substringAfterLast('.'), message)
    }
}
