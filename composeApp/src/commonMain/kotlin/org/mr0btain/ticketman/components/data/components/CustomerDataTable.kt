package org.mr0btain.ticketman.components.data.components

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json

object CustomerDataTable : IntIdTable("customer") {
    val idNumber = integer("number")
    val firstName = text("firstname")
    val lastName = text("lastname")
    val street = text("street")
    val city = text("city")
    val state = text("state")
    val zip = text("zip")
    val tickets = json<TicketList>("tickets", Json { prettyPrint = true })
    val notes = json<Notes>("notes", Json { prettyPrint = true })
    val contactInfo = json<ContactInfo>("contact", Json { prettyPrint = true })
}
