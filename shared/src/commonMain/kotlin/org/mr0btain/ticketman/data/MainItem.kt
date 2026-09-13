package org.mr0btain.ticketman.data

import org.mr0btain.ticketman.data.time.createdAt
import kotlin.time.Instant
import kotlin.uuid.Uuid

abstract class MainItem {
    abstract val identifier: Uuid

    val timestamp: Instant
        get() = identifier.createdAt()
}