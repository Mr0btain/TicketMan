package org.mr0btain.ticketman.components.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import org.mr0btain.ticketman.data.image.StoredImage
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ImageEntity(entityID: EntityID<Uuid>) : UuidEntity(entityID) {
    companion object : UuidEntityClass<ImageEntity>(ImageTable)

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