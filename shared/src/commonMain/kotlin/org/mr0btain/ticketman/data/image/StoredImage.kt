package org.mr0btain.ticketman.data.image

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// TODO: Maybe add more info to this like auth and such
@Serializable
data class StoredImage @OptIn(ExperimentalUuidApi::class) constructor(
    val identifier: Uuid,
    val path: String,
    val time: Long
): SubItem
