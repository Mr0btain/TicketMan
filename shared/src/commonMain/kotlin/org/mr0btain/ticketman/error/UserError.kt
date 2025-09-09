package org.mr0btain.ticketman.error

enum class UserError(override val message: String): ErrorCode {
    NOT_FOUND("User not found!")
}