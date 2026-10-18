package org.mr0btain.ticketman.error

enum class UserError(override val message: String): Error {
    NOT_FOUND("User not found!")
}