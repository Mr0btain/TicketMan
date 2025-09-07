package org.mr0btain.ticketman.database.tables.image

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.dao.UUIDEntity
import org.jetbrains.exposed.v1.dao.UUIDEntityClass
import org.mr0btain.ticketman.data.image.StoredImage
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class ImageEntity(entityID: EntityID<UUID>) : UUIDEntity(entityID) {
    companion object : UUIDEntityClass<ImageEntity>(ImageTable)

    var path by ImageTable.path
    var time by ImageTable.time
}

@OptIn(ExperimentalUuidApi::class)
fun ImageEntity.toStoredImage(): StoredImage {
    return StoredImage(
        identifier = id.value.toKotlinUuid(),
        path = path,
        time = time
    )
}