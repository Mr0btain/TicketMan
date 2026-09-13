package org.mr0btain.ticketman.data

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.time.createdAt
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
abstract class MainItem {
    abstract val identifier: Uuid

    val timestamp: Instant
        get() = identifier.createdAt()
}