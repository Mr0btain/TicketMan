package org.mr0btain.ticketman.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.customer.TicketList
import org.mr0btain.ticketman.data.employee.AccessLevel
import org.mr0btain.ticketman.data.shared.Notes

object UserTable : IntIdTable("user") {
    val identifier = varchar("identifier", 36)
    val username = text("username")
    val password = text("password")
    val firstName = text("firstname")
    val lastName = text("lastname")
    val tickets = json<TicketList>("tickets", Json.Default)
    val contactInfo = json<ContactInfo>("contact", Json.Default)
    val notes = json<Notes>("notes", Json.Default)
    val access = json<AccessLevel>("access", Json.Default)
    val time = long("time")
}