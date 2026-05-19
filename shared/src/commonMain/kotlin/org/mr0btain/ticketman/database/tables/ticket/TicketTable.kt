package org.mr0btain.ticketman.database.tables.ticket

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus

object TicketTable: UUIDTable("tickets") {
    val device = json<Device>("device", Json { ignoreUnknownKeys = true })
    val status = json<TicketStatus>("status", Json { ignoreUnknownKeys = true })
    val priority = json<TicketPriority>("priority", Json { ignoreUnknownKeys = true })
}