package org.mr0btain.ticketman.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.time.Time
import java.util.*

class ImageEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<ImageEntity>(ImageTable)

    var description by ImageTable.description
    var directory by ImageTable.directory
    var tags by ImageTable.tags

    fun toStoredImage(): StoredImage {
        return StoredImage(
            identifier = id.value,
            description = description,
            directory = directory,
            tags = tags
        )
    }
}