package org.mr0btain.ticketman.components.database.tables.ticket

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.ticket.Device
import org.mr0btain.ticketman.data.ticket.TicketPriority
import org.mr0btain.ticketman.data.ticket.TicketStatus

object TicketTable: UuidTable("tickets") {
    val device = json<Device>("device", Json { ignoreUnknownKeys = true })
    val status = json<TicketStatus>("status", Json { ignoreUnknownKeys = true })
    val priority = json<TicketPriority>("priority", Json { ignoreUnknownKeys = true })

    // Ticket number, null only until older tickets are back filled on start
    val numberYear = integer("number_year").nullable()
    val numberSequence = long("number_sequence").nullable()

    // Check in details. Defaults let these be added to existing databases
    val problem = text("problem").default("")
    val condition = text("device_condition").default("")
    val accessories = text("accessories").default("")
    val estimateCents = long("estimate_cents").nullable()
    val promisedAt = long("promised_at").nullable()
    val pickedUpAt = long("picked_up_at").nullable()

    // Unique on (year, sequence), created by LocalDatabase.initDatabase so new and existing databases match
    const val NUMBER_INDEX = "tickets_number_unique"
}
