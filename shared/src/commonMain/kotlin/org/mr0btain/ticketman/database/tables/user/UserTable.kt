package org.mr0btain.ticketman.database.tables.user

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.customer.ContactInfo
import org.mr0btain.ticketman.data.inventory.AccessLevel
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.data.shared.PersonName

object UserTable : UUIDTable("user") {
    val username = text("username")
    val password = text("password")
    val name = json<PersonName>("name", Json { ignoreUnknownKeys = true })
    val contactInfo = json<ContactInfo>("contact", Json { ignoreUnknownKeys = true })
    val access = json<AccessLevel>("access", Json { ignoreUnknownKeys = true })
}