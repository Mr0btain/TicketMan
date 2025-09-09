package org.mr0btain.ticketman.error

enum class SimpleResult(override val message: String): ErrorCode {
    SUCCESS("SUCCESS"),
    FAIL("FAIL")
}