package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json

object TicketDataTable: IntIdTable("tickets") {
    val idNumber = text("number")
    val customerId = text("customerid")
    val assignedEmp = text("assignedemp")
    val device = json<Device>("device", Json)
    val status = json<TicketStatus>("status", Json)
    val priority = json<TicketPriority>("priority", Json)
    val notes = json<Notes>("notes", Json)
}