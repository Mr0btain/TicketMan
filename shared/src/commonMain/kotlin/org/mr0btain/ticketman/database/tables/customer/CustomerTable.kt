package org.mr0btain.ticketman.database.tables.customer

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.customer.Address
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName

object CustomerTable : UUIDTable("customer") {
    val name = json<PersonName>("person_name", Json { ignoreUnknownKeys = true })
    val address = json<Address>("address", Json { ignoreUnknownKeys = true })
    val contactInfo = json<ContactInfo>("contact", Json { ignoreUnknownKeys = true })
    val time = long("time")
}