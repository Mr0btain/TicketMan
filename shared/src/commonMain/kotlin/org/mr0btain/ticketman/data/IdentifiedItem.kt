package org.mr0btain.ticketman.data

import kotlin.uuid.Uuid

interface IdentifiedItem {
    val identifier: Uuid
    val text: String
}