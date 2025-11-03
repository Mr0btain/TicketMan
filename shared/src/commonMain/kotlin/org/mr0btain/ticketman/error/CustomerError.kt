package org.mr0btain.ticketman.error

enum class CustomerError(override val message: String): ErrorCode {
    NOT_FOUND("Customer not found!"),
    ALREADY_EXISTS("Customer already exists!")
}