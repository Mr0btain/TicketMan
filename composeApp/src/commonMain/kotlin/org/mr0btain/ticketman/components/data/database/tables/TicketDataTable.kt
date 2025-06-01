package org.mr0btain.ticketman.components.data.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.components.data.components.Device
import org.mr0btain.ticketman.components.data.components.Notes
import org.mr0btain.ticketman.components.data.components.TicketPriority
import org.mr0btain.ticketman.components.data.components.TicketStatus

object TicketDataTable: IntIdTable("tickets") {
    val identifier = integer("number")
    val customerId = text("customerid")
    val assignedEmp = text("assignedemp")
    val device = json<Device>("device", Json.Default)
    val status = json<TicketStatus>("status", Json.Default)
    val priority = json<TicketPriority>("priority", Json.Default)
    val updates = json<Notes>("updates", Json.Default)
}