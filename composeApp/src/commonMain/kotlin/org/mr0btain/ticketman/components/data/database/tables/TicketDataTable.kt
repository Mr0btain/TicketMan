package org.mr0btain.ticketman.components.data.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.components.data.components.Device
import org.mr0btain.ticketman.components.data.components.Notes
import org.mr0btain.ticketman.components.data.components.PersonName
import org.mr0btain.ticketman.components.data.components.TicketPriority
import org.mr0btain.ticketman.components.data.components.TicketStatus

// TODO: Setup time stamps for notes or updates, times for when tickets added and such

object TicketDataTable: IntIdTable("tickets") {
    val identifier = integer("number")
    val customerId = integer("customerid")
    val assignedEmp = integer("assignedemp")
    val device = json<Device>("device", Json.Default)
    val status = json<TicketStatus>("status", Json.Default)
    val priority = json<TicketPriority>("priority", Json.Default)
    val updates = json<Notes>("updates", Json.Default)
    val time = long("time")
}