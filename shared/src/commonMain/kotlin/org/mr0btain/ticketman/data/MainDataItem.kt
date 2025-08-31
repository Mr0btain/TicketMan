package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@Serializable
sealed interface MainDataItem {
    @OptIn(ExperimentalUuidApi::class)
    val identifier: Uuid
}