package org.mr0btain.ticketman.components.database.tables.user

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.mr0btain.ticketman.components.database.tables.note.NoteEntity
import org.mr0btain.ticketman.components.database.tables.ticket.TicketEntity
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.user.User
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserEntity(entityID: EntityID<Uuid>) : UuidEntity(entityID) {
    companion object : UuidEntityClass<UserEntity>(UserTable)

    var username by UserTable.username
    var password by UserTable.password
    var name by UserTable.name
    var email by UserTable.email
    var phone by UserTable.phone
    var access by UserTable.access

    // For notes
    var notes by NoteEntity via UserNoteTable

    // For tickets
    var tickets by TicketEntity via UserTicketTable

    fun toUser(): User {
        return User(
            identifier = id.value,
            username = username,
            password = password,
            name = name,
            email = email,
            phone = phone,
            accessLevel = access
        )
    }

    fun toIdentity(): Identity {
        return Identity(id.value, name)
    }
}