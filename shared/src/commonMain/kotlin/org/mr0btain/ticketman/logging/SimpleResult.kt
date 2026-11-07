package org.mr0btain.ticketman.logging

import org.mr0btain.applogger.LogMessage


enum class SimpleResult(override val message: String): LogMessage {
    SUCCESS("SUCCESS"),
    FAIL("FAIL")
}