package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.uuid.UUIDSerializer
import java.util.*


@Serializable
sealed interface MainItem {
    @Serializable(UUIDSerializer::class)
    val identifier: UUID
}