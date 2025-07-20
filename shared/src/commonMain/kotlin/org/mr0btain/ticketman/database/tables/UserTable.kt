package org.mr0btain.ticketman.database.tables

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.TicketList
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.Notes

object UserTable : DataIntTable("user") {
    val username = text("username")
    val password = text("password")
    val firstName = text("firstname")
    val lastName = text("lastname")
    val tickets = json<TicketList>("tickets", Json { ignoreUnknownKeys = true })
    val contactInfo = json<ContactInfo>("contact", Json { ignoreUnknownKeys = true })
    val access = json<AccessLevel>("access", Json { ignoreUnknownKeys = true })
}