package org.mr0btain.ticketman.database.tables.user

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.User
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.ticket.TicketEntity
import java.util.*
import kotlin.uuid.ExperimentalUuidApi

class UserEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<UserEntity>(UserTable)

    var username by UserTable.username
    var password by UserTable.password
    var name by UserTable.name
    var contactInfo by UserTable.contactInfo
    var access by UserTable.access
    var time by UserTable.time

    // For notes
    var notes by NoteEntity via UserNoteTable

    // For tickets
    var tickets by TicketEntity via UserTicketTable
}

@OptIn(ExperimentalUuidApi::class)
fun UserEntity.toUser(): User {
    return User(
        username = username,
        password = password,
        name = name,
        contactInfo = contactInfo,
        accessLevel = access,
        time = time
    )
}