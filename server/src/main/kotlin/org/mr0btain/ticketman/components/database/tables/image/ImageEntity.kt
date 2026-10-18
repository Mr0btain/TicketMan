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
    var width by ImageTable.width
    var height by ImageTable.height
    var sizeBytes by ImageTable.sizeBytes
    var sha256 by ImageTable.sha256
    var uploadedBy by ImageTable.uploadedBy
    var status by ImageTable.status
    var statusChangedAt by ImageTable.statusChangedAt

    val imageStatus: ImageStatus
        get() = ImageStatus.entries.firstOrNull { it.name == status } ?: ImageStatus.LINKED

    fun changeStatus(newStatus: ImageStatus, now: Long = System.currentTimeMillis()) {
        if (status != newStatus.name) {
            status = newStatus.name
            statusChangedAt = now
        }
    }

    fun toStoredImage(): StoredImage {
        return StoredImage(
            identifier = id.value,
            description = description,
            tags = tags,
            width = width,
            height = height,
            sizeBytes = sizeBytes
        )
    }
}
