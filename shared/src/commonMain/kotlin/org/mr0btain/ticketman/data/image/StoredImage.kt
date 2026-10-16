package org.mr0btain.ticketman.data.image

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.MainItem
import org.mr0btain.ticketman.data.SubItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Maybe add more info to this like auth and such
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class StoredImage(
    override val identifier: Uuid = Uuid.generateV7(),
    val description: String = "",
    val directory: String = "",
    val tags: List<String> = emptyList(),
    val lastModified: Long = 0
): MainItem(), SubItem
