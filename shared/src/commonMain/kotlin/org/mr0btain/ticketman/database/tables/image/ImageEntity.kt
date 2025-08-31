package org.mr0btain.ticketman.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.mr0btain.ticketman.database.tables.note.NoteEntity
import org.mr0btain.ticketman.database.tables.note.NoteImageTable

class ImageEntity(entityID: EntityID<Long>) : LongEntity(entityID) {
    companion object : LongEntityClass<ImageEntity>(ImageTable)

    var path by ImageTable.path
    var time by ImageTable.time
}