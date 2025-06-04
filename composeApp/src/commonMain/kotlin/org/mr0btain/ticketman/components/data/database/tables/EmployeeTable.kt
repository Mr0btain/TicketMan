package org.mr0btain.ticketman.components.data.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.components.data.components.AccessLevel
import org.mr0btain.ticketman.components.data.components.ContactInfo
import org.mr0btain.ticketman.components.data.components.Notes
import org.mr0btain.ticketman.components.data.components.TicketList

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