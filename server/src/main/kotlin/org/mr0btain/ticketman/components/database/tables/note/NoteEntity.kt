package org.mr0btain.ticketman.components.database.tables.note

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.mr0btain.ticketman.components.database.tables.image.ImageEntity
import org.mr0btain.ticketman.data.shared.Note
import java.util.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class NoteEntity(entityID: EntityID<Uuid>) : UuidEntity(entityID) {
    companion object : UuidEntityClass<NoteEntity>(NoteTable)

    var author by NoteTable.author
    var message by NoteTable.message
    var customerVisible by NoteTable.customerVisible

    // List of images
    var images by ImageEntity via NoteImageTable

    fun toNote(): Note =
        Note(
            identifier = id.value,
            author = author,
            message = message,
            images = images.map { it.toStoredImage() },
            customerVisible = customerVisible
        )
}