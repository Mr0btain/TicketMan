package org.mr0btain.ticketman.database.tables.note

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import java.util.*

class NoteEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<NoteEntity>(NoteTable)

    var author by NoteTable.author
    var message by NoteTable.message

    // List of images
    var images by ImageEntity via NoteImageTable

    fun toNote(): Note {
        return Note(
            identifier = id.value,
            author = author,
            message = message
        )
    }
}