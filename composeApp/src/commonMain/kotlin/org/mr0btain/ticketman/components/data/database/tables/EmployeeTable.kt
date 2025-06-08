package org.mr0btain.ticketman.components.data.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.components.data.components.employee.AccessLevel
import org.mr0btain.ticketman.components.data.components.customer.ContactInfo
import org.mr0btain.ticketman.components.data.components.shared.Notes
import org.mr0btain.ticketman.components.data.components.customer.TicketList

// TODO: Encrypt sensitive data

object EmployeeTable : IntIdTable("employee") {
    val identifier = integer("number")
    val firstName = text("firstname")
    val lastName = text("lastname")
    val tickets = json<TicketList>("tickets", Json.Default)
    val contactInfo = json<ContactInfo>("contact", Json.Default)
    val notes = json<Notes>("notes", Json.Default)
    val access = json<AccessLevel>("access", Json.Default)
    val time = long("time")
}