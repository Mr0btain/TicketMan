package org.mr0btain.ticketman.server.response

/**
 * `GET /api/ping` answers with [SERVER_PING_BODY], so a client can tell a TicketMan server from any other web
 * server at that address (like the web client's dev server, or a page served at `/`)
 */
const val SERVER_PING_PATH = "/api/ping"
const val SERVER_PING_BODY = "TicketMan"
