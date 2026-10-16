package org.mr0btain.ticketman.components.database.tables.user

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.json.json
import org.mr0btain.ticketman.data.inventory.AccessLevel

object UserTable : UuidTable("user") {
    val username = text("username")
    val password = text("password")
    val name = text("name")
    val email = text("email")
    val phone = text("phone")
    val access = json<AccessLevel>("access", Json { ignoreUnknownKeys = true })
}