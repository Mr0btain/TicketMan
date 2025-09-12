package org.mr0btain.ticketman.database.tables.note

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.shared.Note
import org.mr0btain.ticketman.database.tables.image.ImageEntity
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class NoteEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<NoteEntity>(NoteTable)

    var author by NoteTable.author
    var message by NoteTable.message
    var time by NoteTable.time

    // List of images
    var images by ImageEntity via NoteImageTable
}

@OptIn(ExperimentalUuidApi::class)
fun NoteEntity.toNote(): Note {
    return Note(
        identifier = id.value.toKotlinUuid(),
        author = author,
        message = message,
        time = time
    )
}