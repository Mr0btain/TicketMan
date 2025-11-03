package org.mr0btain.ticketman.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.image.StoredImage
import java.util.*

class ImageEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<ImageEntity>(ImageTable)

    var path by ImageTable.path

    fun toStoredImage(): StoredImage {
        return StoredImage(
            identifier = id.value,
            path = path
        )
    }
}