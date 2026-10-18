package org.mr0btain.ticketman.error

enum class TicketError(override val message: String): Error {
    NOT_FOUND("Ticket not found!"),
    ALREADY_EXISTS("Ticket already exists!")
}