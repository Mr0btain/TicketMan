package org.mr0btain.ticketman.data

import java.util.UUID

interface IdentifiedItem {
    val identifier: UUID
    val text: String
}