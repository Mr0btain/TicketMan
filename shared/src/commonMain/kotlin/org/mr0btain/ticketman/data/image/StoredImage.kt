package org.mr0btain.ticketman.data.image

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

// TODO: Maybe add more info to this like auth and such
@Serializable
data class StoredImage(
    @Serializable(UUIDSerializer::class)
    override val identifier: UUID = UUIDv7.randomUUID(),
    val description: String = "",
    val directory: String = "",
    val tags: List<String> = emptyList(),
    val lastModified: Long = 0
): MainItem, SubItem
