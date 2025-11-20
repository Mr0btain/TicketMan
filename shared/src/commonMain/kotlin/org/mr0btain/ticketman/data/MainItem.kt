package org.mr0btain.ticketman.data

import org.mr0btain.ticketman.data.time.Time
import org.mr0btain.ticketman.data.uuid.UUIDv7
import java.util.*

interface MainItem {
    val identifier: UUID

    fun time(): Time = UUIDv7.getTime(identifier)
}