package org.mr0btain.ticketman.logging

import org.mr0btain.ticketman.error.ErrorCode

enum class SimpleResult(override val message: String): ErrorCode {
    SUCCESS("SUCCESS"),
    FAIL("FAIL")
}