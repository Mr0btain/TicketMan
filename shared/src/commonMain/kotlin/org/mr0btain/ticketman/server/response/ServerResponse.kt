package org.mr0btain.ticketman.server.response

data class ServerResponse(
    val result: ServerResult,
    val message: String = ""
)

enum class ServerResult {
    SUCCESS,
    FAIL
}