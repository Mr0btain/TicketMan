package org.mr0btain.ticketman.logging


enum class SimpleResult(override val message: String): LogMessage {
    SUCCESS("SUCCESS"),
    FAIL("FAIL")
}