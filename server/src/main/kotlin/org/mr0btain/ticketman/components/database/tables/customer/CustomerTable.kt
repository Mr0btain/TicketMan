package org.mr0btain.ticketman.components.database.tables.customer

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.customer.Address

object CustomerTable : UuidTable("customer") {
    val name = text("name")
    val phone = text("phone")
    val email = text("email")
    val address = json<Address>("address", Json { ignoreUnknownKeys = true })

    // Agreed to messages about their repairs, off for everyone until they say so
    val notifyEmail = bool("notify_email").default(false)
    val notifySms = bool("notify_sms").default(false)

    // Store credit, only changed by StoredValueDatabase in single updates, never by saving the customer
    val creditCents = long("credit_cents").default(0)
}