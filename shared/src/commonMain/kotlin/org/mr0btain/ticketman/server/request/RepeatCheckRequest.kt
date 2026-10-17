package org.mr0btain.ticketman.server.request

import kotlinx.serialization.Serializable
import org.mr0btain.ticketman.data.ticket.Device
import kotlin.uuid.Uuid

/**
 * Asks whether this customer had this device in recently
 * @param excludeTicket Ticket being checked in, so it doesn't match itself
 */
@Serializable
data class RepeatCheckRequest(
    val customerIdentifier: Uuid,
    val device: Device,
    val excludeTicket: Uuid? = null
)
