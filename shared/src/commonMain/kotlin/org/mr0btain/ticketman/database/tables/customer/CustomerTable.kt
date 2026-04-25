package org.mr0btain.ticketman.database.tables.customer

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.customer.Address

object CustomerTable : UUIDTable("customer") {
    val name = text("name")
    val phone = text("phone")
    val email = text("email")
    val address = json<Address>("address", Json { ignoreUnknownKeys = true })
}