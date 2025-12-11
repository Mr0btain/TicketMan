package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.image.StoredImage
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.time.TimeSerializer
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Note(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID = UUIDv7.randomUUID(),
    val author: String = "",
    val message: String = "",
    val images: List<StoredImage> = emptyList()
): MainItem, SubItem