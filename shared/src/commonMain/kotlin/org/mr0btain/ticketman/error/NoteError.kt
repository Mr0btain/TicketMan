package org.mr0btain.ticketman.error

enum class NoteError(override val message: String): ErrorCode {
    NOT_FOUND("Note not found!"),
    ALREADY_EXISTS("Note already exists!")
}