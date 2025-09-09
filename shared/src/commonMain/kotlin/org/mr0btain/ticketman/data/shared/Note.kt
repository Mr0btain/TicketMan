package org.mr0btain.ticketman.data.shared

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.SubItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Note @OptIn(ExperimentalUuidApi::class) constructor(
    val identifier: Uuid = Uuid.random(),
    val author: String = "",
    val message: String = "",
    val time: Long = Long.MAX_VALUE
): SubItem