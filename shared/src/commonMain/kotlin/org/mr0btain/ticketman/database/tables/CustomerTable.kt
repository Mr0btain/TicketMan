package org.mr0btain.ticketman.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.TicketList
import org.mr0btain.ticketman.data.shared.Notes

// TODO: Maybe encrypt whole data and just leave first last and identifier unencrypted

object CustomerTable : DataIntTable("customer") {
    val firstName = text("firstname")
    val lastName = text("lastname")
    val street = text("street")
    val city = text("city")
    val state = text("state")
    val zip = text("zip")
    val tickets = json<TicketList>("tickets", Json { ignoreUnknownKeys = true })
    val contactInfo = json<ContactInfo>("contact", Json { ignoreUnknownKeys = true })
    val notes = json<Notes>("notes", Json { ignoreUnknownKeys = true })
}