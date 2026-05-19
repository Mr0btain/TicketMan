package org.mr0btain.ticketman.database.tables.user

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import org.mr0btain.ticketman.data.shared.Identity
import org.mr0btain.ticketman.data.user.User
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import java.util.*

class UserEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<UserEntity>(UserTable)

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