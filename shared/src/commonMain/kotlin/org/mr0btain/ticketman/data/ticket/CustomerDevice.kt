package org.mr0btain.ticketman.data.ticket

import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A device a customer owns, so its repairs can be followed over time. Tickets point to it with
 * [Ticket.deviceIdentifier] and keep a copy of its details as they were at check in
 * @param createdAt Epoch millis it was first seen
 */
@Serializable
@OptIn(ExperimentalUuidApi::class)
data class CustomerDevice(
    val identifier: Uuid = Uuid.generateV7(),
    val customerIdentifier: Uuid,
    val device: Device = Device(),
    val color: String = "",
    val notes: String = "",
    val createdAt: Long = 0
) {
    /** Like "Apple iPhone 13" with its serial when it has one */
    val label: String
        get() = listOf(device.toString().trim(), device.serial.trim().takeIf { it.isNotEmpty() }?.let { "($it)" }).filterNotNull()
            .joinToString(" ").ifBlank { "Unnamed device" }
}
