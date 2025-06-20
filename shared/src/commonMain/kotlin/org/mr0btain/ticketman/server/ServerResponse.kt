package org.mr0btain.ticketman.server

data class ServerResponse(
    val result: ServerResult,
    val message: String = ""
)

enum class ServerResult {
    SUCCESS,
    FAIL
}